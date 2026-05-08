[#ftl encoding="UTF-8"]
[#assign bundle = key!"(unknown)"]
[[bundle-${bundle}-exported-packages]]
== Exported Packages

[#if rows?size == 0]
NOTE: No Export-Package entries.
[#else]
[options="header",cols="3,1"]
|===
| Package | Version
[#list rows as row]
| ${row.Package!"-"} | ${row.Version!""}
[/#list]
|===
[/#if]
