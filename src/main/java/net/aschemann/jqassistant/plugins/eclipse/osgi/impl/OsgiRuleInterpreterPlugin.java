package net.aschemann.jqassistant.plugins.eclipse.osgi.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.buschmais.jqassistant.core.analysis.api.AnalyzerContext;
import com.buschmais.jqassistant.core.analysis.api.RuleInterpreterPlugin;
import com.buschmais.jqassistant.core.report.api.model.Column;
import com.buschmais.jqassistant.core.report.api.model.Result;
import com.buschmais.jqassistant.core.report.api.model.Row;
import com.buschmais.jqassistant.core.report.api.model.VerificationResult;
import com.buschmais.jqassistant.core.rule.api.model.ExecutableRule;
import com.buschmais.jqassistant.core.rule.api.model.RuleException;
import com.buschmais.jqassistant.core.rule.api.model.Severity;
import com.buschmais.jqassistant.core.rule.impl.SourceExecutable;
import com.buschmais.jqassistant.core.store.api.Store;
import com.buschmais.xo.api.Query;

import net.aschemann.jqassistant.plugins.eclipse.osgi.impl.OsgiManifestHeaderParser.HeaderEntry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link RuleInterpreterPlugin} that handles the {@code "osgi"} rule language.
 * <p>
 * Concepts using {@code <source language="osgi">} specify a command name that
 * determines which manifest header parsing operation to run. The plugin queries
 * existing ManifestEntry nodes, parses their values with proper quote-aware
 * comma splitting, and creates enriched graph nodes.
 */
public class OsgiRuleInterpreterPlugin implements RuleInterpreterPlugin {

    private static final Logger LOG = LoggerFactory.getLogger(OsgiRuleInterpreterPlugin.class);

    private static final String LANGUAGE = "osgi";

    @Override
    public Collection<String> getLanguages() {
        return Collections.singleton(LANGUAGE);
    }

    @Override
    public <T extends ExecutableRule<?>> boolean accepts(T executableRule) {
        return executableRule.getExecutable() instanceof SourceExecutable
                && LANGUAGE.equals(executableRule.getExecutable().getLanguage());
    }

    @Override
    public <T extends ExecutableRule<?>> Result<T> execute(T executableRule, Map<String, Object> ruleParameters,
            Severity severity, AnalyzerContext context) throws RuleException {
        @SuppressWarnings("unchecked")
        SourceExecutable<String> executable = (SourceExecutable<String>) executableRule.getExecutable();
        String command = executable.getSource().trim();
        LOG.debug("Executing OSGi command: {}", command);

        Store store = context.getStore();
        List<String> columnNames;
        List<Row> rows;

        switch (command) {
            case "parseRequireBundle":
                columnNames = List.of("Bundle", "RequiredBundle", "VersionRange");
                rows = parseRequireBundle(store, executableRule, context);
                break;
            case "parseExportPackage":
                columnNames = List.of("Bundle", "ExportedPackage", "Version");
                rows = parseExportPackage(store, executableRule, context);
                break;
            case "parseImportPackage":
                columnNames = List.of("Bundle", "ImportedPackage", "Optional", "Version");
                rows = parseImportPackage(store, executableRule, context);
                break;
            default:
                throw new RuleException("Unknown OSGi command: " + command);
        }

        VerificationResult verificationResult = context.verify(executableRule, columnNames, rows);
        Result.Status status = context.getStatus(verificationResult, severity);

        return Result.<T>builder()
                .rule(executableRule)
                .verificationResult(verificationResult)
                .status(status)
                .severity(severity)
                .columnNames(columnNames)
                .rows(rows)
                .build();
    }

    private <T extends ExecutableRule<?>> List<Row> parseRequireBundle(Store store, T rule,
            AnalyzerContext context) {
        List<Row> rows = new ArrayList<>();
        String query = "MATCH (bundle:Osgi:Bundle)-[:CONTAINS]->(mf:Manifest:File)"
                + "-[:DECLARES]->(ms:ManifestSection {name: 'Main'})"
                + "-[:HAS]->(entry:ManifestEntry {name: 'Require-Bundle'})"
                + " RETURN bundle.bundleSymbolicName AS bundleName, entry.value AS headerValue";

        try (Query.Result<Query.Result.CompositeRowObject> result = store.executeQuery(query)) {
            for (Query.Result.CompositeRowObject row : result) {
                String bundleName = row.get("bundleName", String.class);
                String headerValue = row.get("headerValue", String.class);

                for (HeaderEntry entry : OsgiManifestHeaderParser.parseHeader(headerValue)) {
                    String symbolicName = entry.getName();
                    String versionRange = entry.getAttribute("bundle-version");
                    String rawValue = entry.getRawValue();

                    Map<String, Object> params = new LinkedHashMap<>();
                    params.put("bundleName", bundleName);
                    params.put("symbolicName", symbolicName);
                    params.put("versionRange", versionRange);
                    params.put("rawValue", rawValue);

                    store.executeQuery(
                            "MATCH (bundle:Osgi:Bundle {bundleSymbolicName: $bundleName}) "
                                    + "MERGE (rb:Osgi:RequiredBundle {symbolicName: $symbolicName}) "
                                    + "MERGE (bundle)-[:REQUIRES_BUNDLE]->(rb) "
                                    + "SET rb.rawValue = $rawValue, rb.versionRange = $versionRange",
                            params).close();

                    rows.add(toRow(rule, context, Map.of(
                            "Bundle", bundleName,
                            "RequiredBundle", symbolicName,
                            "VersionRange", versionRange != null ? versionRange : "")));
                }
            }
        }
        return rows;
    }

