[#ftl encoding="UTF-8"]
[#assign bundle = key!"(unknown)"]
[[bundle-${bundle}-extensions]]
== Extensions

[#if rows?size == 0]
NOTE: No extensions contributed.
[#else]
[options="header",cols="3,2,1,3"]
|===
| Point | Name | Id | Implementation
[#list rows as row]
| ${row.Point!"-"} | ${row.Name!""} | ${row.ExtensionId!""} | ${row.Implementation!""}
[/#list]
|===
[/#if]
