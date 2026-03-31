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

def fileName = concept.report.properties.get("filename") ?: "bundle-dependencies.puml"
def filter = concept.report.properties.get("filter") ?: ".*"
def outputFile = new File(reportDirectory, fileName)

logger.info("Generating PlantUML bundle dependency diagram: {} (filter: {})", outputFile.absolutePath, filter)

def sanitize = { String name ->
    name.replaceAll('[^a-zA-Z0-9_]', '_').replaceAll('_+', '_').replaceAll('_$', '')
}

def componentKey = { String name, String version ->
    version ? "${name}_${version}" : name
}

// Map of componentKey -> [name, version]
def components = new LinkedHashMap<String, Map>()
def unresolvedTargets = new LinkedHashSet<String>()
def edges = []

result.rows.each { row ->
    def source = row.columns.get("Source")?.value?.toString()
    def target = row.columns.get("Target")?.value?.toString()
    def sourceVersion = row.columns.get("SourceVersion")?.value?.toString()?.with { it == "null" ? null : it }
    def targetVersion = row.columns.get("TargetVersion")?.value?.toString()?.with { it == "null" ? null : it }
    def viaRequireBundle = row.columns.get("ViaRequireBundle")?.value
    def viaPackageWiring = row.columns.get("ViaPackageWiring")?.value
    def resolved = row.columns.get("Resolved")?.value
    if (source && target && source.matches(filter) && target.matches(filter)) {
        def srcKey = componentKey(source, sourceVersion)
        def tgtKey = componentKey(target, targetVersion)
        components.put(srcKey, [name: source, version: sourceVersion])
        components.put(tgtKey, [name: target, version: targetVersion])
        def rb = viaRequireBundle == true || viaRequireBundle == "true"
        def pw = viaPackageWiring == true || viaPackageWiring == "true"
        def res = resolved == true || resolved == "true"
        if (!res) {
            unresolvedTargets.add(tgtKey)
        }
        edges.add([sourceKey: srcKey, targetKey: tgtKey, viaRequireBundle: rb, viaPackageWiring: pw, resolved: res])
    }
}

outputFile.withWriter('UTF-8') { writer ->
    writer.writeLine('@startuml')
    writer.writeLine('')
    writer.writeLine('skinparam component {')
    writer.writeLine('    BackgroundColor<<unresolved>> LightGray')
    writer.writeLine('}')
    writer.writeLine('')
    components.sort { a, b -> a.key <=> b.key }.each { key, comp ->
        def alias = sanitize(key)
        def label = comp.version ? "${comp.name}\\n${comp.version}" : comp.name
        def stereotype = unresolvedTargets.contains(key) ? ' <<unresolved>>' : ''
        writer.writeLine("[${label}] as ${alias}${stereotype}")
    }
    writer.writeLine('')
    edges.sort { a, b -> a.sourceKey <=> b.sourceKey ?: a.targetKey <=> b.targetKey }.each { edge ->
        def style
        def color
        if (!edge.resolved) {
            style = '..>'
            color = '#red'
        } else if (edge.viaRequireBundle && edge.viaPackageWiring) {
            style = '-->'
            color = '#green'
        } else if (edge.viaRequireBundle) {
            style = '-->'
            color = '#black'
        } else {
            style = '-->'
            color = '#blue'
        }
        writer.writeLine("${sanitize(edge.sourceKey)} ${style} ${sanitize(edge.targetKey)} ${color}")
    }
    writer.writeLine('')
    writer.writeLine('legend right')
    writer.writeLine('  <color:black>Black</color> = Require-Bundle only')
    writer.writeLine('  <color:blue>Blue</color> = Package wiring only')
    writer.writeLine('  <color:green>Green</color> = Both (redundant)')
    writer.writeLine('  <color:red>Red dashed</color> = Unresolved Require-Bundle')
    writer.writeLine('  <<unresolved>> = bundle not found in scan')
    writer.writeLine('endlegend')
    writer.writeLine('')
    writer.writeLine('@enduml')
}

logger.info("PlantUML diagram written with {} components and {} edges ({} unresolved)",
    components.size(), edges.size(), unresolvedTargets.size())
