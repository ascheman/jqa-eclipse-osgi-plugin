package net.aschemann.jqassistant.plugins.eclipse.osgi.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses OSGi manifest header values with proper handling of quoted strings
 * containing commas (e.g., version ranges like {@code version="[1.0.0,2.0.0)"}).
 */
public final class OsgiManifestHeaderParser {

    private OsgiManifestHeaderParser() {
    }

    /**
     * Represents a single parsed entry from an OSGi manifest header.
     * For example, from {@code org.example.api;version="1.0.0";uses:="org.example.spi"},
     * the name would be {@code org.example.api} and the attributes/directives map
     * would contain {@code version=1.0.0} and {@code uses=org.example.spi}.
     */
    public static class HeaderEntry {
        private final String name;
        private final Map<String, String> attributes;
        private final String rawValue;

        public HeaderEntry(String name, Map<String, String> attributes, String rawValue) {
            this.name = name;
            this.attributes = Collections.unmodifiableMap(attributes);
            this.rawValue = rawValue;
        }

        public String getName() {
            return name;
        }

        public Map<String, String> getAttributes() {
            return attributes;
        }

        public String getRawValue() {
            return rawValue;
        }

        public String getAttribute(String key) {
            return attributes.get(key);
        }

        public boolean hasAttribute(String key) {
            return attributes.containsKey(key);
        }
    }

    /**
     * Splits a manifest header value into individual entries, respecting quoted strings.
     * Commas inside double quotes are not treated as separators.
     *
     * @param headerValue the raw manifest header value
     * @return list of trimmed individual entry strings
     */
    public static List<String> splitHeaderValue(String headerValue) {
        if (headerValue == null || headerValue.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<String> entries = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < headerValue.length(); i++) {
            char c = headerValue.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
                current.append(c);
            } else if (c == ',' && !inQuotes) {
                String entry = current.toString().trim();
                if (!entry.isEmpty()) {
                    entries.add(entry);
                }
                current.setLength(0);
            } else {
                current.append(c);
            }
        }

        String last = current.toString().trim();
        if (!last.isEmpty()) {
            entries.add(last);
        }

        return entries;
    }

    /**
     * Parses a single header entry into its name and attributes/directives.
     * <p>
     * For example: {@code org.example.api;version="[1.0.0,2.0.0)";uses:="org.example.spi"}
     * produces name={@code org.example.api}, attributes={version=[1.0.0,2.0.0), uses=org.example.spi}.
     * <p>
     * Both {@code key=value} (attributes) and {@code key:=value} (directives) are stored
     * in the same map with the key name (without {@code :=}).
     *
     * @param entry a single entry string (already split from the header)
     * @return the parsed HeaderEntry
     */
    public static HeaderEntry parseEntry(String entry) {
        List<String> parts = splitOnSemicolon(entry);
        String name = parts.get(0).trim();
        Map<String, String> attributes = new LinkedHashMap<>();

        for (int i = 1; i < parts.size(); i++) {
            String part = parts.get(i).trim();
            if (part.isEmpty()) {
                continue;
            }

            int eqIdx = part.indexOf('=');
            if (eqIdx > 0) {
                String key = part.substring(0, eqIdx);
                String value = part.substring(eqIdx + 1);

                // Handle directive syntax (key:=value)
                if (key.endsWith(":")) {
                    key = key.substring(0, key.length() - 1);
                }

                // Strip surrounding quotes from value
                value = stripQuotes(value.trim());
                attributes.put(key.trim(), value);
            }
        }

        return new HeaderEntry(name, attributes, entry);
    }

    /**
     * Convenience method: splits the header value and parses each entry.
     *
     * @param headerValue the raw manifest header value
     * @return list of parsed HeaderEntry objects
     */
    public static List<HeaderEntry> parseHeader(String headerValue) {
        List<String> rawEntries = splitHeaderValue(headerValue);
        List<HeaderEntry> entries = new ArrayList<>(rawEntries.size());
        for (String raw : rawEntries) {
            entries.add(parseEntry(raw));
        }
        return entries;
    }

    /**
     * Splits on semicolons that are not inside quoted strings.
     */
    private static List<String> splitOnSemicolon(String value) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
                current.append(c);
            } else if (c == ';' && !inQuotes) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }

        parts.add(current.toString());
        return parts;
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
