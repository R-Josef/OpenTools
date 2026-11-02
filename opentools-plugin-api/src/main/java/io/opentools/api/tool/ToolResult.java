package io.opentools.api.tool;

import java.util.List;
import java.util.Map;

/**
 * Immutable result returned by a completed tool.
 */
public final class ToolResult {
    private final String summary;
    private final List<Object> summaryArguments;
    private final String detail;
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
    public ToolResult(
            String summary,
            String detail,
            ResultTable table,
            List<OutputArtifact> artifacts,
            Map<String, Object> metrics
    ) {
        this(summary, List.of(), detail, List.of(), table, artifacts, metrics);
    }

    /**
     * Creates a result with message arguments. OpenTools derives localization
     * keys from the owning Tool ID.
     *
     * @param summary fallback summary
     * @param summaryArguments summary message arguments
     * @param detail fallback detail
     * @param detailArguments detail message arguments
     * @param table tabular preview
     * @param artifacts produced files
     * @param metrics structured result values
     */
    public ToolResult(
            String summary,
            List<Object> summaryArguments,
            String detail,
            List<Object> detailArguments,
            ResultTable table,
            List<OutputArtifact> artifacts,
            Map<String, Object> metrics
    ) {
        this.summary = summary == null ? "" : summary;
        this.summaryArguments = summaryArguments == null ? List.of() : List.copyOf(summaryArguments);
        this.detail = detail == null ? "" : detail;
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
     * @return tool result
     */
    public static ToolResult success(
            String summary,
            String detail,
            ResultTable table,
            List<OutputArtifact> artifacts
    ) {
        return new ToolResult(summary, detail, table, artifacts, Map.of());
    }
}
