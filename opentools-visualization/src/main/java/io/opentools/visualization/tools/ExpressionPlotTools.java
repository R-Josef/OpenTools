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
import io.opentools.formats.table.DelimitedTableReader;
import io.opentools.visualization.model.GenericPlotData;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ExpressionPlotTools {
    private ExpressionPlotTools() {
    }

    public static List<AbstractOpenTool> tools() {
        return List.of(
                new PlotPcaTool(),
                new PlotCorrelationTool(),
                new PlotVolcanoTool(),
                new PlotBoxTool(),
                new PlotViolinTool(),
                new PlotBarTool()
        );
    }
}

final class PlotPcaTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = descriptor(
            "plot.pca",
            "PCA Plot",
            "Plot PCA coordinates as grouped scatter series.",
            List.of(
                    ToolParameter.requiredFile("input", "PCA coordinates", "Output from expression.pca."),
                    ToolParameter.string("xColumn", "X column", "Coordinate column for x.", "PC1"),
                    ToolParameter.string("yColumn", "Y column", "Coordinate column for y.", "PC2"),
                    ToolParameter.string("groupColumn", "Group column", "Group/color column. Blank uses one series.", "group")
            )
    );

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ValidationResult validate(ToolRequest request) {
        return PlotToolSupport.validate(request);
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        var table = PlotToolSupport.readTable(request.path("input"), null);
        int x = table.requiredColumn(request.string("xColumn"));
        int y = table.requiredColumn(request.string("yColumn"));
        int group = request.string("groupColumn").isBlank() ? -1 : table.column(request.string("groupColumn"));
        Map<String, GenericPlotData.GenericSeries.Builder> series = new LinkedHashMap<>();
        for (List<String> row : table.rows()) {
            String name = group >= 0 && group < row.size() && !row.get(group).isBlank() ? row.get(group) : "All samples";
            series.computeIfAbsent(name, ignored -> new GenericPlotData.GenericSeries.Builder(name))
                    .add(PlotToolSupport.number(row, x), PlotToolSupport.number(row, y));
        }
        GenericPlotData data = new GenericPlotData("SCATTER",
                request.string("xColumn"), request.string("yColumn"), List.of(),
                series.values().stream().map(GenericPlotData.GenericSeries.Builder::build).toList());
        return PlotToolSupport.renderGeneric(this, request, context, data,
                "pca", request.string("title"),
                table.rows().size(), series.size());
    }

    private static ToolDescriptor descriptor(String id, String name, String description, List<ToolParameter> first) {
        return PlotToolSupport.descriptor(id, name, description, first, "OpenTools PCA");
    }
}

final class PlotCorrelationTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = PlotToolSupport.descriptor(
            "plot.correlation",
            "Correlation Heatmap",
            "Render a sample correlation matrix as a heatmap.",
            List.of(
                    ToolParameter.requiredFile("input", "Correlation matrix", "Square matrix with sample names in the first column."),
                    ToolParameter.bool("exportData", "Export processed data", "Write the correlation matrix processed copy.", true)
            ),
            "OpenTools Sample Correlation"
    );

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ValidationResult validate(ToolRequest request) {
        ValidationResult result = validateParameters(request);
        if (request.path("output") != null && !request.bool("overwrite")) {
            Path base = PlotArtifactSupport.stripKnownExtensions(request.path("output"));
            if (PlotArtifactSupport.anyExists(PlotArtifactSupport.expectedOutputs(
                    PlotToolSupport.spec(request, new GenericPlotData("SCATTER", "", "", List.of(), List.of())),
                    base))) {
                result = result.merge(ValidationResult.of(ValidationIssue.error(
                        "output", "One or more plot outputs already exist.", "Enable overwrite or choose another prefix.")));
            }
        }
        return result;
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        Map<String, Object> parameters = PlotToolSupport.heatmapParameters(request);
        ToolResult delegated = new HeatmapTool().execute(new ToolRequest("visual.heatmap", parameters), context);
        return new ToolResult(
                "Rendered correlation heatmap", List.of(),
                "Square correlation matrix rendered with the shared heatmap engine.", List.of(),
                delegated.table(), delegated.artifacts(), delegated.metrics()
        );
    }
}

