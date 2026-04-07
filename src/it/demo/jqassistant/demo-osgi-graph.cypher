// Demo OSGi graph for testing the jqa-eclipse-osgi-plugin
// Simulates 6 bundles with manifests, packages, types, and dependencies
//
// Bundle structure:
//   demo.osgi.app.core    - main application, requires api + logging, imports util
//   demo.osgi.app.ui      - UI layer, requires core + api
//   demo.osgi.api         - public API, no dependencies
//   demo.osgi.util        - utility library, no dependencies
//   demo.osgi.logging     - logging facade, imports util
//   org.external.lib      - external library, required by core but NOT in scan (unresolved)
//
// Dependency types:
//   core -> api:      Require-Bundle + package wiring (redundant, weight > 0)
//   core -> logging:  Require-Bundle only (weight > 0)
//   core -> util:     Package wiring only (weight > 0)
//   core -> external: Require-Bundle unresolved (weight 0)
//   ui -> core:       Require-Bundle + package wiring (redundant, weight > 0)
//   ui -> api:        Package wiring only (weight > 0)
//   logging -> util:  Package wiring only (weight 0, manifest only)

// ============================================================
// Clean up any previous demo data
// ============================================================
MATCH (n) WHERE n.bundleSymbolicName STARTS WITH 'demo.osgi.'
  OR n.symbolicName STARTS WITH 'demo.osgi.'
  OR n.symbolicName = 'org.external.lib'
  OR n.fqn STARTS WITH 'demo.osgi.'
DETACH DELETE n;

// ============================================================
// Create JAR/Archive nodes (bundles)
// ============================================================
CREATE (core:Jar:Archive:File {fileName: 'demo.osgi.app.core-1.0.0.jar'})
CREATE (ui:Jar:Archive:File {fileName: 'demo.osgi.app.ui-1.0.0.jar'})
CREATE (api:Jar:Archive:File {fileName: 'demo.osgi.api-2.0.0.jar'})
CREATE (util:Jar:Archive:File {fileName: 'demo.osgi.util-1.5.0.jar'})
CREATE (logging:Jar:Archive:File {fileName: 'demo.osgi.logging-1.0.0.jar'})

// ============================================================
// Create Manifest structure for each bundle
// ============================================================

// --- demo.osgi.app.core ---
CREATE (core)-[:CONTAINS]->(mf_core:Manifest:File {fileName: 'MANIFEST.MF'})
CREATE (mf_core)-[:DECLARES]->(ms_core:ManifestSection {name: 'Main'})
CREATE (ms_core)-[:HAS]->(:ManifestEntry {name: 'Bundle-SymbolicName', value: 'demo.osgi.app.core'})
CREATE (ms_core)-[:HAS]->(:ManifestEntry {name: 'Bundle-Version', value: '1.0.0'})
CREATE (ms_core)-[:HAS]->(:ManifestEntry {name: 'Bundle-Name', value: 'Demo App Core'})
CREATE (ms_core)-[:HAS]->(:ManifestEntry {name: 'Require-Bundle', value: 'demo.osgi.api;bundle-version="[2.0.0,3.0.0)",demo.osgi.logging;bundle-version="1.0.0",org.external.lib;resolution:=optional'})
CREATE (ms_core)-[:HAS]->(:ManifestEntry {name: 'Import-Package', value: 'demo.osgi.api;version="2.0.0",demo.osgi.api.model;version="2.0.0",demo.osgi.util;version="[1.0.0,2.0.0)"'})
CREATE (ms_core)-[:HAS]->(:ManifestEntry {name: 'Export-Package', value: 'demo.osgi.app.core;version="1.0.0",demo.osgi.app.core.service;version="1.0.0"'})

// --- demo.osgi.app.ui ---
CREATE (ui)-[:CONTAINS]->(mf_ui:Manifest:File {fileName: 'MANIFEST.MF'})
CREATE (mf_ui)-[:DECLARES]->(ms_ui:ManifestSection {name: 'Main'})
CREATE (ms_ui)-[:HAS]->(:ManifestEntry {name: 'Bundle-SymbolicName', value: 'demo.osgi.app.ui'})
CREATE (ms_ui)-[:HAS]->(:ManifestEntry {name: 'Bundle-Version', value: '1.0.0'})
CREATE (ms_ui)-[:HAS]->(:ManifestEntry {name: 'Bundle-Name', value: 'Demo App UI'})
CREATE (ms_ui)-[:HAS]->(:ManifestEntry {name: 'Require-Bundle', value: 'demo.osgi.app.core;bundle-version="1.0.0"'})
CREATE (ms_ui)-[:HAS]->(:ManifestEntry {name: 'Import-Package', value: 'demo.osgi.api;version="2.0.0",demo.osgi.api.model;version="2.0.0",demo.osgi.app.core;version="1.0.0",demo.osgi.app.core.service;version="1.0.0"'})
CREATE (ms_ui)-[:HAS]->(:ManifestEntry {name: 'Export-Package', value: 'demo.osgi.app.ui;version="1.0.0"'})

