# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

`jqa-eclipse-osgi-plugin` is a jQAssistant plugin that provides Cypher-based **concepts** and **constraints** for analyzing Eclipse RCP/OSGi projects. It does **not** scan new artifact types — instead it enriches the graph that jQAssistant's built-in Java plugin already creates from JAR/MANIFEST.MF scanning.

### How it works

1. jQAssistant's Java plugin scans JARs and creates: `(:Jar:Archive)-[:CONTAINS]->(:Manifest:File)-[:DECLARES]->(:ManifestSection {name:"Main"})-[:HAS]->(:ManifestEntry {name, value})`
2. This plugin's Cypher concepts read those `ManifestEntry` nodes and create OSGi/Eclipse-typed nodes, labels, and relationships on top
3. Constraints then validate OSGi best practices against the enriched graph

### Plugin type

This is a **rules-only plugin** (no Java scanner code). All logic lives in Cypher rule XML files under `src/main/resources/META-INF/jqassistant-rules/`.

## Build Commands

This project uses the **Maven Wrapper** (`./mvnw`) — always use it instead of a system-installed `mvn`.

```bash
# Build and install locally (needed before using in other projects)
./mvnw install -DskipTests

# Build with tests
./mvnw verify

# Run integration tests
./mvnw verify -Pfailsafe

# Validate POM only
./mvnw validate
```

## Project Structure

```
jqa-eclipse-osgi-plugin/
├── pom.xml
├── src/main/
│   ├── java/net/aschemann/jqassistant/plugins/eclipse/osgi/
│   │   └── (model classes - future: XO descriptor interfaces)
│   └── resources/META-INF/
│       ├── jqassistant-plugin.xml          # Plugin registration
│       └── jqassistant-rules/
│           ├── osgi-bundle.xml             # Bundle concepts (core)
│           ├── osgi-packages.xml           # Import/Export package concepts
│           ├── eclipse.xml                 # Eclipse-specific concepts
│           └── osgi-constraints.xml        # Validation constraints
├── src/test/
│   ├── java/...                            # Test classes
│   └── resources/                          # Test MANIFEST.MF samples
└── src/asciidoc/                           # Documentation
```

## Rule Files

### osgi-bundle.xml — Group: `osgi:Default`

Core OSGi bundle concepts:

| Concept ID | Description |
|---|---|
| `osgi:Bundle` | Labels JARs with Bundle-SymbolicName as `:Osgi:Bundle` |
| `osgi:BundleVersion` | Extracts Bundle-Version property |
| `osgi:BundleName` | Extracts Bundle-Name property |
| `osgi:BundleVendor` | Extracts Bundle-Vendor property |
| `osgi:SingletonBundle` | Detects `singleton:=true` directive |
| `osgi:BundleActivator` | Extracts Bundle-Activator class name |
| `osgi:LazyActivation` | Detects `Bundle-ActivationPolicy: lazy` |
| `osgi:ExecutionEnvironment` | Extracts Bundle-RequiredExecutionEnvironment |
| `osgi:Fragment` | Labels bundles with Fragment-Host as `:Osgi:Fragment` |
| `osgi:FragmentResolvesToHost` | Creates `[:FRAGMENT_OF]` relationships |
| `osgi:RequiresBundle` | Parses Require-Bundle into `:Osgi:RequiredBundle` nodes |
| `osgi:RequiredBundleResolution` | Creates `[:RESOLVES_TO]` from required to actual bundles |

### osgi-packages.xml — Group: `osgi:Packages`

| Concept ID | Description |
|---|---|
| `osgi:ExportedPackage` | Parses Export-Package into `:Osgi:ExportedPackage` nodes |
| `osgi:ImportedPackage` | Parses Import-Package into `:Osgi:ImportedPackage` nodes |
| `osgi:PackageWiring` | Creates `[:SATISFIES]` relationships (export→import) |
| `osgi:BundleClassPath` | Extracts Bundle-ClassPath entries |

### eclipse.xml — Group: `eclipse:Default`

| Concept ID | Description |
|---|---|
| `eclipse:PlatformFilter` | Extracts Eclipse-PlatformFilter (os, arch) |
| `eclipse:BundleLocalization` | Extracts Bundle-Localization header |
| `eclipse:EclipseBundle` | Labels `org.eclipse.*` bundles as `:Eclipse:FrameworkBundle` |
| `eclipse:ApplicationBundle` | Labels non-framework bundles as `:Eclipse:ApplicationBundle` |

### osgi-constraints.xml — Group: `osgi:Constraints`