final class PlotVolcanoTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = PlotToolSupport.descriptor(
            "plot.volcano",
            "Volcano Plot",
            "Plot log2 fold change against significance and classify up/down/not-significant genes.",
            List.of(
                    ToolParameter.requiredFile("input", "Differential table", "TSV with fold-change and p-value columns."),
                    ToolParameter.string("log2FoldChangeColumn", "Log2 fold-change column", "Column name.", "log2FoldChange"),
                    ToolParameter.string("pValueColumn", "P-value column", "Column name.", "padj"),
                    ToolParameter.decimal("foldChangeThreshold", "Fold-change threshold", "Minimum absolute log2 fold change.", 1.0, 0.0, 100.0),
                    ToolParameter.decimal("pValueThreshold", "P-value threshold", "Maximum significance value.", 0.05, 0.0, 1.0),
                    ToolParameter.bool("exportData", "Export classified points", "Write point classifications.", true)
            ),
            "OpenTools Volcano Plot"
    );

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ValidationResult validate(ToolRequest request) {
        ValidationResult result = PlotToolSupport.validate(request);
        if (request.integer("width") < 320 || request.integer("height") < 240) {
            result = result.merge(ValidationResult.of(ValidationIssue.error(
                    "width", "Plot dimensions are too small.", "Use at least 320x240.")));
        }
        return result;
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        var table = PlotToolSupport.readTable(request.path("input"), null);
        int lfc = table.requiredColumn(request.string("log2FoldChangeColumn"));
        int p = table.requiredColumn(request.string("pValueColumn"));
        Map<String, GenericPlotData.GenericSeries.Builder> groups = new LinkedHashMap<>();
        groups.put("Down", new GenericPlotData.GenericSeries.Builder("Down"));
        groups.put("Not significant", new GenericPlotData.GenericSeries.Builder("Not significant"));
        groups.put("Up", new GenericPlotData.GenericSeries.Builder("Up"));
        List<List<String>> classified = new ArrayList<>();
        for (List<String> row : table.rows()) {
            double lfcValue = PlotToolSupport.number(row, lfc);
            double pValue = Math.max(0, PlotToolSupport.number(row, p));
            double y = pValue <= 0 ? 300 : -Math.log10(pValue);
            String group = pValue <= PlotToolSupport.number(request.value("pValueThreshold"))
                    ? lfcValue >= PlotToolSupport.number(request.value("foldChangeThreshold")) ? "Up"
                    : lfcValue <= -PlotToolSupport.number(request.value("foldChangeThreshold")) ? "Down" : "Not significant"
                    : "Not significant";
            groups.get(group).add(lfcValue, y);
            classified.add(List.of(row.getFirst(), format(lfcValue), format(y), group));
        }
        GenericPlotData data = new GenericPlotData("SCATTER",
                "log2 fold change", "-log10(p-value)", List.of(),
                groups.values().stream().map(GenericPlotData.GenericSeries.Builder::build).toList());
        return PlotToolSupport.renderGenericWithData(this, request, context, data,
                "volcano", request.string("title"),
                classified, List.of("gene", "log2_fold_change", "neg_log10_p", "class"));
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.10g", value);
    }
}

final class PlotBoxTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = PlotToolSupport.descriptor(
            "plot.box",
            "Box Plot",
            "Render grouped numeric distributions as box plots.",
            groupValueParameters(),
            "OpenTools Box Plot"
    );

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ValidationResult validate(ToolRequest request) {
        return PlotToolSupport.validate(request);
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        return PlotToolSupport.groupedDistribution(this, request, context, "BOX", "box");
    }

    private static List<ToolParameter> groupValueParameters() {
        return List.of(
                ToolParameter.requiredFile("input", "Tidy table", "Delimited table with group and numeric value columns."),
                ToolParameter.string("groupColumn", "Group column", "Column containing group labels.", "group"),
                ToolParameter.string("valueColumn", "Value column", "Column containing numeric values.", "value")
        );
    }
}

final class PlotViolinTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = PlotToolSupport.descriptor(
            "plot.violin",
            "Violin Plot",
            "Render grouped numeric distributions as violin plots.",
            List.of(
                    ToolParameter.requiredFile("input", "Tidy table", "Delimited table with group and numeric value columns."),
                    ToolParameter.string("groupColumn", "Group column", "Column containing group labels.", "group"),
                    ToolParameter.string("valueColumn", "Value column", "Column containing numeric values.", "value")
            ),
            "OpenTools Violin Plot"
    );

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ValidationResult validate(ToolRequest request) {
        return PlotToolSupport.validate(request);
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        return PlotToolSupport.groupedDistribution(this, request, context, "VIOLIN", "violin");
    }
}

