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
     * Submits completed tool data through the normal job pipeline.
     *
     * @param request completed tool request
     * @return submitted run identifier
     * @throws Exception if validation or submission fails
     */
    String submit(ToolRequest request) throws Exception;
}
