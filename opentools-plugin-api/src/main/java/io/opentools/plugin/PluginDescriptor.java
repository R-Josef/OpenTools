package io.opentools.plugin;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Metadata read from {@code META-INF/opentools-plugin.json}.
 *
 * @param id unique plugin identifier
 * @param name English fallback display name
 * @param version plugin release version
 * @param apiVersion required Plugin API version
 * @param mainClass class implementing {@link OpenToolsPlugin}
 * @param description English fallback summary
 * @param nameKey optional localization key for the display name
 * @param descriptionKey optional localization key for the description
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
        String nameKey,
        String descriptionKey,
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
        nameKey = optional(nameKey);
        descriptionKey = optional(descriptionKey);
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

    private static String optional(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * Resolves the plugin display name for the active language.
     *
     * @param localizer plugin localizer, or {@code null} before resources load
     * @return localized display name or the manifest fallback
     */
    public String localizedName(PluginLocalizer localizer) {
        return localizer == null || nameKey.isBlank() ? name : localizer.text(nameKey, name);
    }

    /**
     * Resolves the plugin description for the active language.
     *
     * @param localizer plugin localizer, or {@code null} before resources load
     * @return localized description or the manifest fallback
     */
    public String localizedDescription(PluginLocalizer localizer) {
        return localizer == null || descriptionKey.isBlank()
                ? description
                : localizer.text(descriptionKey, description);
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