    private <T extends ExecutableRule<?>> List<Row> parseExportPackage(Store store, T rule,
            AnalyzerContext context) {
        List<Row> rows = new ArrayList<>();
        String query = "MATCH (bundle:Osgi:Bundle)-[:CONTAINS]->(mf:Manifest:File)"
                + "-[:DECLARES]->(ms:ManifestSection {name: 'Main'})"
                + "-[:HAS]->(entry:ManifestEntry {name: 'Export-Package'})"
                + " RETURN bundle.bundleSymbolicName AS bundleName, entry.value AS headerValue";

        try (Query.Result<Query.Result.CompositeRowObject> result = store.executeQuery(query)) {
            for (Query.Result.CompositeRowObject row : result) {
                String bundleName = row.get("bundleName", String.class);
                String headerValue = row.get("headerValue", String.class);

                for (HeaderEntry entry : OsgiManifestHeaderParser.parseHeader(headerValue)) {
                    String packageName = entry.getName();
                    String version = entry.getAttribute("version");
                    String rawValue = entry.getRawValue();

                    Map<String, Object> params = new LinkedHashMap<>();
                    params.put("bundleName", bundleName);
                    params.put("packageName", packageName);
                    params.put("version", version);
                    params.put("rawValue", rawValue);

                    store.executeQuery(
                            "MATCH (bundle:Osgi:Bundle {bundleSymbolicName: $bundleName}) "
                                    + "MERGE (ep:Osgi:ExportedPackage {fqn: $packageName, bundleSymbolicName: $bundleName}) "
                                    + "MERGE (bundle)-[:EXPORTS_PACKAGE]->(ep) "
                                    + "SET ep.rawValue = $rawValue, ep.version = $version",
                            params).close();

                    rows.add(toRow(rule, context, Map.of(
                            "Bundle", bundleName,
                            "ExportedPackage", packageName,
                            "Version", version != null ? version : "")));
                }
            }
        }
        return rows;
    }

    private <T extends ExecutableRule<?>> List<Row> parseImportPackage(Store store, T rule,
            AnalyzerContext context) {
        List<Row> rows = new ArrayList<>();
        String query = "MATCH (bundle:Osgi:Bundle)-[:CONTAINS]->(mf:Manifest:File)"
                + "-[:DECLARES]->(ms:ManifestSection {name: 'Main'})"
                + "-[:HAS]->(entry:ManifestEntry {name: 'Import-Package'})"
                + " RETURN bundle.bundleSymbolicName AS bundleName, entry.value AS headerValue";

        try (Query.Result<Query.Result.CompositeRowObject> result = store.executeQuery(query)) {
            for (Query.Result.CompositeRowObject row : result) {
                String bundleName = row.get("bundleName", String.class);
                String headerValue = row.get("headerValue", String.class);

                for (HeaderEntry entry : OsgiManifestHeaderParser.parseHeader(headerValue)) {
                    String packageName = entry.getName();
                    String version = entry.getAttribute("version");
                    String resolution = entry.getAttribute("resolution");
                    boolean optional = "optional".equals(resolution);
                    String rawValue = entry.getRawValue();

                    Map<String, Object> params = new LinkedHashMap<>();
                    params.put("bundleName", bundleName);
                    params.put("packageName", packageName);
                    params.put("version", version);
                    params.put("rawValue", rawValue);
                    params.put("optional", optional);

                    store.executeQuery(
                            "MATCH (bundle:Osgi:Bundle {bundleSymbolicName: $bundleName}) "
                                    + "MERGE (ip:Osgi:ImportedPackage {fqn: $packageName, bundleSymbolicName: $bundleName}) "
                                    + "MERGE (bundle)-[:IMPORTS_PACKAGE]->(ip) "
                                    + "SET ip.rawValue = $rawValue, ip.optional = $optional, ip.version = $version",
                            params).close();

                    rows.add(toRow(rule, context, Map.of(
                            "Bundle", bundleName,
                            "ImportedPackage", packageName,
                            "Optional", optional,
                            "Version", version != null ? version : "")));
                }
            }
        }
        return rows;
    }

    private Row toRow(ExecutableRule<?> rule, AnalyzerContext context, Map<String, Object> values) {
        Map<String, Column<?>> columns = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            columns.put(entry.getKey(), context.toColumn(entry.getValue()));
        }
        return context.toRow(rule, columns);
    }
}
