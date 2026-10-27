package net.aschemann.jqassistant.plugins.eclipse.osgi;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.buschmais.jqassistant.core.report.api.model.Result;
import com.buschmais.jqassistant.core.rule.api.model.Constraint;
import com.buschmais.jqassistant.core.rule.api.model.RuleException;
import com.buschmais.jqassistant.core.test.plugin.AbstractPluginIT;
import com.buschmais.jqassistant.plugin.java.api.scanner.JavaScope;

/**
 * Integration test for the Eclipse OSGi rule set. Builds a small multi-bundle fixture in-process
 * (a host, two fragments, and an unrelated bundle, some sharing fully qualified class names), scans
 * the resulting JARs into an in-memory store, then applies the concepts and validates the
 * constraints -- asserting the exact behaviour that requires real JAR/MANIFEST/plugin.xml scanning:
 * fragment.xml handling, host-scoped class resolution, ambiguous types across fragments, and the
 * command / property-reference consistency checks.
 */
class EclipseOsgiRulesIT extends AbstractPluginIT {

    private static final String HOST = "com.example.host";
    private static final String WIN32 = "com.example.host.win32";
    private static final String NL = "com.example.host.nl";
    private static final String OTHER = "com.example.other";

    private static File hostJar;
    private static File win32Jar;
    private static File nlJar;
    private static File otherJar;

    @BeforeAll
    static void buildFixtureJars(@TempDir Path work) throws Exception {
        // --- host: declares property testers, a command, views, and handlers with expressions ---
        Bundle host = new Bundle(HOST, null);
        host.addClass("com.example.vm.OrderViewModel", "public class OrderViewModel {}");
        // covered by no property tester -> eclipse:ViewModelWithoutPropertyTester
        host.addClass("com.example.vm.InvoiceViewModel", "public class InvoiceViewModel {}");
        host.addClass("com.example.tester.HostTester", "public class HostTester {}");
        host.addClass("com.example.shared.Dup", "public class Dup {}");
        host.addClass("com.example.view.OrderView", "public class OrderView {}");
        host.addClass("com.example.handler.DoThingHandler", "public class DoThingHandler {}");
        host.resources.put("plugin.xml", HOST_PLUGIN_XML);

        // --- win32 fragment: declares a tester via fragment.xml; ships a fragment-only tester,
        //     a fragment-only view, and a duplicate of the host's shared.Dup ---
        Bundle win32 = new Bundle(WIN32, HOST);
        win32.addClass("com.example.tester.WinTester", "public class WinTester {}");
        win32.addClass("com.example.tester.FragmentOnlyTester", "public class FragmentOnlyTester {}");
        win32.addClass("com.example.view.FragmentOnlyView", "public class FragmentOnlyView {}");
        win32.addClass("com.example.shared.Dup", "public class Dup {}");
        win32.resources.put("fragment.xml", WIN32_FRAGMENT_XML);

        // --- nl fragment: no fragment.xml (resource-only fragment) ---
        Bundle nl = new Bundle(NL, HOST);
        nl.addClass("com.example.nl.Messages", "public class Messages {}");

        // --- other: unrelated bundle carrying the same FQNs (must NOT be pulled into resolution) ---
        Bundle other = new Bundle(OTHER, null);
        other.addClass("com.example.tester.WinTester", "public class WinTester {}");
        other.addClass("com.example.shared.Dup", "public class Dup {}");

        hostJar = host.build(work);
        win32Jar = win32.build(work);
        nlJar = nl.build(work);
        otherJar = other.build(work);
    }

    private void scanFixture() {
        for (File jar : List.of(hostJar, win32Jar, nlJar, otherJar)) {
            store.beginTransaction();
            getScanner().scan(jar, jar.getAbsolutePath(), JavaScope.CLASSPATH);
            store.commitTransaction();
        }
    }

