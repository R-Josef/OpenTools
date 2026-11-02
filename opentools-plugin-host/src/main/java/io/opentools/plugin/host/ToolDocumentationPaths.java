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
     * Returns a host documentation path relative to one language root.
     *
     * @param toolId tool identifier
     * @return path such as {@code tool/my.tool.md}
     */
    public static Optional<String> hostRelativePath(String toolId) {
        return toolFileName(toolId).map(file -> "tool/" + file);
    }

    /**
     * Returns a plugin documentation path relative to one language directory.
     *
     * @param toolId tool identifier
     * @return path such as {@code tool/my.tool.md}
     */
    public static Optional<String> pluginRelativePath(String toolId) {
        return hostRelativePath(toolId);
    }

    /**
     * Returns the complete JAR entry path for a plugin tool document.
     *
     * @param languageCode language directory
     * @param toolId tool identifier
     * @return JAR entry path, or empty for an unsupported Tool ID
     */
    public static Optional<String> pluginEntry(
            String languageCode,
            String toolId
    ) {
        return toolFileName(toolId).map(file ->
                PLUGIN_PREFIX + normalizeLanguage(languageCode) + "/"
                        + "tool/" + file
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
