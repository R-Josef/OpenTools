package io.opentools.analysis.table;

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
import io.opentools.api.tool.ValidationResult;
import io.opentools.core.util.OrderedParallelProcessor;
import io.opentools.core.util.SqliteSpillSupport;
import io.opentools.formats.FormatToolSupport;
import io.opentools.formats.table.DelimitedTableReader;
import io.opentools.formats.table.DelimitedTableWriter;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class TableTools {
    static final long TABLE_FILTER_AUTO_BYTES = Long.MAX_VALUE;
    static final long TABLE_SELECT_AUTO_BYTES = Long.MAX_VALUE;
    static final long TABLE_REPLACE_AUTO_BYTES = Long.MAX_VALUE;
    private static final int TABLE_PARALLEL_BATCH_ROWS = 1024;

    private TableTools() {
    }

    public static List<AbstractOpenTool> tools() {
        return List.of(
                new TableFilterTool(),
                new SelectColumnsTool(),
                new TableJoinTool(),
                new TableTransposeTool(),
                new TableDeduplicateTool(),
                new TablePivotTool(),
                new TableReplaceTool()
        );
    }

    static final class TableFilterTool extends AbstractOpenTool {
        private static final ToolDescriptor DESCRIPTOR = TableTools.descriptor(
                "table.filter", "Filter Table", "Filter rows with text/regex or numeric range predicates.",
                List.of(
                        ToolParameter.requiredFile("input", "Input table", "Delimited text with header."),
                        ToolParameter.string("column", "Filter column", "Column to test.", ""),
                        ToolParameter.string("numericColumn", "Numeric column", "Optional column for numeric bounds.", ""),
                        ToolParameter.string("regex", "Text regex", "Optional Java regex for text matching.", ""),
                        ToolParameter.string("numericMin", "Numeric minimum", "Optional numeric lower bound.", ""),
                        ToolParameter.string("numericMax", "Numeric maximum", "Optional numeric upper bound.", ""),
                        ToolParameter.bool("invert", "Invert selection", "Keep non-matching rows.", false),
                        ToolParameter.integer("threads", "Threads",
                                "0 keeps sequential parsing; explicit 2-64 enables bounded workers.",
                                0, 0, 64)
                ), "1.2.0");

        @Override public ToolDescriptor descriptor() { return DESCRIPTOR; }
        @Override public ValidationResult validate(ToolRequest request) { return validateTable(request); }
        @Override public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
            double min = parseBound(request.string("numericMin"), Double.NEGATIVE_INFINITY);
            double max = parseBound(request.string("numericMax"), Double.POSITIVE_INFINITY);
            Pattern regex = request.string("regex").isBlank() ? null : Pattern.compile(request.string("regex"));
            Path output = FormatToolSupport.outputPath(request, context, "filtered-table.tsv");
            long[] totals = new long[3];
            char delimiter = delimiter(request.path("input"), request.string("delimiter"));
            int threads = resolveTableThreads(
                    request.integer("threads"), Files.size(request.path("input")),
                    TABLE_FILTER_AUTO_BYTES);
            boolean invert = request.bool("invert");
            try (DelimitedTableReader reader = new DelimitedTableReader(
                    request.path("input"), delimiter, true, true)) {
                List<String> headers = requireHeader(reader);
                int column = columnIndex(headers, request.string("column"));
                int numericColumn = request.string("numericColumn").isBlank()
                        ? column : columnIndex(headers, request.string("numericColumn"));
                FormatToolSupport.writeAtomically(output, writer -> {
                    writeLine(writer, headers);
                    OrderedParallelProcessor.processChunks(
                            threads,
                            TABLE_PARALLEL_BATCH_ROWS,
                            context.cancellationToken(),
                            reader::nextRow,
                            chunk -> filterChunk(
                                    chunk, column, numericColumn, regex, min, max, invert),
                            chunk -> {
                                totals[0] += chunk.inputRows();
                                totals[1] += chunk.retainedRows();
                                totals[2] += chunk.filteredRows();
                                for (List<String> row : chunk.rows()) {
                                    writeLine(writer, row);
                                }
                            }
                    );
                });
            }
            context.progress(1.0, "Table filter completed");
            ToolResult result = withMetric(
                    tableResult("table.filter", output, totals[0], totals[1]),
                    "filteredRows", totals[2]);
            result = withMetric(result, "threads", threads);
            return withMetric(result, "parallelRecords", threads > 1);
        }
    }

    static final class SelectColumnsTool extends AbstractOpenTool {
        private static final ToolDescriptor DESCRIPTOR = TableTools.descriptor(
                "table.select-columns", "Select Table Columns", "Select and reorder columns in a table.",
                List.of(ToolParameter.requiredFile("input", "Input table", "Delimited text with header."),
                        ToolParameter.requiredText("columns", "Columns", "Comma/whitespace-separated output column names."),
                        ToolParameter.integer("threads", "Threads",
                                "0 keeps sequential parsing; explicit 2-64 enables bounded workers.",
                                0, 0, 64)),
                "1.2.0");

        @Override public ToolDescriptor descriptor() { return DESCRIPTOR; }
        @Override public ValidationResult validate(ToolRequest request) { return validateTable(request); }
        @Override public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
            Path output = FormatToolSupport.outputPath(request, context, "selected-columns.tsv");
            long[] rows = new long[1];
            char delimiter = delimiter(request.path("input"), request.string("delimiter"));
            int threads = resolveTableThreads(
                    request.integer("threads"), Files.size(request.path("input")),
                    TABLE_SELECT_AUTO_BYTES);
            try (DelimitedTableReader reader = new DelimitedTableReader(
                    request.path("input"), delimiter, true, true)) {
                List<String> headers = requireHeader(reader);
                List<Integer> selected = new ArrayList<>();
                List<String> names = new ArrayList<>();
                for (String name : splitNames(request.string("columns"))) {
                    names.add(name);
                    selected.add(columnIndex(headers, name));
                }
                if (names.isEmpty()) {
                    throw new OpenToolException(
                            "TABLE_COLUMNS_REQUIRED", "At least one output column is required.",
                            "Enter one or more header names.");
                }
                List<Integer> selectedColumns = List.copyOf(selected);
                FormatToolSupport.writeAtomically(output, writer -> {
                    writeLine(writer, names);
                    OrderedParallelProcessor.processChunks(
                            threads,
                            TABLE_PARALLEL_BATCH_ROWS,
                            context.cancellationToken(),
                            reader::nextRow,
                            chunk -> chunk.stream().map(row -> selectedColumns.stream()
                                    .map(index -> field(row, index))
                                    .toList()).toList(),
                            projectedRows -> {
                                for (List<String> projected : projectedRows) {
                                    writeLine(writer, projected);
                                    rows[0]++;
                                }
                            }
                    );
                });
            }
            context.progress(1.0, "Column selection completed");
            ToolResult result = tableResult("table.select_columns", output, rows[0], rows[0]);
            result = withMetric(result, "threads", threads);
            return withMetric(result, "parallelRecords", threads > 1);
        }
    }

    static final class TableJoinTool extends AbstractOpenTool {
        private static final ToolDescriptor DESCRIPTOR = TableTools.descriptor(
                "table.join", "Join Tables", "Join two tables on one key column.",
                List.of(ToolParameter.requiredFile("left", "Left table", "Delimited text with header."),
                        ToolParameter.requiredFile("right", "Right table", "Delimited text with header."),
                        ToolParameter.string("leftKey", "Left key", "Left join column.", ""),
                        ToolParameter.string("rightKey", "Right key", "Right join column.", ""),
                        new ToolParameter("joinType", "Join type", "Join mode.", ParameterType.ENUM,
                                false, "INNER", List.of("INNER", "LEFT", "RIGHT", "FULL"),
                                null, null, false, null),
                        ToolParameter.integer("maxRightRows", "Maximum right rows",
                                "Safety bound for the right-side hash index.", 2_000_000, 1, 100_000_000)));

        @Override public ToolDescriptor descriptor() { return DESCRIPTOR; }
        @Override public ValidationResult validate(ToolRequest request) { return validateTable(request); }
        @Override public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
            char leftDelimiter = delimiter(request.path("left"), request.string("delimiter"));
            char rightDelimiter = delimiter(request.path("right"), request.string("delimiter"));
            Table right = Table.read(
                    request.path("right"),
                    rightDelimiter,
                    request.integer("maxRightRows"),
                    Integer.MAX_VALUE
            );
            List<Integer> rightKeys = keyIndexes(right.headers, request.string("rightKey"));
            List<Integer> leftKeys;
            try (DelimitedTableReader reader = new DelimitedTableReader(
                    request.path("left"), leftDelimiter, true, true)) {
                List<String> leftHeaders = requireHeader(reader);
                leftKeys = keyIndexes(leftHeaders, request.string("leftKey"));
                if (leftKeys.size() != rightKeys.size()) {
                    throw new OpenToolException(
                            "TABLE_JOIN_KEY_COUNT",
                            "Left and right join key counts differ.",
                            "Provide the same number of comma-separated keys on both sides."
                    );
                }
                Map<List<String>, List<IndexedRow>> rightRows = new LinkedHashMap<>();
                for (int index = 0; index < right.rows.size(); index++) {
                    List<String> row = right.rows.get(index);
                    rightRows.computeIfAbsent(key(row, rightKeys), ignored -> new ArrayList<>())
                            .add(new IndexedRow(index, row));
                }
                boolean[] matchedRight = new boolean[right.rows.size()];
                String joinType = request.string("joinType");
                Path output = FormatToolSupport.outputPath(request, context, "joined-table.tsv");
                long[] totals = new long[4];
                FormatToolSupport.writeAtomically(output, writer -> {
                    List<String> header = new ArrayList<>(leftHeaders);
                    header.addAll(uniqueRightHeaders(leftHeaders, right.headers));
                    writeLine(writer, header);
                    List<String> leftRow;
                    while ((leftRow = reader.nextRow()) != null) {
                        context.cancellationToken().throwIfCancelled();
                        totals[0]++;
                        List<IndexedRow> matches = rightRows.get(key(leftRow, leftKeys));
                        if (matches == null || matches.isEmpty()) {
                            totals[2]++;
                            if ("LEFT".equals(joinType) || "FULL".equals(joinType)) {
                                writeJoined(writer, leftRow, null, right.headers.size());
                                totals[1]++;
                            }
                        } else {
                            for (IndexedRow match : matches) {
                                matchedRight[match.index()] = true;
                                writeJoined(writer, leftRow, match.row(), right.headers.size());
                                totals[1]++;
                            }
                        }
                    }
                    if ("RIGHT".equals(joinType) || "FULL".equals(joinType)) {
                        for (int index = 0; index < right.rows.size(); index++) {
                            if (!matchedRight[index]) {
                                writeJoined(writer, null, right.rows.get(index), leftHeaders.size());
                                totals[1]++;
                                totals[3]++;
                            }
                        }
                    }
                });
                context.progress(1.0, "Table join completed");
                ToolResult result = tableResult("table.join", output, totals[0], totals[1]);
                result = withMetric(result, "unmatchedLeftRows", totals[2]);
                result = withMetric(result, "unmatchedRightRows", totals[3]);
                return result;
            }
        }
    }

    static final class TableTransposeTool extends AbstractOpenTool {
        private static final ToolDescriptor DESCRIPTOR = TableTools.descriptor(
                "table.transpose", "Transpose Table",
                "Transpose small tables in memory or spill arbitrary-sized tables to a temporary SQLite work file.",
                List.of(ToolParameter.requiredFile("input", "Input table", "Delimited text with header."),
                        new ToolParameter("transposeMode", "Transpose mode",
                                "AUTO spills large inputs or when maxCells would be exceeded; DISK always uses bounded memory.",
                                ParameterType.ENUM, false, "AUTO",
                                List.of("AUTO", "IN_MEMORY", "DISK"),
                                null, null, false, null),
                        ToolParameter.integer("maxCells", "Maximum cells",
                                "Safety bound for the in-memory path.",
                                5_000_000, 1, 100_000_000),
                        ToolParameter.integer("maxTempMegabytes", "Maximum temporary SQLite size",
                                "Disk safety limit for DISK/AUTO spill mode; 0 means no explicit limit.",
                                8192, 0, 1_048_576)),
                "1.3.0");

        @Override public ToolDescriptor descriptor() { return DESCRIPTOR; }
        @Override public ValidationResult validate(ToolRequest request) { return validateTable(request); }
        @Override public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
            char delimiter = delimiter(request.path("input"), request.string("delimiter"));
            Path output = FormatToolSupport.outputPath(request, context, "transposed.tsv");
            boolean disk = shouldUseDisk(request);
            if (disk) {
                long maxTempBytes = SqliteSpillSupport.megabytes(
                        request.integer("maxTempMegabytes"));
                TransposeStats stats = transposeWithDatabase(
                        request.path("input"), delimiter, output, maxTempBytes, context);
                context.progress(1.0, "Table transpose completed");
                ToolResult result = tableResult(
                        "table.transpose", output, stats.inputRows(), stats.columns());
                result = withMetric(result, "transposeMode", "DISK");
                result = withMetric(result, "spilledCells", stats.nonEmptyCells());
                result = withMetric(result, "spillLimitBytes", maxTempBytes);
                return result;
            }
            try {
                Table table = Table.read(
                        request.path("input"), delimiter, Integer.MAX_VALUE, request.integer("maxCells"));
                int columns = 0;
                for (List<String> source : table.rows) {
                    columns = Math.max(columns, source.size());
                }
                columns = Math.max(columns, table.headers.size());
                int outputColumns = columns;
                FormatToolSupport.writeAtomically(output, writer -> {
                    for (int column = 0; column < outputColumns; column++) {
                        DelimitedTableWriter.writeField(writer, columnName(table.headers, column), '\t');
                        for (List<String> source : table.rows) {
                            writer.write('\t');
                            DelimitedTableWriter.writeField(
                                    writer, column < source.size() ? source.get(column) : "", '\t');
                        }
                        writer.write('\n');
                    }
                });
                context.progress(1.0, "Table transpose completed");
                ToolResult result = tableResult(
                        "table.transpose", output, table.rows.size(), columns);
                result = withMetric(result, "transposeMode", "IN_MEMORY");
                result = withMetric(result, "spilledCells", 0);
                return result;
            } catch (OpenToolException ex) {
                if (!"AUTO".equals(request.string("transposeMode"))
                        || !"TABLE_MEMORY_BOUND".equals(ex.code())) {
                    throw ex;
                }
                context.log("In-memory transpose bound reached; retrying with disk spool");
            }
            long maxTempBytes = SqliteSpillSupport.megabytes(
                    request.integer("maxTempMegabytes"));
            TransposeStats stats = transposeWithDatabase(
                    request.path("input"), delimiter, output, maxTempBytes, context);
            context.progress(1.0, "Table transpose completed");
            ToolResult result = tableResult(
                    "table.transpose", output, stats.inputRows(), stats.columns());
            result = withMetric(result, "transposeMode", "DISK");
            result = withMetric(result, "spilledCells", stats.nonEmptyCells());
            result = withMetric(result, "spillLimitBytes", maxTempBytes);
            return result;
        }

        private static boolean shouldUseDisk(ToolRequest request) throws Exception {
            String mode = request.string("transposeMode");
            if ("DISK".equals(mode)) {
                return true;
            }
            if ("IN_MEMORY".equals(mode)) {
                return false;
            }
            return Files.size(request.path("input")) >= 8L * 1024 * 1024;
        }

        private static TransposeStats transposeWithDatabase(
                Path input,
                char delimiter,
                Path output,
                long maxTempBytes,
                ToolExecutionContext context
        ) throws Exception {
            Path tempDirectory = context.runDirectory().resolve("tmp");
            Path database = SqliteSpillSupport.createTemporaryDatabase(
                    tempDirectory, "transpose-");
            try {
                try {
                    long inputRows = 0;
                    long cells = 0;
                    int columns;
                    List<String> headers;
                    try (Connection connection = DriverManager.getConnection(
                            "jdbc:sqlite:" + database.toAbsolutePath());
                         Statement setup = connection.createStatement()) {
                        setup.execute("PRAGMA journal_mode=OFF");
                        setup.execute("PRAGMA synchronous=OFF");
                        setup.execute("PRAGMA temp_store=FILE");
                        setup.execute("""
                                CREATE TABLE cells (
                                  column_index INTEGER NOT NULL,
                                  row_index INTEGER NOT NULL,
                                  value TEXT NOT NULL,
                                  PRIMARY KEY (column_index, row_index)
                                ) WITHOUT ROWID
                                """);
                        connection.setAutoCommit(false);
                        try (DelimitedTableReader reader = new DelimitedTableReader(
                                input, delimiter, true, true)) {
                            headers = requireHeader(reader);
                            columns = headers.size();
                            try (PreparedStatement insert = connection.prepareStatement(
                                    "INSERT INTO cells(column_index,row_index,value) VALUES(?,?,?)")) {
                                List<String> row;
                                while ((row = reader.nextRow()) != null) {
                                    context.cancellationToken().throwIfCancelled();
                                    columns = Math.max(columns, row.size());
                                    for (int columnIndex = 0; columnIndex < row.size(); columnIndex++) {
                                        String value = row.get(columnIndex);
                                        if (value.isEmpty()) {
                                            continue;
                                        }
                                        insert.setInt(1, columnIndex);
                                        insert.setLong(2, inputRows);
                                        insert.setString(3, value);
                                        insert.addBatch();
                                        cells++;
                                    }
                                    inputRows++;
                                    if (inputRows % 10_000 == 0) {
                                        insert.executeBatch();
                                        connection.commit();
                                        SqliteSpillSupport.enforceLimit(database, maxTempBytes);
                                    }
                                }
                                insert.executeBatch();
                                connection.commit();
                                SqliteSpillSupport.enforceLimit(database, maxTempBytes);
                            }
                        }
                        int finalColumns = columns;
                        long finalInputRows = inputRows;
                        FormatToolSupport.writeAtomically(output, writer -> {
                            try (PreparedStatement select = connection.prepareStatement(
                                    "SELECT row_index,value FROM cells "
                                            + "WHERE column_index=? ORDER BY row_index")) {
                                for (int column = 0; column < finalColumns; column++) {
                                    context.cancellationToken().throwIfCancelled();
                                    writer.write(columnName(headers, column));
                                    select.setInt(1, column);
                                    try (ResultSet values = select.executeQuery()) {
                                        long expectedRow = 0;
                                        boolean hasValue = values.next();
                                        while (expectedRow < finalInputRows) {
                                            writer.write('\t');
                                            if (hasValue && values.getLong(1) == expectedRow) {
                                                DelimitedTableWriter.writeField(
                                                        writer, values.getString(2), '\t');
                                                hasValue = values.next();
                                            }
                                            expectedRow++;
                                        }
                                    }
                                    writer.write('\n');
                                }
                            }
                        });
                    }
                    return new TransposeStats(inputRows, columns, cells);
                } catch (SQLException ex) {
                    throw SqliteSpillSupport.translateSqlException(ex, "Table transpose");
                }
            } finally {
                SqliteSpillSupport.deleteDatabaseQuietly(database);
            }
        }
    }

    static final class TableDeduplicateTool extends AbstractOpenTool {
        private static final ToolDescriptor DESCRIPTOR = TableTools.descriptor(
                "table.deduplicate", "Deduplicate Table", "Keep the first row per key.",
                List.of(ToolParameter.requiredFile("input", "Input table", "Delimited text with header."),
                        ToolParameter.requiredText("keyColumns", "Key columns", "One or more columns defining duplicates."),
                        ToolParameter.integer("maxDistinctKeys", "Maximum distinct keys",
                                "Safety bound for the in-memory key index.",
                                2_000_000, 1, 100_000_000)));

        @Override public ToolDescriptor descriptor() { return DESCRIPTOR; }
        @Override public ValidationResult validate(ToolRequest request) { return validateTable(request); }
        @Override public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
            char delimiter = delimiter(request.path("input"), request.string("delimiter"));
            Path output = FormatToolSupport.outputPath(request, context, "deduplicated.tsv");
            Set<List<String>> seen = new LinkedHashSet<>();
            long[] totals = new long[3];
            try (DelimitedTableReader reader = new DelimitedTableReader(
                    request.path("input"), delimiter, true, true)) {
                List<String> headers = requireHeader(reader);
                List<Integer> keys = keyIndexes(headers, request.string("keyColumns"));
                FormatToolSupport.writeAtomically(output, writer -> {
                    writeLine(writer, headers);
                    List<String> row;
                    while ((row = reader.nextRow()) != null) {
                        context.cancellationToken().throwIfCancelled();
                        totals[0]++;
                        List<String> key = key(row, keys);
                        if (seen.contains(key)) {
                            totals[2]++;
                        } else {
                            if (seen.size() >= request.integer("maxDistinctKeys")) {
                                throw new OpenToolException(
                                        "TABLE_MEMORY_BOUND",
                                        "Distinct deduplication keys exceed maxDistinctKeys.",
                                        "Raise the bound or pre-partition the table."
                                );
                            }
                            seen.add(key);
                            writeLine(writer, row);
                            totals[1]++;
                        }
                    }
                });
            }
            context.progress(1.0, "Deduplication completed");
            ToolResult result = tableResult("table.deduplicate", output, totals[0], totals[1]);
            return withMetric(result, "duplicates", totals[2]);
        }
    }

    static final class TablePivotTool extends AbstractOpenTool {
        private static final ToolDescriptor DESCRIPTOR = TableTools.descriptor(
                "table.pivot", "Pivot Table", "Pivot long rows into a wide matrix with aggregation.",
                List.of(ToolParameter.requiredFile("input", "Input table", "Delimited text with header."),
                        ToolParameter.string("rowColumn", "Row column", "Pivot row key.", ""),
                        ToolParameter.string("columnColumn", "Column column", "Pivot column key.", ""),
                        ToolParameter.string("valueColumn", "Value column", "Numeric value column.", ""),
                        new ToolParameter("aggregation", "Aggregation", "Duplicate-cell aggregation.", ParameterType.ENUM,
                                false, "SUM", List.of("SUM", "MEAN", "COUNT", "MIN", "MAX"),
                                null, null, false, null),
                        ToolParameter.integer("maxCells", "Maximum output cells",
                                "Safety bound for distinct pivot cells held in memory.",
                                2_000_000, 1, 100_000_000)));

        @Override public ToolDescriptor descriptor() { return DESCRIPTOR; }
        @Override public ValidationResult validate(ToolRequest request) { return validateTable(request); }
        @Override public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
            char delimiter = delimiter(request.path("input"), request.string("delimiter"));
            Map<String, Map<String, Aggregate>> values = new LinkedHashMap<>();
            Set<String> pivotColumns = new LinkedHashSet<>();
            long inputRows = 0;
            long cells = 0;
            try (DelimitedTableReader reader = new DelimitedTableReader(
                    request.path("input"), delimiter, true, true)) {
                List<String> headers = requireHeader(reader);
                int rowCol = columnIndex(headers, request.string("rowColumn"));
                int colCol = columnIndex(headers, request.string("columnColumn"));
                int valueCol = columnIndex(headers, request.string("valueColumn"));
                List<String> row;
                while ((row = reader.nextRow()) != null) {
                    context.cancellationToken().throwIfCancelled();
                    inputRows++;
                    String rowKey = field(row, rowCol);
                    String columnKey = field(row, colCol);
                    double value;
                    try {
                        value = Double.parseDouble(field(row, valueCol));
                    } catch (NumberFormatException ex) {
                        throw new OpenToolException(
                                "PIVOT_VALUE_INVALID",
                                "Non-numeric pivot value: " + field(row, valueCol),
                                "Choose a numeric value column."
                        );
                    }
                    Map<String, Aggregate> rowValues = values.computeIfAbsent(
                            rowKey, ignored -> new LinkedHashMap<>());
                    Aggregate aggregate = rowValues.get(columnKey);
                    if (aggregate == null) {
                        if (cells >= request.integer("maxCells")) {
                            throw new OpenToolException(
                                    "TABLE_MEMORY_BOUND",
                                    "Distinct pivot cells exceed maxCells.",
                                    "Raise the bound or aggregate the input in smaller partitions."
                            );
                        }
                        aggregate = new Aggregate();
                        rowValues.put(columnKey, aggregate);
                        pivotColumns.add(columnKey);
                        cells++;
                    }
                    aggregate.add(value);
                }
            }
            List<String> outputColumns = new ArrayList<>();
            outputColumns.add("row");
            outputColumns.addAll(pivotColumns);
            List<List<String>> outputRows = new ArrayList<>();
            for (var rowEntry : values.entrySet()) {
                List<String> output = new ArrayList<>();
                output.add(rowEntry.getKey());
                for (String column : pivotColumns) {
                    Aggregate aggregate = rowEntry.getValue().get(column);
                    output.add(aggregate == null ? ""
                            : aggregate.format(request.string("aggregation")));
                }
                outputRows.add(output);
            }
            Path output = FormatToolSupport.outputPath(request, context, "pivot.tsv");
            Table.writeAll(output, outputColumns, outputRows);
            context.progress(1.0, "Pivot completed");
            ToolResult result = tableResult("table.pivot", output, inputRows, outputRows.size());
            return withMetric(result, "pivotCells", cells);
        }
    }

    static final class TableReplaceTool extends AbstractOpenTool {
        private static final ToolDescriptor DESCRIPTOR = TableTools.descriptor(
                "table.replace", "Replace Table Text", "Regex replace values in one column.",
                List.of(ToolParameter.requiredFile("input", "Input table", "Delimited text with header."),
                        ToolParameter.string("column", "Column", "Column to edit.", ""),
                        ToolParameter.requiredText("search", "Search regex", "Java regular expression."),
                        ToolParameter.string("replacement", "Replacement", "Replacement text.", ""),
                        ToolParameter.integer("threads", "Threads",
                                "0 keeps sequential parsing; explicit 2-64 enables bounded workers.",
                                0, 0, 64)),
                "1.2.0");

        @Override public ToolDescriptor descriptor() { return DESCRIPTOR; }
        @Override public ValidationResult validate(ToolRequest request) { return validateTable(request); }
        @Override public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
            Pattern pattern = Pattern.compile(request.string("search"));
            Path output = FormatToolSupport.outputPath(request, context, "replaced.tsv");
            char delimiter = delimiter(request.path("input"), request.string("delimiter"));
            long[] totals = new long[3];
            int threads = resolveTableThreads(
                    request.integer("threads"), Files.size(request.path("input")),
                    TABLE_REPLACE_AUTO_BYTES);
            String replacement = request.string("replacement");
            try (DelimitedTableReader reader = new DelimitedTableReader(
                    request.path("input"), delimiter, true, true)) {
                List<String> headers = requireHeader(reader);
                int column = columnIndex(headers, request.string("column"));
                FormatToolSupport.writeAtomically(output, writer -> {
                    writeLine(writer, headers);
                    OrderedParallelProcessor.processChunks(
                            threads,
                            TABLE_PARALLEL_BATCH_ROWS,
                            context.cancellationToken(),
                            reader::nextRow,
                            chunk -> replaceChunk(chunk, column, pattern, replacement),
                            chunk -> {
                                totals[0] += chunk.inputRows();
                                totals[1] += chunk.outputRows();
                                totals[2] += chunk.replacements();
                                for (List<String> row : chunk.rows()) {
                                    writeLine(writer, row);
                                }
                            }
                    );
                });
            }
            context.progress(1.0, "Text replacement completed");
            ToolResult result = tableResult("table.replace", output, totals[0], totals[1]);
            result = withMetric(result, "replacements", totals[2]);
            result = withMetric(result, "threads", threads);
            return withMetric(result, "parallelRecords", threads > 1);
        }
    }

    private static ToolDescriptor descriptor(String id, String name, String description, List<ToolParameter> specific) {
        return descriptor(id, name, description, specific, "1.1.0");
    }

    private static ToolDescriptor descriptor(
            String id,
            String name,
            String description,
            List<ToolParameter> specific,
            String version
    ) {
        List<ToolParameter> parameters = new ArrayList<>(specific);
        parameters.add(new ToolParameter("delimiter", "Delimiter", "Delimited table input delimiter.", ParameterType.ENUM,
                false, "AUTO", List.of("AUTO", "TAB", "COMMA", "SEMICOLON"), null, null, false, null));
        parameters.add(ToolParameter.optionalOutputFile("output", "Output table", "Leave blank to save in the run directory."));
        parameters.add(ToolParameter.bool("overwrite", "Overwrite output", "Allow replacing an existing output.", false));
        return new ToolDescriptor(id, name, description, "Table",
                List.of(name.toLowerCase(Locale.ROOT), id), List.of("TSV", "CSV"),
                List.of("TSV"), version, parameters);
    }

    private static ValidationResult validateTable(ToolRequest request) {
        Path input = request.path("input") != null ? request.path("input") : request.path("left");
        return new ValidationResult(List.of()).merge(FormatToolSupport.validateOutput(request, input));
    }

    private static ToolResult tableResult(String prefix, Path output, long input, long outputRows) {
        return new ToolResult(
                "Processed " + input + " rows; wrote " + outputRows, List.of(input, outputRows),
                "Table operation completed.", List.of(),
                new ResultTable(List.of("Metric", "Value"), List.of(
                        List.of("Input rows", Long.toString(input)),
                        List.of("Output rows", Long.toString(outputRows)))),
                List.of(new OutputArtifact(output, "Processed table", "text/tab-separated-values")),
                new LinkedHashMap<>(Map.of("inputRows", input, "outputRows", outputRows))
        );
    }

    private static double parseBound(String value, double fallback) {
        return value == null || value.isBlank() ? fallback : Double.parseDouble(value);
    }

    static int resolveTableThreads(int requested, long inputBytes, long autoBytes) {
        return OrderedParallelProcessor.resolveThreads(requested, inputBytes, autoBytes);
    }

    private static FilterChunk filterChunk(
            List<List<String>> rows,
            int column,
            int numericColumn,
            Pattern regex,
            double minimum,
            double maximum,
            boolean invert
    ) {
        List<List<String>> retained = new ArrayList<>();
        for (List<String> row : rows) {
            String value = field(row, column);
            boolean match = regex == null || regex.matcher(value).find();
            if (!Double.isInfinite(minimum) || !Double.isInfinite(maximum)) {
                try {
                    double number = Double.parseDouble(field(row, numericColumn));
                    match &= number >= minimum && number <= maximum;
                } catch (NumberFormatException ex) {
                    match = false;
                }
            }
            if (invert) {
                match = !match;
            }
            if (match) {
                retained.add(row);
            }
        }
        return new FilterChunk(
                List.copyOf(retained),
                rows.size(),
                retained.size(),
                rows.size() - retained.size()
        );
    }

    private static ReplaceChunk replaceChunk(
            List<List<String>> rows,
            int column,
            Pattern pattern,
            String replacement
    ) {
        List<List<String>> replacedRows = new ArrayList<>(rows.size());
        long replacements = 0;
        for (List<String> row : rows) {
            if (column >= row.size()) {
                replacedRows.add(row);
                continue;
            }
            List<String> replaced = new ArrayList<>(row);
            String before = row.get(column);
            String after = pattern.matcher(before).replaceAll(replacement);
            if (!before.equals(after)) {
                replacements++;
            }
            replaced.set(column, after);
            replacedRows.add(List.copyOf(replaced));
        }
        return new ReplaceChunk(
                List.copyOf(replacedRows),
                rows.size(),
                replacedRows.size(),
                replacements
        );
    }

    private static ToolResult withMetric(ToolResult result, String key, Object value) {
        Map<String, Object> metrics = new LinkedHashMap<>(result.metrics());
        metrics.put(key, value);
        return new ToolResult(result.summary(), result.summaryArguments(),
                result.detail(), result.detailArguments(),
                result.table(), result.artifacts(), metrics);
    }

    private static char delimiter(Path input, String selected) throws Exception {
        return switch (selected) {
            case "COMMA" -> ',';
            case "SEMICOLON" -> ';';
            case "TAB" -> '\t';
            default -> DelimitedTableReader.detectDelimiter(input);
        };
    }

    private static List<String> requireHeader(DelimitedTableReader reader) throws Exception {
        List<String> header = reader.nextRow();
        if (header == null || header.stream().allMatch(String::isBlank)) {
            throw new OpenToolException("TABLE_EMPTY", "Table is empty.", "Provide a header.");
        }
        return List.copyOf(header);
    }

    private static String columnName(List<String> headers, int column) {
        if (column < headers.size() && !headers.get(column).isBlank()) {
            return headers.get(column);
        }
        return "column_" + (column + 1);
    }

    private static int columnIndex(List<String> headers, String name) {
        for (int index = 0; index < headers.size(); index++) {
            if (headers.get(index).equalsIgnoreCase(name)) {
                return index;
            }
        }
        throw new OpenToolException(
                "TABLE_COLUMN_MISSING", "Column not found: " + name,
                "Check the table header."
        );
    }

    private static List<Integer> keyIndexes(List<String> headers, String names) {
        List<Integer> result = new ArrayList<>();
        for (String name : splitNames(names)) {
            result.add(columnIndex(headers, name));
        }
        if (result.isEmpty()) {
            throw new OpenToolException(
                    "TABLE_KEY_REQUIRED", "At least one key column is required.",
                    "Enter one or more comma-separated column names."
            );
        }
        return result;
    }

    private static List<String> splitNames(String value) {
        List<String> names = new ArrayList<>();
        for (String name : value.split("[,;\\s]+")) {
            if (!name.isBlank()) {
                names.add(name);
            }
        }
        return names;
    }

    private static List<String> key(List<String> row, List<Integer> indexes) {
        return indexes.stream().map(index -> field(row, index)).toList();
    }

    private static String field(List<String> row, int index) {
        return index >= 0 && index < row.size() ? row.get(index) : "";
    }

    private static List<String> uniqueRightHeaders(
            List<String> left,
            List<String> right
    ) {
        Set<String> used = new LinkedHashSet<>(left);
        List<String> result = new ArrayList<>();
        for (String original : right) {
            String candidate = original;
            int suffix = 1;
            while (used.contains(candidate)) {
                candidate = original + "_right" + (suffix == 1 ? "" : "_" + suffix);
                suffix++;
            }
            used.add(candidate);
            result.add(candidate);
        }
        return result;
    }

    private static void writeJoined(
            BufferedWriter writer,
            List<String> left,
            List<String> right,
            int columnsOnMissingSide
    ) throws Exception {
        List<String> joined = new ArrayList<>();
        if (left != null) {
            joined.addAll(left);
        } else {
            for (int index = 0; index < columnsOnMissingSide; index++) {
                joined.add("");
            }
        }
        if (right != null) {
            joined.addAll(right);
        } else {
            for (int index = 0; index < columnsOnMissingSide; index++) {
                joined.add("");
            }
        }
        writeLine(writer, joined);
    }

    private static final class Aggregate {
        private double sum;
        private double minimum = Double.POSITIVE_INFINITY;
        private double maximum = Double.NEGATIVE_INFINITY;
        private long count;

        void add(double value) {
            sum += value;
            minimum = Math.min(minimum, value);
            maximum = Math.max(maximum, value);
            count++;
        }

        String format(String mode) {
            if ("COUNT".equals(mode)) {
                return Long.toString(count);
            }
            double value = switch (mode) {
                case "MEAN" -> sum / count;
                case "MIN" -> minimum;
                case "MAX" -> maximum;
                default -> sum;
            };
            return String.format(Locale.ROOT, "%.10g", value);
        }
    }

    private record IndexedRow(int index, List<String> row) {
    }

    private record FilterChunk(
            List<List<String>> rows,
            int inputRows,
            int retainedRows,
            int filteredRows
    ) {
    }

    private record ReplaceChunk(
            List<List<String>> rows,
            int inputRows,
            int outputRows,
            long replacements
    ) {
    }

    private record TransposeStats(long inputRows, int columns, long nonEmptyCells) {
    }

    static final class Table {
        private final List<String> headers;
        private final List<List<String>> rows;

        private Table(List<String> headers, List<List<String>> rows) {
            this.headers = List.copyOf(headers);
            this.rows = rows;
        }

        static Table read(Path input, char delimiter, int maxRows, int maxCells) throws Exception {
            List<String> headers;
            List<List<String>> rows = new ArrayList<>();
            try (DelimitedTableReader reader = new DelimitedTableReader(
                    input, delimiter, true, true)) {
                headers = requireHeader(reader);
                List<String> row;
                while ((row = reader.nextRow()) != null) {
                    if (rows.size() >= maxRows) {
                        throw new OpenToolException(
                                "TABLE_MEMORY_BOUND",
                                "Input rows exceed maxRows.",
                                "Raise the bound or partition the input."
                        );
                    }
                    int rowWidth = Math.max(headers.size(), row.size());
                    if ((long) (rows.size() + 1) * rowWidth > maxCells) {
                        throw new OpenToolException(
                                "TABLE_MEMORY_BOUND",
                                "Estimated cells exceed maxCells.",
                                "Raise the bound or partition the input."
                        );
                    }
                    rows.add(new ArrayList<>(row));
                }
            }
            return new Table(headers, rows);
        }

        static void writeAll(Path output, List<String> headers, List<List<String>> rows) throws Exception {
            FormatToolSupport.writeAtomically(output, writer -> {
                writeLine(writer, headers);
                for (List<String> row : rows) writeLine(writer, row);
            });
        }

    }

    private static void writeLine(BufferedWriter writer, List<String> fields) throws Exception {
        DelimitedTableWriter.writeRow(writer, fields, '\t');
    }
}
