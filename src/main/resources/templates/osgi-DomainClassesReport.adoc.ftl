[#ftl encoding="UTF-8"]
[#assign bundle = key!"(unknown)"]
[[bundle-${bundle}-domain-classes]]
== Domain Classes

[#if rows?size == 0]
NOTE: No domain classes found.
[#else]
[options="header",cols="1"]
|===
| Class
[#list rows as row]
| ${row.ClassName!"-"}
[/#list]
|===
[/#if]
