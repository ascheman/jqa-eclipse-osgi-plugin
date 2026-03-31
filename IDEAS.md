# Ideas Backlog

## HTML Graph Viewer

- [ ] Export graph data as CSV (client-side download button)
- [ ] Load graph from external CSV file (file picker + reload button)
- [ ] Make HTML template a standalone reusable viewer (embedded JSON or CSV input)
- [ ] Color scheme for unused dependencies (red for weight=0, orange for unresolved) — stashed, needs rework
- [ ] Embed Cytoscape.js for fully offline use (no CDN)

## Concepts & Constraints

- [ ] Eclipse feature.xml scanner (requires Java scanner plugin, not just Cypher rules)
- [ ] XO descriptor model interfaces for type-safe store access
- [ ] Integration tests with sample MANIFEST.MF files
- [ ] Provide-Package (legacy) and DynamicImport-Package support
- [ ] Eclipse extension point / extension relationships (plugin.xml)
- [ ] P2 repository metadata analysis

## Reports

- [ ] Package-level dependency graph (not just bundle-level)
- [ ] Dependency weight as edge thickness in PlantUML
- [ ] Mermaid.js output as alternative to PlantUML