| Constraint ID | Description |
|---|---|
| `osgi:UnresolvedRequiredBundle` | Require-Bundle entries with no matching bundle |
| `osgi:UnresolvedFragmentHost` | Fragments whose host bundle is missing |
| `osgi:CircularBundleDependency` | Circular A↔B Require-Bundle dependencies |
| `osgi:MissingBundleVersion` | Bundles without Bundle-Version |
| `osgi:UnsatisfiedImport` | Non-optional Import-Package without matching export |

## Graph Model

### Node Labels

- `:Osgi:Bundle` — on existing `:Jar:Archive` nodes
- `:Osgi:Fragment` — subset of Bundle (has Fragment-Host)
- `:Osgi:RequiredBundle` — intermediate node for Require-Bundle entries
- `:Osgi:ExportedPackage` — one per exported package per bundle
- `:Osgi:ImportedPackage` — one per imported package per bundle
- `:Eclipse:PlatformSpecific` — bundles with Eclipse-PlatformFilter
- `:Eclipse:FrameworkBundle` — `org.eclipse.*` bundles
- `:Eclipse:ApplicationBundle` — non-framework bundles

### Relationships

- `(:Bundle)-[:EXPORTS_PACKAGE]->(:ExportedPackage)`
- `(:Bundle)-[:IMPORTS_PACKAGE]->(:ImportedPackage)`
- `(:Bundle)-[:REQUIRES_BUNDLE]->(:RequiredBundle)`
- `(:RequiredBundle)-[:RESOLVES_TO]->(:Bundle)`
- `(:ExportedPackage)-[:SATISFIES]->(:ImportedPackage)`
- `(:Fragment)-[:FRAGMENT_OF]->(:Bundle)`

### Key Properties on `:Osgi:Bundle`

`bundleSymbolicName`, `bundleVersion`, `bundleName`, `bundleVendor`, `singleton`, `bundleActivator`, `activationPolicy`, `requiredExecutionEnvironment`, `fragmentHostSymbolicName`, `platformFilter`, `targetOs`, `targetArch`, `bundleLocalization`, `bundleClassPath`

## Dependencies

- **jQAssistant 2.9.1** (BOM in `dependencyManagement`, no parent POM)
- **Java 11** baseline
- Depends on `jqassistant-plugin-common` and `jqassistant-plugin-java` (provided scope)
- No additional runtime dependencies (pure Cypher rules)

## Reference: jQAssistant Manifest Graph Structure

The built-in Java plugin creates this graph from JAR scanning:

```
(:Jar:Archive)
  └─[:CONTAINS]→ (:Manifest:File)
       └─[:DECLARES]→ (:ManifestSection {name:"Main"})
            └─[:HAS]→ (:ManifestEntry {name:"Bundle-SymbolicName", value:"com.example.bundle;singleton:=true"})
            └─[:HAS]→ (:ManifestEntry {name:"Bundle-Version", value:"1.0.0"})
            └─[:HAS]→ (:ManifestEntry {name:"Require-Bundle", value:"org.eclipse.core.runtime,com.example.other"})
            └─[:HAS]→ (:ManifestEntry {name:"Export-Package", value:"com.example.api,com.example.spi"})
            ...
```

All OSGi concepts query this structure. When writing new concepts, always start from `(:Osgi:Bundle)-[:CONTAINS]->(:Manifest:File)-[:DECLARES]->(:ManifestSection {name:"Main"})-[:HAS]->(:ManifestEntry)`.

## Reference: jQAssistant java:ArtifactDependency Pattern

The `java:ArtifactDependency` concept (in the core Java plugin) is a useful pattern reference. It creates `[:DEPENDS_ON]` relationships between artifacts based on type dependencies. Our `osgi:RequiredBundleResolution` follows a similar approach — resolving declared dependencies to actual graph nodes.

## Sample Project for Testing

The Eclipse RCP project [intelligentautomation/proteus](https://github.com/intelligentautomation/proteus) on GitHub has a `.jqassistant.yml` configured to scan its 16 OSGi plugin directories. Use it as the primary test bed.

**Note:** The proteus plugins are in source/directory form, not JARs. The `java:classpath` scope in `.jqassistant.yml` may need adjustment. Building proteus into JARs first would give the most reliable results.

See `CLAUDE.local.md` (not committed) for local checkout paths and machine-specific notes.

## Planned Enhancements

- [ ] Eclipse feature.xml scanner (requires Java scanner plugin, not just Cypher rules)
- [ ] XO descriptor model interfaces for type-safe store access
- [ ] Integration tests with sample MANIFEST.MF files
- [ ] `osgi:BundleDependency` concept (analogous to `java:ArtifactDependency`)
- [ ] Provide-Package (legacy) and DynamicImport-Package support
- [ ] Eclipse extension point / extension relationships (plugin.xml)
- [ ] P2 repository metadata analysis
