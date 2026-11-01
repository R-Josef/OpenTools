package io.opentools.api.tool;

import java.util.List;
import java.util.Map;

/**
 * Immutable result returned by a completed task.
 */
public final class TaskResult {
    private final String summary;
    private final String summaryKey;
    private final List<Object> summaryArguments;
    private final String detail;
    private final String detailKey;
    private final List<Object> detailArguments;
    private final ResultTable table;
    private final List<OutputArtifact> artifacts;
    private final Map<String, Object> metrics;

    /**
     * Creates a result with plain summary and detail text.
     *
     * @param summary short result summary
     * @param detail detailed result text
     * @param table tabular preview
     * @param artifacts produced files
     * @param metrics structured result values
     */
    public TaskResult(
            String summary,
            String detail,
            ResultTable table,
            List<OutputArtifact> artifacts,
            Map<String, Object> metrics
    ) {
        this(summary, null, List.of(), detail, null, List.of(), table, artifacts, metrics);
    }

    /**
     * Creates a result with optional localization keys and arguments.
     *
     * @param summary fallback summary
     * @param summaryKey localization key for the summary
     * @param summaryArguments summary message arguments
     * @param detail fallback detail
     * @param detailKey localization key for the detail
     * @param detailArguments detail message arguments
     * @param table tabular preview
     * @param artifacts produced files
     * @param metrics structured result values
     */
    public TaskResult(
            String summary,
            String summaryKey,
            List<Object> summaryArguments,
            String detail,
            String detailKey,
            List<Object> detailArguments,
            ResultTable table,
            List<OutputArtifact> artifacts,
            Map<String, Object> metrics
    ) {
        this.summary = summary == null ? "" : summary;
        this.summaryKey = summaryKey;
        this.summaryArguments = summaryArguments == null ? List.of() : List.copyOf(summaryArguments);
        this.detail = detail == null ? "" : detail;
        this.detailKey = detailKey;
        this.detailArguments = detailArguments == null ? List.of() : List.copyOf(detailArguments);
        this.table = table == null ? ResultTable.empty() : table;
        this.artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
        this.metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
    }

    /**
     * Returns the fallback summary.
     *
     * @return summary text
     */
    public String summary() {
        return summary;
    }

    /**
     * Returns the summary localization key.
     *
     * @return key or {@code null}
     */
    public String summaryKey() {
        return summaryKey;
    }

    /**
     * Returns summary message arguments.
     *
     * @return immutable argument list
     */
    public List<Object> summaryArguments() {
        return summaryArguments;
    }

    /**
     * Returns the fallback detail.
     *
     * @return detail text
     */
    public String detail() {
        return detail;
    }

    /**
     * Returns the detail localization key.
     *
     * @return key or {@code null}
     */
    public String detailKey() {
        return detailKey;
    }

    /**
     * Returns detail message arguments.
     *
     * @return immutable argument list
     */
    public List<Object> detailArguments() {
        return detailArguments;
    }

    /**
     * Returns the tabular preview.
     *
     * @return result table
     */
    public ResultTable table() {
        return table;
    }

    /**
     * Returns produced files.
     *
     * @return immutable artifact list
     */
    public List<OutputArtifact> artifacts() {
        return artifacts;
    }

    /**
     * Returns structured result metrics.
     *
     * @return immutable metric map
     */
    public Map<String, Object> metrics() {
        return metrics;
    }

    /**
     * Creates a successful result without localization metadata or metrics.
     *
     * @param summary short result summary
     * @param detail detailed result text
     * @param table tabular preview
     * @param artifacts produced files
     * @return task result
     */
    public static TaskResult success(
            String summary,
            String detail,
            ResultTable table,
            List<OutputArtifact> artifacts
    ) {
        return new TaskResult(summary, detail, table, artifacts, Map.of());
    }
}
