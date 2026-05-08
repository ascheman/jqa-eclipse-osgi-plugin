[#ftl encoding="UTF-8"]
[#assign bundle = key!"(unknown)"]
[[bundle-${bundle}-source-structure]]
== Source Structure

[#if rows?size == 0]
NOTE: No packages found.
[#else]
[options="header",cols="1"]
|===
| Package
[#list rows as row]
| ${row.Package!"-"}
[/#list]
|===
[/#if]
