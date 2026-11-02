package io.opentools.api.tool;

/**
 * An {@link OpenTool} with a host-integrated interactive Swing page.
 *
 * <p>The custom page replaces the generated parameter form. Completed data must
 * still be submitted as a standard {@link ToolRequest}, keeping validation,
 * jobs, results, CLI, and MCP on the same execution contract.</p>
 */
public interface InteractiveOpenTool extends OpenTool {
    /**
     * Creates a fresh page for one tool selection.
     *
     * @param context host services for interactive submission
     * @return interactive tool page
     * @throws Exception if the page cannot be created
     */
    ToolPanel createPanel(ToolUiContext context) throws Exception;
}