    @Test
    void baseGraphAndPropertyTestersFromPluginAndFragmentXml() throws RuleException {
        scanFixture();

        // applying the concept pulls in its prerequisites (osgi:Bundle, eclipse:Extension, ...)
        applyConcept("eclipse:PropertyTester");

        // four bundles get the :Osgi:Bundle label
        assertThat(single("MATCH (b:Osgi:Bundle) RETURN count(b) AS c")).isEqualTo(4L);

        // five testers: four from the host plugin.xml, one from the fragment.xml. Two of the host
        // ones are deliberately broken fixtures for the constraints further down -- t4 names a
        // class that exists nowhere, t5 a receiver type that exists nowhere.
        assertThat(column("MATCH (b:Osgi:Bundle)-[:DECLARES_PROPERTY_TESTER]->(pt:Eclipse:PropertyTester) "
                + "RETURN b.bundleSymbolicName + '/' + pt.testerClass + '/' + pt.testerType AS v", "v"))
                .containsExactlyInAnyOrder(
                        HOST + "/com.example.tester.HostTester/com.example.vm.OrderViewModel",
                        HOST + "/com.example.tester.HostTester/com.example.vm.MissingViewModel",
                        HOST + "/com.example.tester.FragmentOnlyTester/com.example.vm.OrderViewModel",
                        HOST + "/com.example.tester.MissingTester/com.example.vm.OrderViewModel",
                        WIN32 + "/com.example.tester.WinTester/com.example.vm.OrderViewModel");
    }

    @Test
    void propertyTesterClassAndTypeResolveHostScopedToExactlyOneTarget() throws RuleException {
        scanFixture();
        applyConcept("eclipse:PropertyTesterImplementedBy");
        applyConcept("eclipse:PropertyTesterTestsType");

        // every tester resolves to exactly one implementation and one receiver type
        // MissingTester is an intentionally unresolvable fixture, see
        // unresolvedPropertyTesterClassIsReported below.
        List<Map<String, Object>> rows = rows(
                "MATCH (b:Osgi:Bundle)-[:DECLARES_PROPERTY_TESTER]->(pt:Eclipse:PropertyTester) "
                        + "WHERE pt.testerClass <> 'com.example.tester.MissingTester' "
                        + "OPTIONAL MATCH (pt)-[:IMPLEMENTED_BY]->(impl:Type)<-[:CONTAINS]-(ib:Osgi:Bundle) "
                        + "RETURN pt.testerClass AS cls, count(DISTINCT impl) AS impls, "
                        + "collect(DISTINCT ib.bundleSymbolicName) AS resolvedIn");
        for (Map<String, Object> r : rows) {
            assertThat(r.get("impls")).as("one implementation per tester %s", r.get("cls")).isEqualTo(1L);
        }
        // the fragment-declared tester's class resolves to the fragment, NOT the unrelated 'other' copy
        assertThat(column(
                "MATCH (:Osgi:Bundle {bundleSymbolicName:'" + WIN32 + "'})-[:DECLARES_PROPERTY_TESTER]->(pt) "
                        + "MATCH (pt)-[:IMPLEMENTED_BY]->(t:Type)<-[:CONTAINS]-(b:Osgi:Bundle) "
                        + "RETURN b.bundleSymbolicName AS v", "v"))
                .containsExactly(WIN32);
    }

    @Test
    void hostDeclaredTesterWithFragmentOnlyClassIsWarned() throws RuleException {
        scanFixture();
        assertThat(constraintColumn("eclipse:PropertyTesterClassProvidedByFragment", "TesterClass"))
                .containsExactly("com.example.tester.FragmentOnlyTester");
    }

    @Test
    void duplicateTypeAcrossHostAndFragmentIsAmbiguousButUnrelatedIsNot() throws RuleException {
        scanFixture();
        // only shared.Dup (host + win32) is flagged; the unrelated 'other' copy is not part of the class space
        assertThat(constraintColumn("osgi:AmbiguousTypeAcrossFragments", "DuplicatedType"))
                .containsExactly("com.example.shared.Dup");
    }

    @Test
    void extensionClassResolutionAndFragmentDependencyChecks() throws RuleException {
        scanFixture();
        // a genuinely missing extension class is reported; tester classes and fragment-resolved ones are not
        assertThat(constraintColumn("eclipse:UnresolvedExtensionClass", "UnresolvedClass"))
                .containsExactly("com.example.view.MissingView");
        // a host view whose class lives only in a fragment is warned (property testers excluded here)
        assertThat(constraintColumn("eclipse:ExtensionClassProvidedByFragment", "Class"))
                .containsExactly("com.example.view.FragmentOnlyView");
    }

