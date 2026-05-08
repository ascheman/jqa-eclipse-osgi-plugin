[#ftl encoding="UTF-8"]
[#--
  Default template for the osgi:BundleMetadataReport concept.
  When the concept declares <report type="template" primaryColumn="SymbolicName"/>,
  this template is rendered once per bundle with `key` set to the bundle's
  Symbolic Name and `rows` containing the (typically single) row for it.
--]
[#assign bundle = key!"(unknown)"]
[[bundle-${bundle}-metadata]]
== MetaData

[options="header",cols="1,3"]
|===
| Property | Value
[#list rows as row]
| Symbolic Name | ${row.SymbolicName!"-"}
| Version       | ${row.Version!"-"}
| Name          | ${row.Name!"-"}
| Vendor        | ${row.Vendor!"-"}
| Activator     | ${row.Activator!"-"}
[/#list]
|===
