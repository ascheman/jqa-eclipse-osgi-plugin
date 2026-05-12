[#ftl encoding="UTF-8"]
[#--
  Per-bundle metrics fragment for osgi:metrics:BundleMetricsReport.
  Renders one AsciiDoc include per :ProjectBundle into
  target/jqassistant/report/asciidoc/<SymbolicName>/osgi-metrics-BundleMetricsReport.adoc.

  Empty fragments are produced for bundles that pass the primary-key-pattern
  filter but did not match :ProjectBundle (stub-source mechanism), so includes
  in higher-level documents do not break.
--]
[#assign bundle = key!"(unknown)"]
[[bundle-${bundle}-metrics]]
== Metrics

[#if rows?size == 0]
NOTE: Not a project bundle (did not match `projectBundlePattern`), no metrics collected.
[#else]
[#assign row = rows[0]]
.Size
[options="header",cols="2,1"]
|===
| Property | Value
| Types          | ${row.Types!"-"}
| Abstract Types | ${row.AbstractTypes!"-"}
| Effective LoC  | ${row.LoC!"-"}
|===

.Coupling and main-sequence position
[options="header",cols="2,1,3"]
|===
| Property | Value | Notes
| Afferent Coupling (Ca)            | ${row.Ca!"-"}         | Project bundles depending on this bundle
| Efferent Coupling (Ce)            | ${row.Ce!"-"}         | Project bundles this bundle depends on
| Ca (type-weighted)                | ${row.CaWeighted!"-"} | Sum of incoming `typeDependencyCount`
| Ce (type-weighted)                | ${row.CeWeighted!"-"} | Sum of outgoing `typeDependencyCount`
| Instability (I)                   | ${row.I!"-"}          | `Ce / (Ca + Ce)`, range [0,1]
| Abstractness (A)                  | ${row.A!"-"}          | `(interfaces + abstract) / types`, range [0,1]
| Distance from main sequence (D)   | ${row.D!"-"}          | `\|A + I - 1\|`, range [0,1] (lower is better)
| Participates in cycle             | ${row.InCycle!"-"}    | `true` if reachable from itself via DEPENDS_ON
|===
[/#if]