    @Test
    void unresolvedCommandReferenceIsReported() throws RuleException {
        scanFixture();
        assertThat(constraintColumn("eclipse:UnresolvedCommandReference", "UnresolvedCommandId"))
                .containsExactly("com.example.command.doesNotExist");
    }

    @Test
    void unsatisfiedPropertyReferenceIsReportedOnlyForKnownNamespaces() throws RuleException {
        scanFixture();
        // the typo in a namespace we own is flagged; the framework property (unknown namespace) is ignored
        assertThat(constraintColumn("eclipse:UnsatisfiedPropertyReference", "UnsatisfiedProperty"))
                .containsExactly("com.example.host.isOpne");
    }

    @Test
    void fragmentWithoutDescriptorIsFlagged() throws RuleException {
        scanFixture();
        assertThat(constraintColumn("eclipse:FragmentWithoutDescriptor", "Fragment"))
                .containsExactly(NL);
    }

    @Test
    void analyzedBundlePatternRestrictsEvaluationToSelectedBundles() throws RuleException {
        scanFixture();
        // restrict constraint evaluation to a pattern that excludes every customer bundle under
        // test -> all constraints against them fall silent, while the bundles stay in the graph.
        Map<String, String> onlyOther = Map.of("analyzedBundlePattern", "com\\.example\\.other");
        assertThat(validateConstraint("eclipse:UnresolvedExtensionClass", onlyOther).getRows())
                .as("unresolved extension class silenced for out-of-scope bundles").isEmpty();
        assertThat(validateConstraint("eclipse:UnresolvedCommandReference", onlyOther).getRows())
                .as("unresolved command reference silenced").isEmpty();
        assertThat(validateConstraint("osgi:AmbiguousTypeAcrossFragments", onlyOther).getRows())
                .as("ambiguous type silenced").isEmpty();
        assertThat(validateConstraint("eclipse:PropertyTesterClassProvidedByFragment", onlyOther).getRows())
                .as("property tester fragment dependency silenced").isEmpty();
    }

    @Test
    void unresolvedPropertyTesterClassIsReported() throws RuleException {
        scanFixture();
        // only the class that exists in no bundle at all; the fragment-provided one is reported by
        // eclipse:PropertyTesterClassProvidedByFragment instead, not here
        assertThat(constraintColumn("eclipse:UnresolvedPropertyTesterClass", "MissingTesterClass"))
                .containsExactly("com.example.tester.MissingTester");
    }

    @Test
    void unresolvedPropertyTesterReceiverTypeIsReported() throws RuleException {
        scanFixture();
        // the tester class itself resolves (HostTester), only its receiver type does not
        assertThat(constraintColumn("eclipse:UnresolvedPropertyTesterType", "UnresolvedReceiverType"))
                .containsExactly("com.example.vm.MissingViewModel");
    }

    @Test
    void viewModelWithoutPropertyTesterIsReported() throws RuleException {
        scanFixture();
        applyConcept("eclipse:ViewModelType");

        // the default viewModelPattern is ".*ViewModel$", so both view models are labelled
        assertThat(column("MATCH (t:Eclipse:ViewModelType) RETURN t.fqn AS v", "v"))
                .containsExactlyInAnyOrder("com.example.vm.OrderViewModel", "com.example.vm.InvoiceViewModel");

        // ... but only the one no tester tests is reported. OrderViewModel is covered by t1.
        assertThat(constraintColumn("eclipse:ViewModelWithoutPropertyTester", "UncoveredViewModelType"))
                .containsExactly("com.example.vm.InvoiceViewModel");
    }

