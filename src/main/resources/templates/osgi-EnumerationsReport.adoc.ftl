[#ftl encoding="UTF-8"]
[#assign bundle = key!"(unknown)"]
[[bundle-${bundle}-enumerations]]
== Enumerations

[#if rows?size == 0]
NOTE: No enumerations found.
[#else]
[options="header",cols="1"]
|===
| Enum
[#list rows as row]
| ${row.EnumName!"-"}
[/#list]
|===
[/#if]
