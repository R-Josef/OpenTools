package io.opentools.visualization.tools;

import io.opentools.api.tool.AbstractOpenTool;
import io.opentools.api.tool.OpenToolException;
import io.opentools.api.tool.OutputArtifact;
import io.opentools.api.tool.ParameterType;
import io.opentools.api.tool.ResultTable;
import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolResult;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolParameter;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ValidationIssue;
import io.opentools.api.tool.ValidationResult;
import io.opentools.core.util.OrderedParallelProcessor;
import io.opentools.visualization.model.HeatmapData;
import io.opentools.visualization.model.PlotDiagnostic;
import io.opentools.visualization.model.PlotExport;
import io.opentools.visualization.model.PlotSpec;
import io.opentools.visualization.model.PlotStyle;
import io.opentools.visualization.model.PlotType;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class HeatmapTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "visual.heatmap",
            "Heatmap",
            "Build a reproducible heatmap from a labeled numeric matrix with diagnostics, ordering, and headless export.",
            "Visualization",
            List.of("heatmap", "matrix plot", "expression heatmap", "热图", "矩阵图"),
            List.of("TSV", "CSV", "TSV.gz", "CSV.gz"),
            List.of("SVG", "PNG", "PDF", "JSON"),
            "1.2.0",
            List.of(
                    ToolParameter.requiredFile("input", "Input matrix", "A labeled numeric matrix with the first row containing column names."),
                    enumParameter("delimiter", "Delimiter", "AUTO detects tab, comma, or semicolon.", "AUTO",
                            List.of("AUTO", "TAB", "COMMA", "SEMICOLON"), false),
                    enumParameter("missingPolicy", "Missing values", "Choose how empty cells are handled.", "ROW_MEAN",
                            List.of("ERROR", "ROW_MEAN", "ZERO", "GRAY"), false),
                    enumParameter("invalidPolicy", "Invalid values", "Choose whether non-numeric cells stop the task or become missing.", "AS_MISSING",
                            List.of("ERROR", "AS_MISSING"), false),
                    enumParameter("normalization", "Value normalization", "Optionally calculate a z-score within each row.", "NONE",
                            List.of("NONE", "ROW_ZSCORE"), false),
                    enumParameter("rowOrder", "Row order", "Use names, means, or deterministic average-linkage clustering.", "ORIGINAL",
                            List.of("ORIGINAL", "NAME_ASC", "NAME_DESC", "MEAN_ASC", "MEAN_DESC", "HIERARCHICAL"), false),
                    enumParameter("columnOrder", "Column order", "Use names, means, or deterministic average-linkage clustering.", "ORIGINAL",
                            List.of("ORIGINAL", "NAME_ASC", "NAME_DESC", "MEAN_ASC", "MEAN_DESC", "HIERARCHICAL"), false),
                    enumParameter("palette", "Color palette", "Select the heatmap color ramp.", "BLUE_WHITE_RED",
                            List.of("BLUE_WHITE_RED", "VIRIDIS", "SEQUENTIAL_BLUE"), false),
                    ToolParameter.bool("showValues", "Show cell values", "Draw values when cells are large enough.", false),
                    enumParameter("rangeMode", "Color range", "AUTO uses the displayed data range; FIXED uses the entered limits.", "AUTO",
                            List.of("AUTO", "FIXED"), false),
                    ToolParameter.decimal("rangeMin", "Fixed minimum", "Lower color-scale limit for FIXED range mode.", -1, -1.0e12, 1.0e12),
                    ToolParameter.decimal("rangeMax", "Fixed maximum", "Upper color-scale limit for FIXED range mode.", 1, -1.0e12, 1.0e12),
                    ToolParameter.string("title", "Plot title", "Optional title written into the plot model.", "OpenTools Heatmap"),
                    ToolParameter.string("subtitle", "Plot subtitle", "Optional subtitle written into the plot model.", ""),
                    integerParameter("maxRows", "Preview rows", "Maximum rows retained in the serialized plot model.", 200, 1, 5000, true),
                    integerParameter("maxColumns", "Preview columns", "Maximum columns retained in the serialized plot model.", 200, 1, 5000, true),
                    integerParameter("maxClusterItems", "Maximum cluster items",
                            "Safety bound for rows or columns participating in hierarchical clustering.",
                            1000, 2, 5000, true),
                    integerParameter("threads", "Threads",
                            "0 uses AUTO for large matrices; 1 disables parallel distance rows.",
                            0, 0, 64, true),
                    integerParameter("width", "Image width", "Rendered width in pixels.", 1200, 320, 10000, true),
                    integerParameter("height", "Image height", "Rendered height in pixels.", 800, 240, 10000, true),
                    integerParameter("dpi", "PDF DPI", "Resolution metadata and physical PDF page size.", 144, 72, 600, true),
                    enumParameter("exportFormats", "Export formats", "Choose the headless artifact formats.", "PNG_SVG_PDF",
                            List.of("PNG_SVG_PDF", "PNG_SVG", "PNG"), true),
                    ToolParameter.bool("exportData", "Export processed matrix", "Write the displayed, ordered matrix as TSV.", true),
                    ToolParameter.optionalOutputFile("output", "Output prefix", "Optional base path for plot and image outputs."),
                    ToolParameter.bool("overwrite", "Overwrite outputs", "Allow replacing existing custom output files.", false)
            )
    );

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ValidationResult validate(ToolRequest request) {
        ValidationResult result = validateParameters(request);
        if (request.string("rangeMode").equals("FIXED")
                && request.value("rangeMin") != null
                && request.value("rangeMax") != null
                && parseDouble(request, "rangeMin") >= parseDouble(request, "rangeMax")) {
            result = result.merge(ValidationResult.of(ValidationIssue.error(
                    "rangeMax",
                    "Fixed maximum must be greater than fixed minimum.",
                    "Use a larger maximum or switch the color range to AUTO."
            )));
        }
        Path requested = request.path("output");
        if (requested != null && !request.bool("overwrite")) {
            Path base = PlotArtifactSupport.stripKnownExtensions(requested);
            List<Path> expected = new ArrayList<>();
            expected.add(PlotArtifactSupport.plotDocumentPath(base));
            exportFormats(request).forEach(format -> expected.add(Path.of(base + "." + format.toLowerCase(Locale.ROOT))));
            if (request.bool("exportData")) {
                expected.add(Path.of(base + ".processed.tsv"));
            }
            if (PlotArtifactSupport.anyExists(expected)) {
                result = result.merge(ValidationResult.of(ValidationIssue.error(
                        "output",
                        "One or more plot outputs already exist.",
                        "Choose another output prefix or enable overwrite."
                )));
            }
        }
        return result;
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        MatrixParser.Config config = new MatrixParser.Config(
                request.path("input"),
                request.string("delimiter"),
                request.string("missingPolicy"),
                request.string("invalidPolicy"),
                request.string("rowOrder"),
                request.string("columnOrder"),
                request.string("normalization"),
                request.integer("maxRows"),
                request.integer("maxColumns"),
                request.integer("maxClusterItems")
        );
        int threads = OrderedParallelProcessor.resolveThreads(
                request.integer("threads"), Files.size(request.path("input")));
        context.progress(0.08, "Parsing heatmap matrix");
        MatrixParser.MatrixResult parsed = new MatrixParser().parse(
                config, context.cancellationToken(), threads);
        HeatmapData data = parsed.data();
        double minimum = request.string("rangeMode").equals("FIXED")
                ? parseDouble(request, "rangeMin")
                : parsed.minimum();
        double maximum = request.string("rangeMode").equals("FIXED")
                ? parseDouble(request, "rangeMax")
                : parsed.maximum();
        if (maximum <= minimum) {
            maximum = minimum + 1;
        }

        PlotStyle baseStyle = PlotStyle.heatmap(palette(request.string("palette")), request.bool("showValues"));
        PlotStyle style = new PlotStyle(
                baseStyle.backgroundColor(),
                baseStyle.foregroundColor(),
                baseStyle.mutedColor(),
                baseStyle.accentColor(),
                baseStyle.missingColor(),
                baseStyle.palette(),
                baseStyle.fontFamily(),
                baseStyle.baseFontSize(),
                baseStyle.lineWidth(),
                true,
                request.bool("showValues"),
                minimum,
                maximum,
                request.string("normalization"),
                1,
                1
        );
        Path base = PlotArtifactSupport.basePath(context, request.path("output"), "heatmap");
        PlotExport export = new PlotExport(
                exportFormats(request),
                base.getFileName().toString(),
                request.integer("width"),
                request.integer("height"),
                request.integer("dpi"),
                false,
                request.bool("overwrite")
        );
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("sourceRows", Integer.toString(data.sourceRows()));
        metadata.put("sourceColumns", Integer.toString(data.sourceColumns()));
        metadata.put("displayRows", Integer.toString(data.rowLabels().size()));
        metadata.put("displayColumns", Integer.toString(data.columnLabels().size()));
        metadata.put("missingPolicy", request.string("missingPolicy"));
        metadata.put("invalidPolicy", request.string("invalidPolicy"));
        metadata.put("delimiter", Character.toString(parsed.delimiter()));
        metadata.put("rowOrder", request.string("rowOrder"));
        metadata.put("columnOrder", request.string("columnOrder"));
        metadata.put("normalization", request.string("normalization"));
        if (request.string("rowOrder").equals("HIERARCHICAL")
                || request.string("columnOrder").equals("HIERARCHICAL")) {
            metadata.put("clusteringAlgorithm", HierarchicalClusterer.ALGORITHM);
            metadata.put("clusteringThreads", Integer.toString(threads));
        }
        PlotSpec spec = new PlotSpec(
                "1.0",
                "heatmap",
                PlotType.HEATMAP,
                request.string("title"),
                request.string("subtitle"),
                style,
                export,
                metadata,
                data
        );
        Path processed = Path.of(base + ".processed.tsv");
        if (request.bool("exportData")) {
            writeProcessed(processed, data);
        }
        context.progress(0.55, "Rendering heatmap");
        PlotArtifactSupport.RenderedPlot rendered = PlotArtifactSupport.render(
                descriptor().id(),
                spec,
                context,
                base,
                PlotArtifactSupport.lineage(descriptor().id(), spec, request.path("input"))
        );
        List<OutputArtifact> artifacts = new ArrayList<>(rendered.artifacts());
        if (request.bool("exportData")) {
            artifacts.add(new OutputArtifact(processed, "Processed heatmap matrix", "text/tab-separated-values"));
        }

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("sourceRows", data.sourceRows());
        metrics.put("sourceColumns", data.sourceColumns());
        metrics.put("displayRows", data.rowLabels().size());
        metrics.put("displayColumns", data.columnLabels().size());
        metrics.put("missingValues", data.missingValues());
        metrics.put("invalidValues", data.invalidValues());
        metrics.put("minimum", minimum);
        metrics.put("maximum", maximum);
        metrics.put("rowSampleStep", data.rowSampleStep());
        metrics.put("columnSampleStep", data.columnSampleStep());
        metrics.put("exportFormats", export.formats());
        metrics.put("rowOrder", request.string("rowOrder"));
        metrics.put("columnOrder", request.string("columnOrder"));
        metrics.put("threads", threads);
        metrics.put("parallelDistanceRows",
                threads > 1 && (request.string("rowOrder").equals("HIERARCHICAL")
                        || request.string("columnOrder").equals("HIERARCHICAL")));
        metrics.put("clusteringAlgorithm",
                request.string("rowOrder").equals("HIERARCHICAL")
                        || request.string("columnOrder").equals("HIERARCHICAL")
                        ? HierarchicalClusterer.ALGORITHM : "NONE");

        List<List<String>> diagnosticRows = new ArrayList<>();
        data.diagnostics().stream().limit(50).forEach(diagnostic -> diagnosticRows.add(List.of(
                diagnostic.severity(),
                Long.toString(diagnostic.line()),
                Integer.toString(diagnostic.row()),
                Integer.toString(diagnostic.column()),
                diagnostic.message()
        )));
        ResultTable table = new ResultTable(
                List.of("Severity", "Line", "Row", "Column", "Message"),
                diagnosticRows
        );
        context.progress(1.0, "Heatmap completed");
        return new ToolResult(
                "Rendered " + data.rowLabels().size() + " x " + data.columnLabels().size() + " heatmap",
                List.of(data.rowLabels().size(), data.columnLabels().size()),
                "Source matrix: " + data.sourceRows() + " x " + data.sourceColumns()
                        + "; missing: " + data.missingValues()
                        + "; invalid: " + data.invalidValues(),
                List.of(data.sourceRows(), data.sourceColumns(), data.missingValues(), data.invalidValues()),
                table,
                artifacts,
                metrics
        );
    }

    private static List<String> exportFormats(ToolRequest request) {
        return switch (request.string("exportFormats")) {
            case "PNG_SVG" -> List.of("PNG", "SVG");
            case "PNG" -> List.of("PNG");
            default -> List.of("PNG", "SVG", "PDF");
        };
    }

    private static List<String> palette(String name) {
        return switch (name) {
            case "VIRIDIS" -> PlotStyle.VIRIDIS;
            case "SEQUENTIAL_BLUE" -> PlotStyle.SEQUENTIAL_BLUE;
            default -> PlotStyle.BLUE_WHITE_RED;
        };
    }

    private static void writeProcessed(Path output, HeatmapData data) throws Exception {
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            writer.write("row");
            for (String column : data.columnLabels()) {
                writer.write('\t');
                writer.write(column);
            }
            writer.newLine();
            for (int row = 0; row < data.rowLabels().size(); row++) {
                writer.write(data.rowLabels().get(row));
                for (Double value : data.values().get(row)) {
                    writer.write('\t');
                    if (value != null) {
                        writer.write(String.format(Locale.ROOT, "%.10g", value));
                    }
                }
                writer.newLine();
            }
        }
    }

    private static double parseDouble(ToolRequest request, String id) {
        Object value = request.value(id);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ex) {
            throw new OpenToolException(
                    "VIS_INVALID_NUMBER",
                    "Invalid numeric parameter: " + id,
                    "Enter a decimal number."
            );
        }
    }

    private static ToolParameter enumParameter(
            String id,
            String title,
            String description,
            String defaultValue,
            List<String> options,
            boolean advanced
    ) {
        return new ToolParameter(
                id,
                title,
                description,
                ParameterType.ENUM,
                false,
                defaultValue,
                options,
                null,
                null,
                advanced,
                null
        );
    }

    private static ToolParameter integerParameter(
            String id,
            String title,
            String description,
            int defaultValue,
            int minimum,
            int maximum,
            boolean advanced
    ) {
        return new ToolParameter(
                id,
                title,
                description,
                ParameterType.INTEGER,
                false,
                defaultValue,
                List.of(),
                (double) minimum,
                (double) maximum,
                advanced,
                null
        );
    }
}
