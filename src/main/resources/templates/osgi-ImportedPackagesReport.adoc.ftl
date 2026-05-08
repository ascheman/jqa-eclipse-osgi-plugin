[#ftl encoding="UTF-8"]
[#assign bundle = key!"(unknown)"]
[[bundle-${bundle}-imported-packages]]
== Imported Packages

[#if rows?size == 0]
NOTE: No Import-Package entries.
[#else]
[options="header",cols="3,2,1,1"]
|===
| Package | Version | Optional | Resolved
[#list rows as row]
| ${row.Package!"-"} | ${row.Version!""} | ${row.Optional!"false"} | ${row.Resolved!"false"}
[/#list]
|===
[/#if]