final class PlotBarTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = PlotToolSupport.descriptor(
            "plot.bar",
            "Bar Plot",
            "Render categories from a two-column label/value table.",
            List.of(
                    ToolParameter.requiredFile("input", "Bar table", "Delimited table with category and value columns."),
                    ToolParameter.string("categoryColumn", "Category column", "First column is used when blank.", ""),
                    ToolParameter.string("valueColumn", "Value column", "Second column is used when blank.", "")
            ),
            "OpenTools Bar Plot"
    );

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ValidationResult validate(ToolRequest request) {
        return PlotToolSupport.validate(request);
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        var table = PlotToolSupport.readTable(request.path("input"), null);
        int category = request.string("categoryColumn").isBlank() ? 0 : table.requiredColumn(request.string("categoryColumn"));
        int value = request.string("valueColumn").isBlank() ? 1 : table.requiredColumn(request.string("valueColumn"));
        GenericPlotData.GenericSeries.Builder series = new GenericPlotData.GenericSeries.Builder("Value");
        List<String> categories = new ArrayList<>();
        for (int row = 0; row < table.rows().size(); row++) {
            categories.add(PlotToolSupport.cell(table.rows().get(row), category));
            series.add(row, PlotToolSupport.number(table.rows().get(row), value));
        }
        GenericPlotData data = new GenericPlotData("BAR", "", request.string("valueLabel"),
                categories, List.of(series.build()));
        return PlotToolSupport.renderGeneric(this, request, context, data,
                "bar", request.string("title"), categories);
    }
}

final class PlotToolSupport {
    private static final List<String> PALETTE = List.of(
            "#1F6F78", "#D47A43", "#5F7DB5", "#7F9E56", "#B55C78", "#8C6BAA"
    );

    private PlotToolSupport() {
    }

    static ToolDescriptor descriptor(
            String id, String name, String description,
            List<ToolParameter> first, String defaultTitle
    ) {
        List<ToolParameter> parameters = new ArrayList<>(first);
        parameters.add(ToolParameter.string("title", "Plot title", "Chart title.", defaultTitle));
        parameters.add(ToolParameter.string("subtitle", "Plot subtitle", "Optional subtitle.", ""));
        parameters.add(ToolParameter.string("valueLabel", "Value label", "Y-axis value label.", ""));
        parameters.add(ToolParameter.integer("width", "Image width", "Export width in pixels.", 1200, 320, 10000));
        parameters.add(ToolParameter.integer("height", "Image height", "Export height in pixels.", 800, 240, 10000));
        parameters.add(ToolParameter.integer("dpi", "PDF DPI", "PDF export resolution.", 144, 72, 600));
        parameters.add(new ToolParameter("exportFormats", "Export formats", "Plot artifact formats.",
                ParameterType.ENUM, false, "PNG_SVG_PDF", List.of("PNG_SVG_PDF", "PNG_SVG", "PNG"),
                null, null, true, null));
        parameters.add(ToolParameter.optionalOutputFile("output", "Output prefix", "Leave blank to save in the run directory."));
        parameters.add(ToolParameter.bool("overwrite", "Overwrite outputs", "Allow replacing existing plot outputs.", false));
        return new ToolDescriptor(id, name, description, "Visualization",
                List.of(name.toLowerCase(Locale.ROOT), id, "plot", "表达图"),
                List.of("TSV", "CSV"), List.of("PNG", "SVG", "PDF", "JSON"),
                "1.1.0", parameters);
    }

    static ValidationResult validate(ToolRequest request) {
        ValidationResult result = new PlotValidation().validate(request);
        if (request.path("output") != null && !request.bool("overwrite")) {
            Path base = PlotArtifactSupport.stripKnownExtensions(request.path("output"));
            if (PlotArtifactSupport.anyExists(PlotArtifactSupport.expectedOutputs(
                    spec(request, new GenericPlotData("SCATTER", "", "", List.of(), List.of())), base))) {
                result = result.merge(ValidationResult.of(ValidationIssue.error(
                        "output", "One or more plot outputs already exist.",
                        "Choose another prefix or enable overwrite.")));
            }
        }
        return result;
    }