    @Test
    void commandsAndTheirReferencesFormAResolvedGraph() throws RuleException {
        scanFixture();
        applyConcept("eclipse:CommandReference");

        // the declared command, reached through the concept's own prerequisite eclipse:Command
        assertThat(column("MATCH (:Osgi:Bundle)-[:DECLARES_COMMAND]->(c:Eclipse:Command) "
                + "RETURN c.commandId AS v", "v"))
                .containsExactly("com.example.command.doThing");

        // two references: the handler for the declared command resolves, the other does not
        assertThat(column("MATCH (r:Eclipse:CommandReference) "
                + "RETURN r.commandId + '=' + size([(r)-[:REFERENCES_COMMAND]->(c) | c]) AS v", "v"))
                .containsExactlyInAnyOrder(
                        "com.example.command.doThing=1",
                        "com.example.command.doesNotExist=0");
    }

    @Test
    void fragmentDescriptorIsRecordedOnlyForFragmentsCarryingFragmentXml() throws RuleException {
        scanFixture();
        applyConcept("eclipse:FragmentDescriptor");

        // win32 ships a fragment.xml, the resource-only nl fragment does not
        assertThat(column("MATCH (f:Osgi:Fragment)-[:HAS_FRAGMENT_DESCRIPTOR]->(:Xml:Document) "
                + "RETURN f.bundleSymbolicName AS v", "v"))
                .containsExactly(WIN32);
        assertThat(column("MATCH (f:Osgi:Fragment) WHERE f.hasFragmentDescriptor IS NULL "
                + "RETURN f.bundleSymbolicName AS v", "v"))
                .containsExactly(NL);
    }

    // ---------------------------------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------------------------------

    private Set<String> constraintColumn(String constraintId, String column) throws RuleException {
        Result<Constraint> result = validateConstraint(constraintId);
        return result.getRows().stream()
                .map(row -> String.valueOf(row.getColumns().get(column).getValue()))
                .collect(Collectors.toCollection(java.util.TreeSet::new));
    }

    private List<Map<String, Object>> rows(String cypher) {
        store.beginTransaction();
        try {
            return query(cypher).getRows();
        } finally {
            store.commitTransaction();
        }
    }

    private Set<String> column(String cypher, String column) {
        return rows(cypher).stream()
                .map(r -> String.valueOf(r.get(column)))
                .collect(Collectors.toCollection(java.util.TreeSet::new));
    }

    private Object single(String cypher) {
        List<Map<String, Object>> rows = rows(cypher);
        return rows.get(0).values().iterator().next();
    }

    /** In-memory description of a bundle jar: OSGi manifest headers, Java sources, and resources. */
    private static final class Bundle {
        private final String symbolicName;
        private final String fragmentHost;
        private final Map<String, String> sources = new LinkedHashMap<>();
        private final Map<String, String> resources = new LinkedHashMap<>();

        Bundle(String symbolicName, String fragmentHost) {
            this.symbolicName = symbolicName;
            this.fragmentHost = fragmentHost;
        }

        /** body is the class declaration; the package is derived from the fully qualified name. */
        void addClass(String fqn, String body) {
            int lastDot = fqn.lastIndexOf('.');
            String pkg = fqn.substring(0, lastDot);
            sources.put(fqn, "package " + pkg + ";\n" + body + "\n");
        }

        File build(Path work) throws Exception {
            Path srcDir = work.resolve(symbolicName + "-src");
            Path classesDir = work.resolve(symbolicName + "-classes");
            Files.createDirectories(classesDir);

            List<String> sourceFiles = new ArrayList<>();
            for (Map.Entry<String, String> e : sources.entrySet()) {
                Path f = srcDir.resolve(e.getKey().replace('.', '/') + ".java");
                Files.createDirectories(f.getParent());
                Files.writeString(f, e.getValue());
                sourceFiles.add(f.toString());
            }
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            List<String> args = new ArrayList<>(List.of("-d", classesDir.toString()));
            args.addAll(sourceFiles);
            int rc = compiler.run(null, null, null, args.toArray(new String[0]));
            assertThat(rc).as("javac exit code for %s", symbolicName).isZero();

            Manifest manifest = new Manifest();
            Attributes main = manifest.getMainAttributes();
            main.put(Attributes.Name.MANIFEST_VERSION, "1.0");
            main.putValue("Bundle-ManifestVersion", "2");
            main.putValue("Bundle-SymbolicName", symbolicName);
            main.putValue("Bundle-Version", "1.0.0");
            if (fragmentHost != null) {
                main.putValue("Fragment-Host", fragmentHost);
            }

            File jar = work.resolve(symbolicName + ".jar").toFile();
            try (JarOutputStream jos = new JarOutputStream(
                    new BufferedOutputStream(new FileOutputStream(jar)), manifest)) {
                List<Path> classFiles = Files.walk(classesDir).filter(Files::isRegularFile)
                        .collect(Collectors.toList());
                for (Path p : classFiles) {
                    String name = classesDir.relativize(p).toString().replace(File.separatorChar, '/');
                    writeEntry(jos, name, Files.readAllBytes(p));
                }
                for (Map.Entry<String, String> e : resources.entrySet()) {
                    writeEntry(jos, e.getKey(), e.getValue().getBytes(UTF_8));
                }
            }
            return jar;
        }

