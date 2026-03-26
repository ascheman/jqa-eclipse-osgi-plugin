package net.aschemann.jqassistant.plugins.eclipse.osgi.impl;

import java.util.List;

import net.aschemann.jqassistant.plugins.eclipse.osgi.impl.OsgiManifestHeaderParser.HeaderEntry;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OsgiManifestHeaderParserTest {

    // --- splitHeaderValue ---

    @Test
    void splitSimplePackageList() {
        List<String> entries = OsgiManifestHeaderParser.splitHeaderValue(
                "org.example.api,org.example.spi,org.example.util");
        assertThat(entries).containsExactly("org.example.api", "org.example.spi", "org.example.util");
    }

    @Test
    void splitPreservesQuotedCommas() {
        List<String> entries = OsgiManifestHeaderParser.splitHeaderValue(
                "org.example.api;version=\"[1.0.0,2.0.0)\",org.example.spi");
        assertThat(entries).containsExactly(
                "org.example.api;version=\"[1.0.0,2.0.0)\"",
                "org.example.spi");
    }

    @Test
    void splitMultipleQuotedRanges() {
        List<String> entries = OsgiManifestHeaderParser.splitHeaderValue(
                "org.a;version=\"[1.0,2.0)\",org.b;version=\"[3.0,4.0)\",org.c");
        assertThat(entries).containsExactly(
                "org.a;version=\"[1.0,2.0)\"",
                "org.b;version=\"[3.0,4.0)\"",
                "org.c");
    }

    @Test
    void splitHandlesWhitespace() {
        List<String> entries = OsgiManifestHeaderParser.splitHeaderValue(
                " org.a , org.b , org.c ");
        assertThat(entries).containsExactly("org.a", "org.b", "org.c");
    }

    @Test
    void splitNullReturnsEmpty() {
        assertThat(OsgiManifestHeaderParser.splitHeaderValue(null)).isEmpty();
    }

    @Test
    void splitEmptyReturnsEmpty() {
        assertThat(OsgiManifestHeaderParser.splitHeaderValue("")).isEmpty();
        assertThat(OsgiManifestHeaderParser.splitHeaderValue("   ")).isEmpty();
    }

    @Test
    void splitSingleEntry() {
        List<String> entries = OsgiManifestHeaderParser.splitHeaderValue("org.example.api");
        assertThat(entries).containsExactly("org.example.api");
    }

    // --- parseEntry ---

    @Test
    void parseSimplePackageName() {
        HeaderEntry entry = OsgiManifestHeaderParser.parseEntry("org.example.api");
        assertThat(entry.getName()).isEqualTo("org.example.api");
        assertThat(entry.getAttributes()).isEmpty();
    }

    @Test
    void parsePackageWithVersion() {
        HeaderEntry entry = OsgiManifestHeaderParser.parseEntry(
                "org.example.api;version=\"1.0.0\"");
        assertThat(entry.getName()).isEqualTo("org.example.api");
        assertThat(entry.getAttribute("version")).isEqualTo("1.0.0");
    }

    @Test
    void parsePackageWithVersionRange() {
        HeaderEntry entry = OsgiManifestHeaderParser.parseEntry(
                "org.example.api;version=\"[1.0.0,2.0.0)\"");
        assertThat(entry.getName()).isEqualTo("org.example.api");
        assertThat(entry.getAttribute("version")).isEqualTo("[1.0.0,2.0.0)");
    }

    @Test
    void parseDirectiveSyntax() {
        HeaderEntry entry = OsgiManifestHeaderParser.parseEntry(
                "org.example.api;resolution:=optional");
        assertThat(entry.getName()).isEqualTo("org.example.api");
        assertThat(entry.getAttribute("resolution")).isEqualTo("optional");
    }

    @Test
    void parseMultipleAttributes() {
        HeaderEntry entry = OsgiManifestHeaderParser.parseEntry(
                "org.example.api;version=\"1.0.0\";uses:=\"org.example.spi,org.example.util\"");
        assertThat(entry.getName()).isEqualTo("org.example.api");
        assertThat(entry.getAttribute("version")).isEqualTo("1.0.0");
        assertThat(entry.getAttribute("uses")).isEqualTo("org.example.spi,org.example.util");
    }

    @Test
    void parseBundleVersionDirective() {
        HeaderEntry entry = OsgiManifestHeaderParser.parseEntry(
                "org.eclipse.core.runtime;bundle-version=\"[3.7.0,4.0.0)\"");
        assertThat(entry.getName()).isEqualTo("org.eclipse.core.runtime");
        assertThat(entry.getAttribute("bundle-version")).isEqualTo("[3.7.0,4.0.0)");
    }

    @Test
    void parseSingletonDirective() {
        HeaderEntry entry = OsgiManifestHeaderParser.parseEntry(
                "com.example.bundle;singleton:=true");
        assertThat(entry.getName()).isEqualTo("com.example.bundle");
        assertThat(entry.getAttribute("singleton")).isEqualTo("true");
    }

    // --- parseHeader (end-to-end) ---

    @Test
    void parseCompleteImportPackageHeader() {
        String header = "org.eclipse.swt;version=\"[3.0.0,4.0.0)\","
                + "org.eclipse.jface;resolution:=optional,"
                + "javax.servlet;version=\"2.5.0\"";
        List<HeaderEntry> entries = OsgiManifestHeaderParser.parseHeader(header);

        assertThat(entries).hasSize(3);

        assertThat(entries.get(0).getName()).isEqualTo("org.eclipse.swt");
        assertThat(entries.get(0).getAttribute("version")).isEqualTo("[3.0.0,4.0.0)");

        assertThat(entries.get(1).getName()).isEqualTo("org.eclipse.jface");
        assertThat(entries.get(1).getAttribute("resolution")).isEqualTo("optional");

        assertThat(entries.get(2).getName()).isEqualTo("javax.servlet");
        assertThat(entries.get(2).getAttribute("version")).isEqualTo("2.5.0");
    }

    @Test
    void parseCompleteRequireBundleHeader() {
        String header = "org.eclipse.core.runtime;bundle-version=\"[3.7.0,4.0.0)\","
                + "org.eclipse.ui;bundle-version=\"3.5.0\","
                + "com.example.other";
        List<HeaderEntry> entries = OsgiManifestHeaderParser.parseHeader(header);

        assertThat(entries).hasSize(3);

        assertThat(entries.get(0).getName()).isEqualTo("org.eclipse.core.runtime");
        assertThat(entries.get(0).getAttribute("bundle-version")).isEqualTo("[3.7.0,4.0.0)");

        assertThat(entries.get(1).getName()).isEqualTo("org.eclipse.ui");
        assertThat(entries.get(1).getAttribute("bundle-version")).isEqualTo("3.5.0");

        assertThat(entries.get(2).getName()).isEqualTo("com.example.other");
        assertThat(entries.get(2).hasAttribute("bundle-version")).isFalse();
    }

    @Test
    void parseExportPackageWithUses() {
        String header = "org.example.api;version=\"1.2.3\";uses:=\"org.example.spi,org.example.model\"";
        List<HeaderEntry> entries = OsgiManifestHeaderParser.parseHeader(header);

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getName()).isEqualTo("org.example.api");
        assertThat(entries.get(0).getAttribute("version")).isEqualTo("1.2.3");
        assertThat(entries.get(0).getAttribute("uses")).isEqualTo("org.example.spi,org.example.model");
    }

    @Test
    void parseRealWorldEclipseHeader() {
        // Realistic Eclipse Import-Package with mixed attributes
        String header = "org.eclipse.core.commands;version=\"[3.2.0,4.0.0)\","
                + "org.eclipse.core.runtime;version=\"[3.4.0,4.0.0)\","
                + "org.eclipse.jface.action,"
                + "org.osgi.framework;version=\"[1.3.0,2.0.0)\"";
        List<HeaderEntry> entries = OsgiManifestHeaderParser.parseHeader(header);

        assertThat(entries).hasSize(4);
        assertThat(entries.get(0).getAttribute("version")).isEqualTo("[3.2.0,4.0.0)");
        assertThat(entries.get(2).getName()).isEqualTo("org.eclipse.jface.action");
        assertThat(entries.get(2).hasAttribute("version")).isFalse();
        assertThat(entries.get(3).getAttribute("version")).isEqualTo("[1.3.0,2.0.0)");
    }
}
