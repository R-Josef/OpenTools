package io.opentools.plugin;

/**
 * Plugin-scoped access to the language resources bundled in the plugin JAR.
 *
 * <p>Implementations resolve keys only within one plugin. They follow the
 * active language, its language-only form, {@code en_US}, and finally the
 * caller-provided fallback text.</p>
 */
public interface PluginLocalizer {
    /**
     * Returns the active interface language code.
     *
     * @return language code such as {@code en_US} or {@code zh_CN}
     */
    String languageCode();

    /**
     * Resolves a plugin-localized message.
     *
     * @param key resource key
     * @param fallback text used when no resource contains the key
     * @param arguments optional {@link java.text.MessageFormat} arguments
     * @return localized text
     */
    String text(String key, String fallback, Object... arguments);
}