// --- demo.osgi.api ---
CREATE (api)-[:CONTAINS]->(mf_api:Manifest:File {fileName: 'MANIFEST.MF'})
CREATE (mf_api)-[:DECLARES]->(ms_api:ManifestSection {name: 'Main'})
CREATE (ms_api)-[:HAS]->(:ManifestEntry {name: 'Bundle-SymbolicName', value: 'demo.osgi.api'})
CREATE (ms_api)-[:HAS]->(:ManifestEntry {name: 'Bundle-Version', value: '2.0.0'})
CREATE (ms_api)-[:HAS]->(:ManifestEntry {name: 'Bundle-Name', value: 'Demo API'})
CREATE (ms_api)-[:HAS]->(:ManifestEntry {name: 'Export-Package', value: 'demo.osgi.api;version="2.0.0",demo.osgi.api.model;version="2.0.0"'})

// --- demo.osgi.util ---
CREATE (util)-[:CONTAINS]->(mf_util:Manifest:File {fileName: 'MANIFEST.MF'})
CREATE (mf_util)-[:DECLARES]->(ms_util:ManifestSection {name: 'Main'})
CREATE (ms_util)-[:HAS]->(:ManifestEntry {name: 'Bundle-SymbolicName', value: 'demo.osgi.util'})
CREATE (ms_util)-[:HAS]->(:ManifestEntry {name: 'Bundle-Version', value: '1.5.0'})
CREATE (ms_util)-[:HAS]->(:ManifestEntry {name: 'Bundle-Name', value: 'Demo Utilities'})
CREATE (ms_util)-[:HAS]->(:ManifestEntry {name: 'Export-Package', value: 'demo.osgi.util;version="1.5.0",demo.osgi.util.io;version="1.5.0"'})

// --- demo.osgi.logging ---
CREATE (logging)-[:CONTAINS]->(mf_log:Manifest:File {fileName: 'MANIFEST.MF'})
CREATE (mf_log)-[:DECLARES]->(ms_log:ManifestSection {name: 'Main'})
CREATE (ms_log)-[:HAS]->(:ManifestEntry {name: 'Bundle-SymbolicName', value: 'demo.osgi.logging'})
CREATE (ms_log)-[:HAS]->(:ManifestEntry {name: 'Bundle-Version', value: '1.0.0'})
CREATE (ms_log)-[:HAS]->(:ManifestEntry {name: 'Bundle-Name', value: 'Demo Logging'})
CREATE (ms_log)-[:HAS]->(:ManifestEntry {name: 'Import-Package', value: 'demo.osgi.util;version="[1.0.0,2.0.0)";resolution:=optional'})
CREATE (ms_log)-[:HAS]->(:ManifestEntry {name: 'Export-Package', value: 'demo.osgi.logging;version="1.0.0"'})

// ============================================================
// Create Java Packages and Types (simulating scan results)
// ============================================================

// --- demo.osgi.app.core packages and types ---
CREATE (core)-[:CONTAINS]->(pkg_core:Package:File:Directory:Java {fqn: 'demo.osgi.app.core'})
CREATE (pkg_core)-[:CONTAINS]->(t_core_app:Type:File:Java:ByteCode:Class {fqn: 'demo.osgi.app.core.Application'})
CREATE (pkg_core)-[:CONTAINS]->(t_core_mgr:Type:File:Java:ByteCode:Class {fqn: 'demo.osgi.app.core.Manager'})
CREATE (core)-[:CONTAINS]->(pkg_core_svc:Package:File:Directory:Java {fqn: 'demo.osgi.app.core.service'})
CREATE (pkg_core_svc)-[:CONTAINS]->(t_core_svc:Type:File:Java:ByteCode:Class {fqn: 'demo.osgi.app.core.service.CoreService'})

// --- demo.osgi.app.ui packages and types ---
CREATE (ui)-[:CONTAINS]->(pkg_ui:Package:File:Directory:Java {fqn: 'demo.osgi.app.ui'})
CREATE (pkg_ui)-[:CONTAINS]->(t_ui_main:Type:File:Java:ByteCode:Class {fqn: 'demo.osgi.app.ui.MainView'})
CREATE (pkg_ui)-[:CONTAINS]->(t_ui_ctrl:Type:File:Java:ByteCode:Class {fqn: 'demo.osgi.app.ui.Controller'})

