[#ftl encoding="UTF-8"]
[#assign bundle = key!"(unknown)"]
[[bundle-${bundle}-required-bundles]]
== Required Bundles

[#if rows?size == 0]
NOTE: No Require-Bundle entries.
[#else]
[options="header",cols="3,2,1,1"]
|===
| Required Bundle | Version Range | Optional | Resolved
[#list rows as row]
| ${row.RequiredBundle!"-"} | ${row.VersionRange!""} | ${row.Optional!"false"} | ${row.Resolved!"false"}
[/#list]
|===
[/#if]
