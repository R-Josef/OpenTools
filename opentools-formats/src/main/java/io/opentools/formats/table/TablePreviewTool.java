package io.opentools.formats.table;

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

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TablePreviewTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "table.preview",
            "Preview Delimited Table",
            "Stream a large TSV or CSV file and create a bounded preview.",
            "Table",
            List.of("table preview", "large table", "TSV preview", "表格预览"),
            List.of("TSV", "CSV", "TSV.gz", "CSV.gz"),
            List.of("TSV"),
            "1.1.0",
            List.of(
                    ToolParameter.requiredFile("input", "Input table", "A delimited text file, optionally gzip-compressed."),
                    new ToolParameter(
                            "delimiter",
                            "Delimiter",
                            "AUTO detects tab, comma, or semicolon.",
                            ParameterType.ENUM,
                            false,
                            "AUTO",
                            List.of("AUTO", "TAB", "COMMA", "SEMICOLON"),
                            null,
                            null,
                            false,
                            null
                    ),
                    ToolParameter.bool("firstRowHeader", "First row is header", "Use the first row as column names.", true),
                    ToolParameter.integer("previewRows", "Preview rows", "Maximum rows stored for the interactive preview.", 100, 1, 5000),
                    ToolParameter.integer("maxColumns", "Maximum columns", "Maximum columns included in the preview.", 200, 1, 5000),
                    ToolParameter.bool("countRows", "Count all rows", "Read the entire file to report total row count.", true),
                    ToolParameter.optionalOutputFile("output", "Preview TSV", "Leave blank to save inside the run directory."),
                    ToolParameter.bool("overwrite", "Overwrite output", "Allow replacing an existing custom output file.", false)
            )
    );

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ValidationResult validate(ToolRequest request) {
        ValidationResult base = validateParameters(request);
        Path output = request.path("output");
        if (output != null && Files.exists(output) && !request.bool("overwrite")) {
            base = base.merge(ValidationResult.of(ValidationIssue.error(
                    "output", "Output file already exists: " + output, "Choose another path or enable overwrite."
            )));
        }
        return base;
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        Path input = request.path("input");
        Path output = request.path("output");
        if (output == null || output.toString().isBlank()) {
            output = context.defaultOutputPath("table-preview.tsv");
        }
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        int previewLimit = request.integer("previewRows") <= 0 ? 100 : request.integer("previewRows");
        int columnLimit = request.integer("maxColumns") <= 0 ? 200 : request.integer("maxColumns");
        boolean firstRowHeader = request.bool("firstRowHeader");
        boolean countRows = request.bool("countRows");

        char delimiter = detectDelimiter(input, request.string("delimiter"));
        List<String> columns = List.of();
        List<List<String>> preview = new ArrayList<>();
        long totalRows = 0;
        long maxColumnsSeen = 0;
        try (DelimitedTableReader reader = new DelimitedTableReader(input, delimiter)) {
            List<String> row;
            while ((row = reader.nextRow()) != null) {
                context.cancellationToken().throwIfCancelled();
                maxColumnsSeen = Math.max(maxColumnsSeen, row.size());
                if (columns.isEmpty()) {
                    if (firstRowHeader) {
                        columns = buildColumns(row, columnLimit);
                        continue;
                    }
                    columns = generatedColumns(Math.min(row.size(), columnLimit));
                }
                totalRows++;
                if (preview.size() < previewLimit) {
                    preview.add(padRow(row, columns.size()));
                }
                if (!countRows && preview.size() >= previewLimit) {
                    break;
                }
                if (totalRows % 100_000 == 0) {
                    context.progress(0.1 + Math.min(0.8, totalRows / 1_000_000.0),
                            "Read " + totalRows + " rows");
                }
            }
        }
        if (columns.isEmpty()) {
            throw new OpenToolException("EMPTY_TABLE", "No tabular rows were found.", "Check the input delimiter and file content.");
        }
        writePreview(output, columns, preview);
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("rows", totalRows);
        metrics.put("columnsSeen", maxColumnsSeen);
        metrics.put("columnsIncluded", columns.size());
        metrics.put("previewRows", preview.size());
        metrics.put("countedCompletely", countRows);
        metrics.put("delimiter", delimiterName(delimiter));

        ResultTable table = new ResultTable(columns, preview);
        context.progress(1.0, "Table preview completed");
        return new ToolResult(
                "Previewed " + preview.size() + " of " + totalRows + " rows",
                List.of(preview.size(), totalRows),
                columns.size() + " columns included",
                List.of(columns.size()),
                table,
                List.of(new OutputArtifact(output, "Bounded table preview", "text/tab-separated-values")),
                metrics
        );
    }

    private static char detectDelimiter(Path input, String selected) throws Exception {
        return switch (selected == null ? "AUTO" : selected) {
            case "TAB" -> '\t';
            case "COMMA" -> ',';
            case "SEMICOLON" -> ';';
            default -> {
                String name = input.getFileName().toString().toLowerCase(Locale.ROOT);
                if (name.contains(".tsv") || name.contains(".tab")) {
                    yield '\t';
                }
                if (name.contains(".csv")) {
                    yield ',';
                }
                yield DelimitedTableReader.detectDelimiter(input);
            }
        };
    }

    private static List<String> buildColumns(List<String> header, int limit) {
        List<String> columns = new ArrayList<>();
        for (int i = 0; i < header.size() && i < limit; i++) {
            String value = header.get(i).trim();
            columns.add(value.isEmpty() ? "column_" + (i + 1) : value);
        }
        return List.copyOf(columns);
    }

    private static List<String> generatedColumns(int count) {
        List<String> columns = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            columns.add("column_" + (i + 1));
        }
        return List.copyOf(columns);
    }

    private static List<String> padRow(List<String> row, int size) {
        List<String> padded = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            padded.add(i < row.size() ? row.get(i) : "");
        }
        return List.copyOf(padded);
    }

    private static void writePreview(Path output, List<String> columns, List<List<String>> rows) throws Exception {
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            DelimitedTableWriter.writeRow(writer, columns, '\t');
            for (List<String> row : rows) {
                DelimitedTableWriter.writeRow(writer, row, '\t');
            }
        }
    }

    private static String delimiterName(char delimiter) {
        return delimiter == '\t' ? "TAB" : delimiter == ',' ? "COMMA" : "SEMICOLON";
    }
}
