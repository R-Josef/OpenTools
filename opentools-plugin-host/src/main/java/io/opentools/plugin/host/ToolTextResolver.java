package io.opentools.plugin.host;

import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolParameter;
import io.opentools.core.i18n.LanguageManager;
import io.opentools.plugin.PluginLocalizer;

import java.text.MessageFormat;
import java.util.Locale;

/**
 * Resolves tool-owned text through the exact plugin binding for the tool.
 *
 * <p>Built-in tools continue to use {@link LanguageManager}; plugin tools use
 * only their own JAR language resources.</p>
 */
public final class ToolTextResolver {
    private final PluginManager plugins;
    private final LanguageManager language;

    public ToolTextResolver(PluginManager plugins, LanguageManager language) {
        this.plugins = plugins;
        this.language = language;
    }

    /**
     * Creates a resolver for hosts without plugins.
     *
     * @param language host language manager
     * @return host-only resolver
     */
    public static ToolTextResolver hostOnly(LanguageManager language) {
        return new ToolTextResolver(null, language);
    }

    /**
     * Resolves a tool name.
     *
     * @param tool tool descriptor
     * @return localized name
     */
    public String name(ToolDescriptor tool) {
        return text(tool, "tool." + tool.id() + ".name", tool.name());
    }

    /**
     * Resolves a tool description.
     *
     * @param tool tool descriptor
     * @return localized description
     */
    public String description(ToolDescriptor tool) {
        return text(tool, "tool." + tool.id() + ".description", tool.description());
    }

    /**
     * Resolves a parameter title.
     *
     * @param tool owning tool
     * @param parameter parameter descriptor
     * @return localized title
     */
    public String parameterTitle(ToolDescriptor tool, ToolParameter parameter) {
        return text(
                tool,
                "tool." + tool.id() + ".parameter." + parameter.id() + ".title",
                parameter.title()
        );
    }

    /**
     * Resolves a parameter description.
     *
     * @param tool owning tool
     * @param parameter parameter descriptor
     * @return localized description
     */
    public String parameterDescription(ToolDescriptor tool, ToolParameter parameter) {
        return text(
                tool,
                "tool." + tool.id() + ".parameter." + parameter.id() + ".description",
                parameter.description()
        );
    }

    /**
     * Builds searchable text from what the active language displays.
     *
     * <p>Includes the localized tool name, description, category, parameter
     * titles/descriptions, formats, stable ID, and the optional
     * `tool.<id>.search` language value. Descriptor aliases are deliberately
     * not included, so the UI does not match a hidden English alias while
     * showing a localized title.</p>
     *
     * @param tool tool descriptor
     * @return lowercase search text
     */
    public String searchText(ToolDescriptor tool) {
        StringBuilder search = new StringBuilder(256);
        append(search, name(tool));
        append(search, description(tool));
        append(search, category(tool.category()));
        append(search, tool.id());
        append(search, text(tool, "tool." + tool.id() + ".search", ""));
        append(search, String.join(" ", tool.inputFormats()));
        append(search, String.join(" ", tool.outputFormats()));
        for (ToolParameter parameter : tool.parameters()) {
            append(search, parameterTitle(tool, parameter));
            append(search, parameterDescription(tool, parameter));
        }
        return search.toString().toLowerCase(Locale.ROOT);
    }

    private static void append(StringBuilder target, String value) {
        if (value != null && !value.isBlank()) {
            target.append(' ').append(value);
        }
    }

    /**
     * Resolves text using the exact registered owner of a tool ID.
     *
     * @param toolId stable tool identifier
     * @param key localization key
     * @param fallback fallback text
     * @param arguments optional message arguments
     * @return localized text
     */
    public String text(String toolId, String key, String fallback, Object... arguments) {
        PluginLocalizer localizer = plugins == null ? null : plugins.localizerForTool(toolId);
        return localizer == null
                ? hostText(key, fallback, arguments)
                : localizer.text(key, fallback, arguments);
    }

    /**
     * Resolves text using the owning plugin of a descriptor.
     *
     * @param tool tool descriptor
     * @param key localization key
     * @param fallback fallback text
     * @param arguments optional message arguments
     * @return localized text
     */
    public String text(ToolDescriptor tool, String key, String fallback, Object... arguments) {
        return text(tool.id(), key, fallback, arguments);
    }

    /**
     * Resolves a host-owned category label.
     *
     * @param category descriptor category
     * @return localized host category
     */
    public String category(String category) {
        return hostText("category." + category.toLowerCase(Locale.ROOT), category);
    }

    /**
     * Returns the active language code.
     *
     * @return language code
     */
    public String languageCode() {
        return language == null ? "en_US" : language.activeCode();
    }

    /**
     * Returns the host language manager for generic application UI.
     *
     * @return language manager
     */
    public LanguageManager language() {
        return language;
    }

    private String hostText(String key, String fallback, Object... arguments) {
        if (language != null) {
            return language.text(key, fallback, arguments);
        }
        String pattern = fallback == null ? key : fallback;
        return arguments == null || arguments.length == 0
                ? pattern
                : MessageFormat.format(pattern, arguments);
    }
}
