[#ftl encoding="UTF-8"]
[#assign bundle = key!"(unknown)"]
[[bundle-${bundle}-config-files]]
== Configuration Files

[#if rows?size == 0]
NOTE: No configuration files.
[#else]
[options="header",cols="1"]
|===
| File
[#list rows as row]
| ${row.FilePath!"-"}
[/#list]
|===
[/#if]