// --- demo.osgi.api packages and types ---
CREATE (api)-[:CONTAINS]->(pkg_api:Package:File:Directory:Java {fqn: 'demo.osgi.api'})
CREATE (pkg_api)-[:CONTAINS]->(t_api_svc:Type:File:Java:ByteCode:Interface {fqn: 'demo.osgi.api.Service'})
CREATE (pkg_api)-[:CONTAINS]->(t_api_factory:Type:File:Java:ByteCode:Interface {fqn: 'demo.osgi.api.Factory'})
CREATE (api)-[:CONTAINS]->(pkg_api_model:Package:File:Directory:Java {fqn: 'demo.osgi.api.model'})
CREATE (pkg_api_model)-[:CONTAINS]->(t_api_entity:Type:File:Java:ByteCode:Class {fqn: 'demo.osgi.api.model.Entity'})
CREATE (pkg_api_model)-[:CONTAINS]->(t_api_dto:Type:File:Java:ByteCode:Class {fqn: 'demo.osgi.api.model.DataObject'})

// --- demo.osgi.util packages and types ---
CREATE (util)-[:CONTAINS]->(pkg_util:Package:File:Directory:Java {fqn: 'demo.osgi.util'})
CREATE (pkg_util)-[:CONTAINS]->(t_util_helper:Type:File:Java:ByteCode:Class {fqn: 'demo.osgi.util.Helper'})
CREATE (pkg_util)-[:CONTAINS]->(t_util_config:Type:File:Java:ByteCode:Class {fqn: 'demo.osgi.util.Config'})
CREATE (util)-[:CONTAINS]->(pkg_util_io:Package:File:Directory:Java {fqn: 'demo.osgi.util.io'})
CREATE (pkg_util_io)-[:CONTAINS]->(t_util_reader:Type:File:Java:ByteCode:Class {fqn: 'demo.osgi.util.io.FileReader'})

// --- demo.osgi.logging packages and types ---
CREATE (logging)-[:CONTAINS]->(pkg_log:Package:File:Directory:Java {fqn: 'demo.osgi.logging'})
CREATE (pkg_log)-[:CONTAINS]->(t_log_logger:Type:File:Java:ByteCode:Class {fqn: 'demo.osgi.logging.Logger'})
CREATE (pkg_log)-[:CONTAINS]->(t_log_handler:Type:File:Java:ByteCode:Class {fqn: 'demo.osgi.logging.Handler'})

// ============================================================
// Create Type DEPENDS_ON relationships (simulating actual code usage)
// ============================================================

// core -> api (real code dependency: Application uses Service and Entity)
CREATE (t_core_app)-[:DEPENDS_ON]->(t_api_svc)
CREATE (t_core_app)-[:DEPENDS_ON]->(t_api_entity)
CREATE (t_core_mgr)-[:DEPENDS_ON]->(t_api_factory)
CREATE (t_core_mgr)-[:DEPENDS_ON]->(t_api_dto)
CREATE (t_core_svc)-[:DEPENDS_ON]->(t_api_svc)

// core -> util (real code dependency: Manager uses Helper)
CREATE (t_core_mgr)-[:DEPENDS_ON]->(t_util_helper)
CREATE (t_core_mgr)-[:DEPENDS_ON]->(t_util_config)

// core -> logging (real code dependency: Application uses Logger)
CREATE (t_core_app)-[:DEPENDS_ON]->(t_log_logger)

// ui -> core (real code dependency: MainView uses Application and CoreService)
CREATE (t_ui_main)-[:DEPENDS_ON]->(t_core_app)
CREATE (t_ui_ctrl)-[:DEPENDS_ON]->(t_core_svc)

// ui -> api (real code dependency: Controller uses Service and Entity)
CREATE (t_ui_ctrl)-[:DEPENDS_ON]->(t_api_svc)
CREATE (t_ui_ctrl)-[:DEPENDS_ON]->(t_api_entity)
CREATE (t_ui_main)-[:DEPENDS_ON]->(t_api_dto)

// logging -> util: NO code dependency (manifest import only, weight should be 0)

// ============================================================
// Summary of expected results (after running plugin concepts):
// ============================================================
// Bundle                 | Exports                                    | Imports/Requires
// demo.osgi.app.core     | core, core.service                         | Requires: api, logging, org.external.lib(opt) / Imports: api, api.model, util
// demo.osgi.app.ui       | ui                                         | Requires: core / Imports: api, api.model, core, core.service
// demo.osgi.api          | api, api.model                             | (none)
// demo.osgi.util         | util, util.io                              | (none)
// demo.osgi.logging      | logging                                    | Imports: util (optional)
//
// Expected DEPENDS_ON edges:
//   core -> api:      viaRequireBundle=true, viaPackageWiring=true (redundant), weight=5
//   core -> util:     viaRequireBundle=false, viaPackageWiring=true, weight=2
//   core -> logging:  viaRequireBundle=true, viaPackageWiring=false, weight=1
//   core -> external: unresolved, weight=0
//   ui -> core:       viaRequireBundle=true, viaPackageWiring=true (redundant), weight=2
//   ui -> api:        viaRequireBundle=false, viaPackageWiring=true, weight=3
//   logging -> util:  viaRequireBundle=false, viaPackageWiring=true, weight=0 (manifest only)
;
