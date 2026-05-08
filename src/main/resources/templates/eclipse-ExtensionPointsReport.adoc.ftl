[#ftl encoding="UTF-8"]
[#assign bundle = key!"(unknown)"]
[[bundle-${bundle}-extension-points]]
== Extension Points

[#if rows?size == 0]
NOTE: No extension points declared.
[#else]
[options="header",cols="2,2,2"]
|===
| Id | Name | Schema
[#list rows as row]
| ${row.ExtensionPointId!"-"} | ${row.Name!""} | ${row.Schema!""}
[/#list]
|===
[/#if]