    static ToolResult renderGeneric(
            AbstractOpenTool tool, ToolRequest request, ToolExecutionContext context, GenericPlotData data,
            String defaultName, String title, Object... summaryArguments
    ) throws Exception {
        return renderGenericWithData(tool, request, context, data, defaultName, title,
                List.of(), List.of(), summaryArguments);
    }

    static ToolResult renderGenericWithData(
            AbstractOpenTool tool, ToolRequest request, ToolExecutionContext context, GenericPlotData data,
            String defaultName, String title,
            List<List<String>> processedRows, List<String> processedHeader,
            Object... summaryArguments
    ) throws Exception {
        Path base = PlotArtifactSupport.basePath(context, request.path("output"), defaultName);
        PlotExport export = export(request, base);
        PlotStyle defaults = PlotStyle.defaults();
        PlotStyle style = new PlotStyle(defaults.backgroundColor(), defaults.foregroundColor(),
                defaults.mutedColor(), defaults.accentColor(), defaults.missingColor(),
                PALETTE, defaults.fontFamily(), defaults.baseFontSize(), 1.4,
                true, false, null, null, "NONE", 1, 1);
        PlotSpec spec = new PlotSpec("1.0", defaultName, PlotType.GENERIC,
                title.isBlank() ? tool.descriptor().name() : title,
                request.string("subtitle"), style, export, Map.of(), data);
        PlotArtifactSupport.RenderedPlot rendered = PlotArtifactSupport.render(
                tool.descriptor().id(), spec, context, base,
                PlotArtifactSupport.lineage(tool.descriptor().id(), spec,
                        request.path("input")));
        List<OutputArtifact> artifacts = new ArrayList<>(rendered.artifacts());
        if (!processedRows.isEmpty()) {
            Path processed = Path.of(base + ".processed.tsv");
            writeProcessed(processed, processedHeader, processedRows);
            artifacts.add(new OutputArtifact(processed, "Plot source data", "text/tab-separated-values"));
        }
        context.progress(1.0, tool.descriptor().name() + " completed");
        return new ToolResult(
                tool.descriptor().name() + " completed", List.of(summaryArguments),
                data.series().size() + " series rendered.", List.of(data.series().size()),
                new ResultTable(List.of("Metric", "Value"), List.of(
                        List.of("Series", Integer.toString(data.series().size())),
                        List.of("Chart", data.chart())
                )),
                artifacts,
                Map.of("series", data.series().size(), "chart", data.chart(),
                        "exportFormats", export.formats())
        );
    }

    static ToolResult groupedDistribution(
            AbstractOpenTool tool, ToolRequest request, ToolExecutionContext context,
            String chart, String defaultName
    ) throws Exception {
        PlotTable table = readTable(request.path("input"), null);
        int group = table.requiredColumn(request.string("groupColumn"));
        int value = table.requiredColumn(request.string("valueColumn"));
        Map<String, GenericPlotData.GenericSeries.Builder> byGroup = new LinkedHashMap<>();
        for (List<String> row : table.rows()) {
            byGroup.computeIfAbsent(cell(row, group),
                    ignored -> new GenericPlotData.GenericSeries.Builder(cell(row, group)))
                    .add(byGroup.size(), number(row, value));
        }
        List<String> categories = List.copyOf(byGroup.keySet());
        GenericPlotData data = new GenericPlotData(chart, "Group", request.string("valueLabel"),
                categories, byGroup.values().stream().map(GenericPlotData.GenericSeries.Builder::build).toList());
        return renderGeneric(tool, request, context, data, defaultName,
                request.string("title"), categories);
    }

    static PlotTable readTable(Path input, String delimiter) throws Exception {
        char separator = switch (delimiter == null ? "AUTO" : delimiter) {
            case "", "AUTO" -> DelimitedTableReader.detectDelimiter(input);
            case "TAB" -> '\t';
            case "COMMA" -> ',';
            case "SEMICOLON" -> ';';
            default -> delimiter.charAt(0);
        };
        List<String> headers;
        List<List<String>> rows = new ArrayList<>();
        try (DelimitedTableReader reader = new DelimitedTableReader(
                input, separator, true, true)) {
            headers = reader.nextRow();
            List<String> fields;
            while ((fields = reader.nextRow()) != null) {
                rows.add(fields);
            }
        }
        if (headers == null || rows.isEmpty()) {
            throw new OpenToolException("PLOT_TABLE_EMPTY",
                    "Plot input must contain a header and at least one data row.",
                    "Check the selected columns and delimiter.");
        }
        return new PlotTable(headers, rows);
    }

