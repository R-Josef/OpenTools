package io.opentools.plugin;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Metadata read from {@code META-INF/opentools-plugin.json}.
 *
 * @param id unique plugin identifier
 * @param name display name
 * @param version plugin release version
 * @param apiVersion required Plugin API version
 * @param mainClass class implementing {@link OpenToolsPlugin}
 * @param description optional summary
 * @param author optional author text
 * @param dependencies required plugin IDs
 * @param requestedCapabilities requested host capabilities
 */
public record PluginDescriptor(
        String id,
        String name,
        String version,
        String apiVersion,
        String mainClass,
        String description,
        String author,
        List<String> dependencies,
        List<String> requestedCapabilities
) {
    public PluginDescriptor {
        id = required(id, "id");
        name = required(name, "name");
        version = required(version, "version");
        apiVersion = required(apiVersion, "apiVersion");
        mainClass = required(mainClass, "mainClass");
        description = description == null ? "" : description;
        author = author == null ? "" : author;
        dependencies = normalize(dependencies);
        requestedCapabilities = requestedCapabilities == null || requestedCapabilities.isEmpty()
                ? List.of("tool", "page", "data")
                : normalizeLowercase(requestedCapabilities);
        if (!id.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("Plugin id contains unsupported characters: " + id);
        }
        if (dependencies.contains(id)) {
            throw new IllegalArgumentException("Plugin cannot depend on itself: " + id);
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Plugin descriptor field is required: " + field);
        }
        return value.trim();
    }

    private static List<String> normalize(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                unique.add(value.trim());
            }
        }
        return List.copyOf(unique);
    }

    private static List<String> normalizeLowercase(List<String> values) {
        if (values == null) {
            return List.of();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                unique.add(value.trim().toLowerCase(Locale.ROOT));
            }
        }
        return List.copyOf(unique);
    }
}
