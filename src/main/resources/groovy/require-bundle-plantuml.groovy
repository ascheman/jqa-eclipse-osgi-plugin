#!/usr/bin/env groovy

// Generates a PlantUML component diagram from Require-Bundle relationships.
// Mandatory dependencies are shown in red, optional ones in blue.
//
// Injected variables:
//   concept         - the executed concept
//   result          - the Cypher query result (rows with Bundle, RequiredBundle, Optional columns)
//   reportDirectory - the jQAssistant report output directory
//   logger          - SLF4J logger
//   store           - the Neo4j store
//
// Optional report properties:
//   filename        - output file name (default: require-bundle-dependencies.puml)
//   filter          - regex to match bundle names (default: .* = all bundles)

def fileName = concept.report.properties.get("filename") ?: "require-bundle-dependencies.puml"
def filter = concept.report.properties.get("filter") ?: ".*"
def outputFile = new File(reportDirectory, fileName)

logger.info("Generating PlantUML Require-Bundle diagram: {} (filter: {})", outputFile.absolutePath, filter)

def sanitize = { String name ->
    name.replaceAll('[.\\- ]', '_')
}

def components = new LinkedHashSet<String>()
def edges = []

result.rows.each { row ->
    def source = row.columns.get("Bundle")?.value?.toString()
    def target = row.columns.get("RequiredBundle")?.value?.toString()
    def optional = row.columns.get("Optional")?.value
    def resolved = row.columns.get("Resolved")?.value
    if (source && target && source.matches(filter) && target.matches(filter)) {
        components.add(source)
        components.add(target)
        edges.add([source: source, target: target, optional: optional == true || optional == "true", resolved: resolved == true || resolved == "true"])
    }
}

outputFile.withWriter('UTF-8') { writer ->
    writer.writeLine('@startuml')
    writer.writeLine('')
    writer.writeLine('skinparam component {')
    writer.writeLine('    BackgroundColor<<unresolved>> LightGray')
    writer.writeLine('}')
    writer.writeLine('')
    // Collect resolved targets to mark unresolved ones
    def resolvedTargets = edges.findAll { it.resolved }.collect { it.target } as Set
    components.sort().each { name ->
        def stereotype = resolvedTargets.contains(name) || edges.any { it.source == name } ? '' : ' <<unresolved>>'
        writer.writeLine("[${name}] as ${sanitize(name)}${stereotype}")
    }
    writer.writeLine('')
    edges.sort { a, b -> a.source <=> b.source ?: a.target <=> b.target }.each { edge ->
        def style = edge.optional ? '..>' : '-->'
        def color = edge.optional ? '#blue' : '#red'
        writer.writeLine("${sanitize(edge.source)} ${style} ${sanitize(edge.target)} ${color}")
    }
    writer.writeLine('')
    writer.writeLine('legend right')
    writer.writeLine('  <color:red>Red</color> = mandatory Require-Bundle')
    writer.writeLine('  <color:blue>Blue dashed</color> = optional Require-Bundle')
    writer.writeLine('  <<unresolved>> = bundle not found in scan')
    writer.writeLine('endlegend')
    writer.writeLine('')
    writer.writeLine('@enduml')
}

logger.info("PlantUML Require-Bundle diagram written with {} components and {} edges", components.size(), edges.size())