    static PlotSpec spec(ToolRequest request, GenericPlotData data) {
        Path base = Path.of("plot");
        return new PlotSpec("1.0", "plot", PlotType.GENERIC, request.string("title"), "",
                PlotStyle.defaults(), export(request, base), Map.of(), data);
    }

    static Map<String, Object> heatmapParameters(ToolRequest request) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("input", request.path("input").toString());
        parameters.put("delimiter", "TAB");
        parameters.put("missingPolicy", "ERROR");
        parameters.put("invalidPolicy", "ERROR");
        parameters.put("normalization", "NONE");
        parameters.put("rowOrder", "ORIGINAL");
        parameters.put("columnOrder", "ORIGINAL");
        parameters.put("palette", "BLUE_WHITE_RED");
        parameters.put("showValues", false);
        parameters.put("rangeMode", "AUTO");
        parameters.put("rangeMin", -1);
        parameters.put("rangeMax", 1);
        parameters.put("title", request.string("title"));
        parameters.put("subtitle", request.string("subtitle"));
        parameters.put("maxRows", 5000);
        parameters.put("maxColumns", 5000);
        parameters.put("width", request.integer("width"));
        parameters.put("height", request.integer("height"));
        parameters.put("dpi", request.integer("dpi"));
        parameters.put("exportFormats", request.string("exportFormats"));
        parameters.put("exportData", request.bool("exportData"));
        if (request.path("output") != null) {
            parameters.put("output", request.path("output").toString());
        }
        parameters.put("overwrite", request.bool("overwrite"));
        return parameters;
    }

    private static PlotExport export(ToolRequest request, Path base) {
        List<String> formats = switch (request.string("exportFormats")) {
            case "PNG_SVG" -> List.of("PNG", "SVG");
            case "PNG" -> List.of("PNG");
            default -> List.of("PNG", "SVG", "PDF");
        };
        return new PlotExport(formats, base.getFileName().toString(),
                request.integer("width"), request.integer("height"),
                request.integer("dpi"), false, request.bool("overwrite"));
    }

    private static void writeProcessed(Path output, List<String> header, List<List<String>> rows) throws Exception {
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            writer.write(String.join("\t", header));
            writer.newLine();
            for (List<String> row : rows) {
                writer.write(String.join("\t", row));
                writer.newLine();
            }
        }
    }

    static double number(List<String> row, int column) {
        try {
            return Double.parseDouble(cell(row, column));
        } catch (NumberFormatException ex) {
            throw new OpenToolException("PLOT_NUMERIC_VALUE_INVALID",
                    "Expected a number in column " + (column + 1) + ".",
                    "Check the selected value columns.");
        }
    }

    static String cell(List<String> row, int column) {
        return column >= 0 && column < row.size() ? row.get(column).trim() : "";
    }

    static double number(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    record PlotTable(List<String> headers, List<List<String>> rows) {
        int requiredColumn(String name) {
            int index = column(name);
            if (index < 0) {
                throw new OpenToolException("PLOT_COLUMN_MISSING",
                        "Column not found: " + name, "Check the table header.");
            }
            return index;
        }

        int column(String name) {
            for (int index = 0; index < headers.size(); index++) {
                if (headers.get(index).equalsIgnoreCase(name)) {
                    return index;
                }
            }
            return -1;
        }
    }

    private static final class PlotValidation {
        ValidationResult validate(ToolRequest request) {
            List<ValidationIssue> issues = new ArrayList<>();
            if (request.path("input") == null || !Files.isRegularFile(request.path("input"))) {
                issues.add(ValidationIssue.error("input", "Input file does not exist.", "Choose a readable file."));
            }
            if (request.integer("width") < 320 || request.integer("height") < 240) {
                issues.add(ValidationIssue.error("width", "Plot dimensions are too small.", "Use at least 320x240."));
            }
            return new ValidationResult(issues);
        }
    }
}
