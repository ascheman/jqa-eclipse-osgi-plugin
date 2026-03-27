#!/usr/bin/env groovy

// Generates a PlantUML component diagram from osgi:BundleDependency results.
//
// Injected variables:
//   concept         - the executed concept
//   result          - the Cypher query result (rows with Source, Target columns)
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
    name.replaceAll('[.\\- ]', '_')
}

def components = new LinkedHashSet<String>()
def edges = []

result.rows.each { row ->
    def source = row.columns.get("Source")?.value?.toString()
    def target = row.columns.get("Target")?.value?.toString()
    if (source && target && source.matches(filter) && target.matches(filter)) {
        components.add(source)
        components.add(target)
        edges.add([source, target])
    }
}

outputFile.withWriter('UTF-8') { writer ->
    writer.writeLine('@startuml')
    writer.writeLine('')
    components.sort().each { name ->
        writer.writeLine("[${name}] as ${sanitize(name)}")
    }
    writer.writeLine('')
    edges.sort { a, b -> a[0] <=> b[0] ?: a[1] <=> b[1] }.each { edge ->
        writer.writeLine("${sanitize(edge[0])} --> ${sanitize(edge[1])}")
    }
    writer.writeLine('')
    writer.writeLine('@enduml')
}

logger.info("PlantUML diagram written with {} components and {} edges", components.size(), edges.size())
