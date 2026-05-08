[#ftl encoding="UTF-8"]
[#assign bundle = key!"(unknown)"]
[[bundle-${bundle}-manifest]]
== Manifest Details

[#if rows?size == 0]
NOTE: No manifest details recorded.
[#else]
[options="header",cols="1,3"]
|===
| Header | Value
[#list rows as row]
| ${row.HeaderName!"-"} | ${row.HeaderValue!""}
[/#list]
|===
[/#if]