        private static void writeEntry(JarOutputStream jos, String name, byte[] content) throws Exception {
            jos.putNextEntry(new JarEntry(name));
            jos.write(content);
            jos.closeEntry();
        }
    }

    private static final String HOST_PLUGIN_XML =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<?eclipse version=\"3.4\"?>\n"
            + "<plugin>\n"
            + "   <extension point=\"org.eclipse.core.expressions.propertyTesters\">\n"
            + "      <propertyTester id=\"t1\" type=\"com.example.vm.OrderViewModel\" namespace=\"com.example.host\" properties=\"isOpen\" class=\"com.example.tester.HostTester\"/>\n"
            + "      <propertyTester id=\"t2\" type=\"com.example.vm.OrderViewModel\" namespace=\"com.example.host2\" properties=\"isWin\" class=\"com.example.tester.FragmentOnlyTester\"/>\n"
            + "      <propertyTester id=\"t4\" type=\"com.example.vm.OrderViewModel\" namespace=\"com.example.host4\" properties=\"isGone\" class=\"com.example.tester.MissingTester\"/>\n"
            + "      <propertyTester id=\"t5\" type=\"com.example.vm.MissingViewModel\" namespace=\"com.example.host5\" properties=\"isX\" class=\"com.example.tester.HostTester\"/>\n"
            + "   </extension>\n"
            + "   <extension point=\"org.eclipse.ui.commands\">\n"
            + "      <command id=\"com.example.command.doThing\" name=\"Do Thing\"/>\n"
            + "   </extension>\n"
            + "   <extension point=\"org.eclipse.ui.views\">\n"
            + "      <view id=\"v1\" class=\"com.example.view.OrderView\"/>\n"
            + "      <view id=\"v2\" class=\"com.example.view.MissingView\"/>\n"
            + "      <view id=\"v3\" class=\"com.example.view.FragmentOnlyView\"/>\n"
            + "   </extension>\n"
            + "   <extension point=\"org.eclipse.ui.handlers\">\n"
            + "      <handler commandId=\"com.example.command.doThing\" class=\"com.example.handler.DoThingHandler\">\n"
            + "         <enablement>\n"
            + "            <with variable=\"selection\">\n"
            + "               <iterate>\n"
            + "                  <test property=\"com.example.host.isOpen\"/>\n"
            + "                  <test property=\"com.example.host.isOpne\"/>\n"
            + "                  <test property=\"org.eclipse.core.resources.name\"/>\n"
            + "               </iterate>\n"
            + "            </with>\n"
            + "         </enablement>\n"
            + "      </handler>\n"
            + "      <handler commandId=\"com.example.command.doesNotExist\" class=\"com.example.handler.DoThingHandler\"/>\n"
            + "   </extension>\n"
            + "</plugin>\n";

    private static final String WIN32_FRAGMENT_XML =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<?eclipse version=\"3.4\"?>\n"
            + "<fragment>\n"
            + "   <extension point=\"org.eclipse.core.expressions.propertyTesters\">\n"
            + "      <propertyTester id=\"t3\" type=\"com.example.vm.OrderViewModel\" namespace=\"com.example.win\" properties=\"isWin32\" class=\"com.example.tester.WinTester\"/>\n"
            + "   </extension>\n"
            + "</fragment>\n";
}
