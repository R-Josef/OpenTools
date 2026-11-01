package io.opentools.api.tool;

/**
 * A headless task exposed by OpenTools or a plugin.
 */
public interface OpenTool {
    /**
     * Returns task metadata used by discovery, validation, and documentation.
     *
     * @return immutable task descriptor
     */
    ToolDescriptor descriptor();

    /**
     * Validates task parameters without executing the task.
     *
     * @param request task parameters
     * @return validation issues
     */
    ValidationResult validate(ToolRequest request);

    /**
     * Executes the task.
     *
     * @param request validated task parameters
     * @param context run-scoped paths, logging, progress, and cancellation
     * @return result summary, table, metrics, and output artifacts
     * @throws Exception when task execution fails
     */
    TaskResult execute(ToolRequest request, TaskContext context) throws Exception;
}
