#!/usr/bin/env groovy

// Generates an interactive HTML graph visualization of OSGi bundle dependencies.
// Loads the HTML template from groovy/bundle-dependency-template.html on the classpath
// and replaces placeholders with graph data.
//
// Injected variables:
//   concept         - the executed concept
//   result          - the Cypher query result
//   reportDirectory - the jQAssistant report output directory
//   logger          - SLF4J logger
//   store           - the Neo4j store
//
// Optional report properties:
//   filename        - output file name (default: bundle-dependencies.html)
//   filter          - regex to match bundle names (default: .* = all bundles)
//   groupPattern    - regex with capture group to extract group name from bundle name
//                     e.g. "com\\.mycompany\\.(shared|app\\.(?:one|two)|base)\\..*"

def fileName = concept.report.properties.get("filename") ?: "bundle-dependencies.html"
def filter = concept.report.properties.get("filter") ?: ".*"
def groupPattern = concept.report.properties.get("groupPattern") ?: ""
def outputFile = new File(reportDirectory, fileName)

logger.info("Generating interactive HTML bundle dependency graph: {} (filter: {})", outputFile.absolutePath, filter)

// --- Load template ---
def templatePath = "groovy/bundle-dependency-template.html"
def templateStream = Thread.currentThread().contextClassLoader.getResourceAsStream(templatePath)
if (templateStream == null) {
    // Fallback: try file on disk
    def templateFile = new File(templatePath)
    if (templateFile.exists()) {
        templateStream = new FileInputStream(templateFile)
    } else {
        logger.error("Template '{}' not found on classpath or disk", templatePath)
        return
    }
}
def template = templateStream.withReader('UTF-8') { it.text }

// --- Build graph data ---
def sanitizeId = { String s -> s.replaceAll('[^a-zA-Z0-9_]', '_').replaceAll('_+', '_').replaceAll('_$', '') }
def jsEscape = { String s -> s.replace('\\', '\\\\').replace("'", "\\'").replace('"', '\\"') }

def nodes = new LinkedHashMap<String, Map>()
def unresolvedTargets = new LinkedHashSet<String>()
def edges = []
def allVersions = new TreeSet<String>()

result.rows.each { row ->
    def source = row.columns.get("Source")?.value?.toString()
    def target = row.columns.get("Target")?.value?.toString()
    def sourceVersion = row.columns.get("SourceVersion")?.value?.toString()?.with { it == "null" ? null : it }
    def targetVersion = row.columns.get("TargetVersion")?.value?.toString()?.with { it == "null" ? null : it }
    def viaRequireBundle = row.columns.get("ViaRequireBundle")?.value
    def viaPackageWiring = row.columns.get("ViaPackageWiring")?.value
    def resolved = row.columns.get("Resolved")?.value
    def typeDepCount = row.columns.get("TypeDependencyCount")?.value
    if (source && target) {
        def srcId = sourceVersion ? "${source}_${sourceVersion}".toString() : source
        def tgtId = targetVersion ? "${target}_${targetVersion}".toString() : target
        nodes.put(srcId, [name: source, version: sourceVersion])
        nodes.put(tgtId, [name: target, version: targetVersion])
        if (sourceVersion) allVersions.add(sourceVersion)
        if (targetVersion) allVersions.add(targetVersion)
        def rb = viaRequireBundle == true || viaRequireBundle == "true"
        def pw = viaPackageWiring == true || viaPackageWiring == "true"
        def res = resolved == true || resolved == "true"
        def weight = (typeDepCount instanceof Number) ? typeDepCount.intValue() : 0
        if (!res) {
            unresolvedTargets.add(tgtId)
        }
        def type
        if (!res) type = 'unresolved'
        else if (rb && pw) type = 'redundant'
        else if (rb) type = 'requireBundle'
        else type = 'packageWiring'
        edges.add([source: srcId, target: tgtId, type: type, weight: weight])
    }
}

// --- Build JSON and options ---
def nodesJson = nodes.collect { id, comp ->
    def label = comp.version ? "${comp.name}\\n${comp.version}" : comp.name
    def cls = unresolvedTargets.contains(id) ? 'unresolved' : 'resolved'
    """      { "data": { "id": "${sanitizeId(id)}", "label": "${label}", "name": "${jsEscape(comp.name)}", "version": "${comp.version ?: ''}", "nodeClass": "${cls}" } }"""
}.join(",\n")

def edgesJson = edges.collect { edge ->
    """      { "data": { "source": "${sanitizeId(edge.source)}", "target": "${sanitizeId(edge.target)}", "type": "${edge.type}", "weight": ${edge.weight} } }"""
}.join(",\n")

def versionOptionsHtml = allVersions.collect { v -> """<option value="${v}">${v}</option>""" }.join("\n      ")

def rawFilter = (filter == ".*") ? "" : filter
def filterValueHtml = rawFilter.replace('&', '&amp;').replace('"', '&quot;')
def filterValueJs = rawFilter.replace('\\', '\\\\').replace("'", "\\'")
def groupPatternHtml = groupPattern.replace('&', '&amp;').replace('"', '&quot;')
def groupPatternJs = groupPattern.replace('\\', '\\\\').replace("'", "\\'")

// --- Replace placeholders and write ---
def html = template
    .replace('@@NODES_JSON@@', nodesJson)
    .replace('@@EDGES_JSON@@', edgesJson)
    .replace('@@VERSION_OPTIONS@@', versionOptionsHtml)
    .replace('@@FILTER_VALUE@@', filterValueHtml)
    .replace('@@FILTER_VALUE_JS@@', filterValueJs)
    .replace('@@GROUP_PATTERN@@', groupPatternHtml)
    .replace('@@GROUP_PATTERN_JS@@', groupPatternJs)

outputFile.withWriter('UTF-8') { writer ->
    writer.write(html)
}

logger.info("Interactive HTML graph written with {} nodes and {} edges, {} versions",
    nodes.size(), edges.size(), allVersions.size())
