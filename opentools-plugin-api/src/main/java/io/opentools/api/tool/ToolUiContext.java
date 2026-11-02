package io.opentools.api.tool;

/**
 * Host services available to an interactive tool panel.
 */
public interface ToolUiContext {
    /**
     * Returns the tool currently displayed by the panel.
     *
     * @return tool descriptor
     */
    ToolDescriptor tool();

    /**
     * Returns the active interface language code.
     *
     * @return language code such as {@code en_US} or {@code zh_CN}
     */
    String languageCode();

    /**
     * Resolves a message using the owning plugin's bundled language resources.
     *
     * @param key resource key
     * @param fallback text used when the resource is missing
     * @param arguments optional {@link java.text.MessageFormat} arguments
     * @return localized text
     */
    String text(String key, String fallback, Object... arguments);

    /**
     * Submits completed tool data through the normal job pipeline.
     *
     * @param request completed tool request
     * @return submitted run identifier
     * @throws Exception if validation or submission fails
     */
    String submit(ToolRequest request) throws Exception;
}
