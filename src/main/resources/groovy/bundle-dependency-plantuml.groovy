#!/usr/bin/env groovy

// Generates a unified PlantUML component diagram of all OSGi bundle dependencies.
// Components show bundle name and version on separate lines.
// Colors indicate the dependency type:
//   Black  = Require-Bundle only (normal bundle dependency)
//   Blue   = Package wiring only (clean OSGi pattern)
//   Green  = Both Require-Bundle and package wiring (redundant, cleanup candidate)
//   Red    = Unresolved Require-Bundle (missing bundle)
//
// Injected variables:
//   concept         - the executed concept
//   result          - the Cypher query result
//   reportDirectory - the jQAssistant report output directory
//   logger          - SLF4J logger
//   store           - the Neo4j store
//
// Optional report properties:
//   filename        - output file name (default: bundle-dependencies.puml)
//   filter          - regex to match bundle names (default: .* = all bundles)
//   groupPattern    - regex with capture group to extract group name from bundle name

def fileName = concept.report.properties.get("filename") ?: "bundle-dependencies.puml"
def filter = concept.report.properties.get("filter") ?: ".*"
def groupPatternStr = concept.report.properties.get("groupPattern")
def outputFile = new File(reportDirectory, fileName)

logger.info("Generating PlantUML bundle dependency diagram: {} (filter: {})", outputFile.absolutePath, filter)

def sanitize = { String name ->
    name.replaceAll('[^a-zA-Z0-9_]', '_').replaceAll('_+', '_').replaceAll('_$', '')
}

def componentKey = { String name, String version ->
    version ? "${name}_${version}".toString() : name
}

// Map of componentKey -> [name, version]
def components = new LinkedHashMap<String, Map>()
def unresolvedTargets = new LinkedHashSet<String>()
def edges = []

def filterPattern = ~filter
logger.info("PlantUML filter pattern: '{}', result rows: {}", filter, result.rows.size())

result.rows.each { row ->
    def source = row.columns.get("Source")?.value?.toString()
    def target = row.columns.get("Target")?.value?.toString()
    def sourceVersion = row.columns.get("SourceVersion")?.value?.toString()?.with { it == "null" ? null : it }
    def targetVersion = row.columns.get("TargetVersion")?.value?.toString()?.with { it == "null" ? null : it }
    def viaRequireBundle = row.columns.get("ViaRequireBundle")?.value
    def viaPackageWiring = row.columns.get("ViaPackageWiring")?.value
    def resolved = row.columns.get("Resolved")?.value
    def typeDepCount = row.columns.get("TypeDependencyCount")?.value
    def srcMatch = source ? filterPattern.matcher(source).find() : false
    def tgtMatch = target ? filterPattern.matcher(target).find() : false
    logger.debug("PlantUML row: {} -> {}, srcMatch={}, tgtMatch={}", source, target, srcMatch, tgtMatch)
    if (source && target && srcMatch && tgtMatch) {
        def srcKey = componentKey(source, sourceVersion)
        def tgtKey = componentKey(target, targetVersion)
        components.put(srcKey, [name: source, version: sourceVersion])
        components.put(tgtKey, [name: target, version: targetVersion])
        def rb = viaRequireBundle == true || viaRequireBundle == "true"
        def pw = viaPackageWiring == true || viaPackageWiring == "true"
        def res = resolved == true || resolved == "true"
        def weight = (typeDepCount instanceof Number) ? typeDepCount.intValue() : 0
        if (!res) {
            unresolvedTargets.add(tgtKey)
        }
        edges.add([sourceKey: srcKey, targetKey: tgtKey, viaRequireBundle: rb, viaPackageWiring: pw, resolved: res, weight: weight])
    }
}

// --- Compute groups ---
def groupPattern = groupPatternStr ? ~groupPatternStr : null
def groups = new LinkedHashMap<String, List>() // groupName -> [componentKeys]
def nodeGroups = [:] // componentKey -> groupName

if (groupPattern) {
    components.each { key, comp ->
        if (!comp?.name) return
        def matcher = groupPattern.matcher(comp.name)
        if (matcher.find() && matcher.groupCount() >= 1) {
            def groupName = matcher.group(1)
            if (groupName) {
                groups.computeIfAbsent(groupName, { [] }).add(key)
                nodeGroups[key] = groupName
            }
        }
    }
    logger.info("Grouping: {} groups found: {}", groups.size(), groups.keySet())
}

outputFile.withWriter('UTF-8') { writer ->
    writer.writeLine('@startuml')
    writer.writeLine('')
    writer.writeLine('skinparam component {')
    writer.writeLine('    BackgroundColor<<unresolved>> LightGray')
    writer.writeLine('}')
    writer.writeLine('')
    // Write grouped components
    groups.sort { a, b -> a.key <=> b.key }.each { groupName, keys ->
        logger.info("Group '{}': keys={}", groupName, keys)
        writer.writeLine("package \"${groupName}\" {")
        keys.sort().each { key ->
            def comp = components[key]
            logger.info("  key='{}', comp={}, inComponents={}", key, comp, components.containsKey(key))
            if (!comp) return // skip if component was filtered out
            def alias = sanitize(key)
            def label = comp.version ? "${comp.name}\\n${comp.version}" : comp.name
            def stereotype = unresolvedTargets.contains(key) ? ' <<unresolved>>' : ''
            writer.writeLine("    [${label}] as ${alias}${stereotype}")
        }
        writer.writeLine("}")
        writer.writeLine('')
    }
    // Write ungrouped components
    components.sort { a, b -> a.key <=> b.key }.each { key, comp ->
        if (!nodeGroups.containsKey(key)) {
            def alias = sanitize(key)
            def label = comp.version ? "${comp.name}\\n${comp.version}" : comp.name
            def stereotype = unresolvedTargets.contains(key) ? ' <<unresolved>>' : ''
            writer.writeLine("[${label}] as ${alias}${stereotype}")
        }
    }
    writer.writeLine('')
    edges.sort { a, b -> a.sourceKey <=> b.sourceKey ?: a.targetKey <=> b.targetKey }.each { edge ->
        def style
        def color = 'blue'
        if (!edge.resolved) {
            style = '..'
            color = 'orange'
        } else if (edge.weight == 0) {
            style = '..'
            color = 'red'
        } else if (edge.viaRequireBundle && edge.viaPackageWiring) {
            style = '--'
            color = 'green'
        } else if (edge.viaRequireBundle) {
            style = '--'
            color = 'black'
        } else {
            style = '--'
            color = 'blue'
        }
        def weightLabel = edge.weight != null ? " : ${edge.weight}" : ""
        writer.writeLine("${sanitize(edge.sourceKey)} -[${color}]${style}> ${sanitize(edge.targetKey)}${weightLabel}")
    }
    writer.writeLine('')
    writer.writeLine('legend right')
    writer.writeLine('  <color:black>Black</color> = Require-Bundle only')
    writer.writeLine('  <color:blue>Blue</color> = Package wiring only')
    writer.writeLine('  <color:green>Green</color> = Both (redundant)')
    writer.writeLine('  <color:orange>Orange dashed</color> = Unresolved Require-Bundle')
    writer.writeLine('  <color:red>Red</color> = Unused (0 type dependencies)')
    writer.writeLine('  <<unresolved>> = bundle not found in scan')
    writer.writeLine('endlegend')
    writer.writeLine('')
    writer.writeLine('@enduml')
}

logger.info("PlantUML diagram written with {} components and {} edges ({} unresolved)",
    components.size(), edges.size(), unresolvedTargets.size())
