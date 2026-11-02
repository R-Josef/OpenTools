package io.opentools.plugin.host;

import java.util.Locale;
import java.util.Optional;

/**
 * Resource-path conventions for optional Markdown tool documentation.
 */
public final class ToolDocumentationPaths {
    public static final String PLUGIN_PREFIX = "META-INF/opentools/docs/";

    private ToolDocumentationPaths() {
    }

    /**
     * Returns the category directory used below {@code categories/}.
     *
     * @param category descriptor category
     * @return lowercase hyphenated directory name
     */
    public static String categoryDirectory(String category) {
        String normalized = category == null ? "" : category.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return normalized.isBlank() ? "uncategorized" : normalized;
    }

    /**
     * Returns a host documentation path relative to one language root.
     *
     * @param category descriptor category
     * @param toolId tool identifier
     * @return path such as {@code categories/plugin/my.tool.md}
     */
    public static Optional<String> hostRelativePath(String category, String toolId) {
        return toolFileName(toolId)
                .map(file -> "categories/" + categoryDirectory(category) + "/" + file);
    }

    /**
     * Returns a plugin documentation path relative to one language directory.
     *
     * @param category descriptor category
     * @param toolId tool identifier
     * @return path such as {@code categories/plugin/my.tool.md}
     */
    public static Optional<String> pluginRelativePath(String category, String toolId) {
        return hostRelativePath(category, toolId);
    }

    /**
     * Returns the complete JAR entry path for a plugin tool document.
     *
     * @param languageCode language directory
     * @param category descriptor category
     * @param toolId tool identifier
     * @return JAR entry path, or empty for an unsupported Tool ID
     */
    public static Optional<String> pluginEntry(
            String languageCode,
            String category,
            String toolId
    ) {
        return toolFileName(toolId).map(file ->
                PLUGIN_PREFIX + normalizeLanguage(languageCode) + "/"
                        + "categories/" + categoryDirectory(category) + "/" + file
        );
    }

    static String normalizeLanguage(String languageCode) {
        String normalized = languageCode == null
                ? "en_US"
                : languageCode.trim().replace('-', '_');
        if (!normalized.matches("[A-Za-z]{2,8}(?:_[A-Za-z0-9]{2,8})?")) {
            return "en_US";
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private static Optional<String> toolFileName(String toolId) {
        if (toolId == null || !toolId.matches("[A-Za-z0-9._-]+")) {
            return Optional.empty();
        }
        return Optional.of(toolId + ".md");
    }
}
