[#ftl encoding="UTF-8"]
[#--
  Per-bundle internal package dependency / cycle diagram.
  Each row is one (Source, Target, Weight, InCycle) edge inside `key`'s bundle.
  Renders a PlantUML block with cycle edges highlighted in red; falls back to a
  NOTE when the bundle has no internal package-level edges at all.
--]
[#assign bundle = key!"(unknown)"]
[[bundle-${bundle}-package-cycles]]
== Internal Package Cycles

[#if rows?size == 0]
NOTE: No internal package-to-package dependencies recorded for this bundle.
[#else]
[#assign cycleEdges = 0]
[#list rows as row][#if row.InCycle?lower_case == "true"][#assign cycleEdges = cycleEdges + 1][/#if][/#list]
[#if cycleEdges == 0]
NOTE: ${rows?size} internal package-to-package dependencies, none of them in a cycle.
[#else]
${cycleEdges} of ${rows?size} internal package-to-package dependencies participate in a cycle (shown in red).

[#-- PlantUML arrow syntaxes are kept in string literals so the embedded
     `[#…]` does not collide with FreeMarker's square-bracket directive syntax. --]
[#assign cycleArrow = "-[#red,thickness=2]->"]
[#assign plainArrow = "-->"]
[plantuml,bundle-${bundle}-package-cycles,svg]
....
@startuml
left to right direction
skinparam packageStyle rectangle
hide empty members
[#list rows as row]
[#if row.InCycle?lower_case == "true"]
"${row.Source}" ${cycleArrow} "${row.Target}" : ${row.Weight!"0"}
[#else]
"${row.Source}" ${plainArrow} "${row.Target}" : ${row.Weight!"0"}
[/#if]
[/#list]
@enduml
....
[/#if]
[/#if]
