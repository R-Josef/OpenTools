package io.opentools.api.tool;

/**
 * A headless tool exposed by OpenTools or a plugin.
 */
public interface OpenTool {
    /**
     * Returns tool metadata used by discovery, validation, and documentation.
     *
     * @return immutable tool descriptor
     */
    ToolDescriptor descriptor();

    /**
     * Validates tool parameters without executing the tool.
     *
     * @param request tool parameters
     * @return validation issues
     */
    ValidationResult validate(ToolRequest request);

    /**
     * Executes the tool.
     *
     * @param request validated tool parameters
     * @param context run-scoped paths, logging, progress, and cancellation
     * @return result summary, table, metrics, and output artifacts
     * @throws Exception when tool execution fails
     */
    ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception;
}
