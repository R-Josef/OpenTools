package io.opentools.analysis.table;

import io.opentools.analysis.BuiltInAnalysis;
import io.opentools.api.tool.CancellationToken;
import io.opentools.api.tool.OpenTool;
import io.opentools.api.tool.OpenToolException;
import io.opentools.api.tool.TaskContext;
import io.opentools.api.tool.TaskResult;
import io.opentools.api.tool.ToolParameter;
import io.opentools.core.tool.ToolRegistry;
import io.opentools.api.tool.ToolRequest;
import io.opentools.formats.table.DelimitedTableReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TableToolsTest {
    @TempDir
    Path tempDir;

    @Test
    void filtersSelectsReplacesAndDeduplicates() throws Exception {
        Path input = tempDir.resolve("data.tsv");
        Files.writeString(input, """
                id	group	value
                a	A	10
                b	B	20
                c	A	30
                d	A	40
                """);
        Path filtered = tempDir.resolve("filtered.tsv");
        TaskResult filter = run("table.filter", Map.of(
                "input", input, "column", "group", "regex", "^A$", "numericColumn", "value",
                "numericMin", "15", "output", filtered));
        assertEquals(2L, filter.metrics().get("outputRows"));

        Path selected = tempDir.resolve("selected.tsv");
        run("table.select-columns", Map.of(
                "input", filtered, "columns", "id,value", "output", selected));
        assertTrue(Files.readAllLines(selected).getFirst().equals("id\tvalue"));

        Path replaced = tempDir.resolve("replaced.tsv");
        TaskResult replace = run("table.replace", Map.of(
                "input", selected, "column", "id", "search", "^",
                "replacement", "gene_", "output", replaced));
        assertEquals(2L, replace.metrics().get("replacements"));
        assertTrue(Files.readString(replaced).contains("gene_c"));

        Path dedupInput = tempDir.resolve("dedup.tsv");
        Files.writeString(dedupInput, "id\tvalue\na\t1\na\t2\nb\t3\n");
        Path dedup = tempDir.resolve("dedup-out.tsv");
        TaskResult dedupResult = run("table.deduplicate", Map.of(
                "input", dedupInput, "keyColumns", "id", "output", dedup));
        assertEquals(1L, dedupResult.metrics().get("duplicates"));
    }

    @Test
    void joinsTransposesAndPivotsTables() throws Exception {
        Path left = tempDir.resolve("left.tsv");
        Files.writeString(left, "id\tgroup\na\tA\nb\tB\nc\tA\n");
        Path right = tempDir.resolve("right.tsv");
        Files.writeString(right, "id\tvalue\na\t1\na\t2\nb\t3\n");
        Path joined = tempDir.resolve("joined.tsv");
        TaskResult join = run("table.join", Map.of(
                "left", left, "right", right, "leftKey", "id", "rightKey", "id",
                "joinType", "LEFT", "output", joined));
        assertEquals(4L, join.metrics().get("outputRows"));
        assertTrue(Files.readString(joined).contains("c\tA\t\t"));

        Path transpose = tempDir.resolve("transpose.tsv");
        run("table.transpose", Map.of("input", left, "output", transpose));
        assertTrue(Files.readAllLines(transpose).getFirst().startsWith("id\ta\tb\tc"));

        Path longTable = tempDir.resolve("long.tsv");
        Files.writeString(longTable, """
                gene	sample	value
                g1	S1	1
                g1	S2	2
                g2	S1	3
                g2	S1	4
                """);
        Path pivot = tempDir.resolve("pivot.tsv");
        TaskResult pivotResult = run("table.pivot", Map.of(
                "input", longTable, "rowColumn", "gene", "columnColumn", "sample",
                "valueColumn", "value", "aggregation", "MEAN", "output", pivot));
        assertEquals(2L, pivotResult.metrics().get("outputRows"));
        assertTrue(Files.readString(pivot).contains("g2\t3.5"));
    }

    @Test
    void streamsQuotedMultilineRowsAndSupportsCompoundFullJoin() throws Exception {
        Path quoted = tempDir.resolve("quoted.tsv");
        Files.writeString(quoted, """
                id	group	value
                a	A	10
                b	"A
                B"	20
                """);
        Path filtered = tempDir.resolve("quoted-filtered.tsv");
        TaskResult filter = run("table.filter", Map.of(
                "input", quoted,
                "column", "group",
                "regex", "B$",
                "output", filtered
        ));
        assertEquals(1L, filter.metrics().get("outputRows"));
        assertTrue(Files.readString(filtered).contains("\"A\nB\""));

        Path left = tempDir.resolve("compound-left.tsv");
        Files.writeString(left, "id\tgroup\tvalue\na\tA\t1\nb\tB\t2\n");
        Path right = tempDir.resolve("compound-right.tsv");
        Files.writeString(right, "id\tgroup\tlabel\na\tA\tx\nc\tC\ty\n");
        Path joined = tempDir.resolve("compound-full.tsv");
        TaskResult join = run("table.join", Map.of(
                "left", left,
                "right", right,
                "leftKey", "id,group",
                "rightKey", "id,group",
                "joinType", "FULL",
                "output", joined
        ));
        assertEquals(3L, join.metrics().get("outputRows"));
        assertEquals(1L, join.metrics().get("unmatchedLeftRows"));
        assertEquals(1L, join.metrics().get("unmatchedRightRows"));
        assertTrue(Files.readString(joined).contains("\t\t\tc\tC\ty"));
    }

    @Test
    void parallelFilterSelectAndReplacePreserveQuotedRecordOrder() throws Exception {
        Path input = tempDir.resolve("parallel-quoted.tsv");
        StringBuilder content = new StringBuilder("id\tgroup\tvalue\tnote\r\n");
        for (int index = 0; index < 300; index++) {
            String group = index % 17 == 0 ? "\"A\r\nB\"" : index % 2 == 0 ? "A" : "B";
            String note = index % 11 == 0
                    ? "\"line one\r\nline \"\"two\"\"\tend\""
                    : "\"quoted\tvalue " + index + "\"";
            content.append("r").append(index).append('\t')
                    .append(group).append('\t')
                    .append(index + 10).append('\t')
                    .append(note).append("\r\n");
        }
        Files.writeString(input, content);

        for (String id : List.of("table.filter", "table.select-columns", "table.replace")) {
            Path sequential = tempDir.resolve(id + "-sequential.tsv");
            Path parallel = tempDir.resolve(id + "-parallel.tsv");
            Map<String, Object> parameters = new LinkedHashMap<>();
            parameters.put("input", input);
            if ("table.filter".equals(id)) {
                parameters.put("column", "group");
                parameters.put("regex", "A");
                parameters.put("numericColumn", "value");
                parameters.put("numericMin", "10");
                parameters.put("numericMax", "10000");
            } else if ("table.select-columns".equals(id)) {
                parameters.put("columns", "note,id,group,value");
            } else {
                parameters.put("column", "id");
                parameters.put("search", "^r(\\d+)");
                parameters.put("replacement", "sample_$1");
            }
            parameters.put("threads", 1);
            parameters.put("output", sequential);
            run(id, parameters);
            parameters.put("threads", 3);
            parameters.put("output", parallel);
            TaskResult result = run(id, parameters);

            assertEquals(Files.readString(sequential), Files.readString(parallel));
            assertEquals(3, result.metrics().get("threads"));
            assertEquals(true, result.metrics().get("parallelRecords"));
        }

        Path selected = tempDir.resolve("table.select-columns-parallel.tsv");
        try (DelimitedTableReader reader = new DelimitedTableReader(selected, '\t')) {
            assertEquals(List.of("note", "id", "group", "value"), reader.nextRow());
            List<String> row = reader.nextRow();
            assertEquals("line one\nline \"two\"\tend", row.getFirst());
        }

        Path replaced = tempDir.resolve("table.replace-parallel.tsv");
        assertTrue(Files.readString(replaced).contains("sample_0\t"));
        assertTrue(Files.readString(replaced).contains("sample_299\t"));
    }

    @Test
    void tableParallelSchemaAndCancellationKeepAtomicBoundary() throws Exception {
        Path input = tempDir.resolve("cancel-table.tsv");
        Files.writeString(input, "id\tgroup\nr1\tA\nr2\tB\n");
        for (String id : List.of("table.filter", "table.select-columns", "table.replace")) {
            OpenTool tool = TableTools.tools().stream()
                    .filter(candidate -> candidate.descriptor().id().equals(id))
                    .findFirst()
                    .orElseThrow();
            ToolParameter threads = tool.descriptor().parameters().stream()
                    .filter(parameter -> parameter.id().equals("threads"))
                    .findFirst()
                    .orElseThrow();
            assertEquals(0, threads.defaultValue());
            assertEquals(0, threads.min());
            assertEquals(64, threads.max());
        }
        assertEquals(1, TableTools.resolveTableThreads(
                0,
                TableTools.TABLE_FILTER_AUTO_BYTES - 1,
                TableTools.TABLE_FILTER_AUTO_BYTES));
        assertEquals(1, TableTools.resolveTableThreads(
                0,
                4L * 1024 * 1024 * 1024,
                TableTools.TABLE_SELECT_AUTO_BYTES));
        assertEquals(4, TableTools.resolveTableThreads(
                4,
                1,
                TableTools.TABLE_FILTER_AUTO_BYTES));

        Path output = tempDir.resolve("cancelled-table.tsv");
        CancellationToken cancellation = new CancellationToken();
        cancellation.cancel();
        assertThrows(java.util.concurrent.CancellationException.class, () -> run(
                "table.filter",
                Map.of(
                        "input", input,
                        "column", "group",
                        "regex", "A",
                        "threads", 2,
                        "output", output
                ),
                cancellation
        ));
        assertFalse(Files.exists(output));
        try (var files = Files.list(output.toAbsolutePath().getParent())) {
            assertTrue(files.noneMatch(path ->
                    path.getFileName().toString().startsWith(".opentools-")));
        }
    }

    @Test
    void malformedMultilineRecordFailsWithoutPublishingPartialParallelOutput() throws Exception {
        Path input = tempDir.resolve("malformed-multiline.tsv");
        Files.writeString(input, """
                id	group	value
                r1	A	1
                r2	"unterminated
                value	2
                """);
        Path output = tempDir.resolve("malformed-output.tsv");

        OpenToolException exception = assertThrows(OpenToolException.class, () -> run(
                "table.filter",
                Map.of(
                        "input", input,
                        "column", "group",
                        "regex", "A",
                        "threads", 3,
                        "output", output
                )
        ));

        assertEquals("DELIMITED_TABLE_MALFORMED", exception.code());
        assertFalse(Files.exists(output));
        Path outputDirectory = output.toAbsolutePath().getParent();
        try (var files = Files.list(outputDirectory)) {
            assertTrue(files.noneMatch(path ->
                    path.getFileName().toString().startsWith(".opentools-")));
        }
    }

    @Test
    void enforcesExplicitTableMemoryBound() throws Exception {
        Path input = tempDir.resolve("bounded.tsv");
        Files.writeString(input, "a\tb\n1\t2\n3\t4\n");
        OpenToolException exception = assertThrows(OpenToolException.class, () -> run(
                "table.transpose",
                Map.of("input", input, "transposeMode", "IN_MEMORY", "maxCells", 2)
        ));
        assertEquals("TABLE_MEMORY_BOUND", exception.code());
    }

    @Test
    void autoTransposeFallsBackToDiskWhenMemoryBoundIsReached() throws Exception {
        Path input = tempDir.resolve("auto-spill.tsv");
        Files.writeString(input, "a\tb\n1\t2\n3\t4\n");
        Path output = tempDir.resolve("auto-spill-output.tsv");

        TaskResult result = run("table.transpose", Map.of(
                "input", input,
                "transposeMode", "AUTO",
                "maxCells", 2,
                "output", output
        ));

        assertEquals("DISK", result.metrics().get("transposeMode"));
        assertTrue(Files.size(output) > 0);
    }

    @Test
    void transposesRaggedQuotedTableThroughDiskMode() throws Exception {
        Path input = tempDir.resolve("disk-transpose.tsv");
        Files.writeString(input, "a\tb\n\"x\ny\"\t1\nz\t2\t3\n");
        Path output = tempDir.resolve("disk-transposed.tsv");

        TaskResult result = run("table.transpose", Map.of(
                "input", input,
                "transposeMode", "DISK",
                "output", output
        ));

        assertEquals("DISK", result.metrics().get("transposeMode"));
        assertEquals(3L, result.metrics().get("outputRows"));
        assertEquals(5L, result.metrics().get("spilledCells"));
        try (DelimitedTableReader reader = new DelimitedTableReader(output, '\t')) {
            assertEquals(java.util.List.of("a", "x\ny", "z"), reader.nextRow());
            assertEquals(java.util.List.of("b", "1", "2"), reader.nextRow());
            assertEquals(java.util.List.of("column_3", "", "3"), reader.nextRow());
        }
        Path temp = tempDir.resolve("out/tmp");
        if (Files.isDirectory(temp)) {
            try (var files = Files.list(temp)) {
                assertTrue(files.noneMatch(path -> path.getFileName().toString().endsWith(".sqlite")));
            }
        }
    }

    @Test
    void diskTransposeEnforcesTemporaryDatabaseLimitWithoutPublishingOutput() throws Exception {
        Path input = tempDir.resolve("large-spill.tsv");
        try (BufferedWriter writer = Files.newBufferedWriter(input)) {
            writer.write("id\tgroup\tvalue\tnote\n");
            String payload = "x".repeat(96);
            for (int index = 0; index < 20_000; index++) {
                writer.write(index + "\tgroup_" + (index % 50) + "\t"
                        + index + "\t" + payload + "\n");
            }
        }
        Path output = tempDir.resolve("bounded-spill-output.tsv");

        OpenToolException exception = assertThrows(OpenToolException.class, () -> run(
                "table.transpose",
                Map.of(
                        "input", input,
                        "transposeMode", "DISK",
                        "maxTempMegabytes", 1,
                        "output", output
                )
        ));

        assertEquals("SPILL_SPACE_LIMIT", exception.code());
        assertFalse(Files.exists(output));
    }

    private TaskResult run(String id, Map<String, Object> supplied) throws Exception {
        return run(id, supplied, new CancellationToken());
    }

    private TaskResult run(
            String id,
            Map<String, Object> supplied,
            CancellationToken cancellationToken
    ) throws Exception {
        ToolRegistry registry = new ToolRegistry();
        BuiltInAnalysis.register(registry);
        OpenTool tool = registry.find(id).orElseThrow();
        Map<String, Object> parameters = new LinkedHashMap<>();
        for (ToolParameter parameter : tool.descriptor().parameters()) {
            if (parameter.defaultValue() != null) parameters.put(parameter.id(), parameter.defaultValue());
        }
        parameters.putAll(supplied);
        ToolRequest request = new ToolRequest(id, parameters);
        var validation = tool.validate(request);
        assertTrue(validation.valid(), validation.issues().toString());
        Path output = Files.createDirectories(tempDir.resolve("out"));
        return tool.execute(request, new TaskContext("tables", tempDir, output, output,
                ignored -> { }, ignored -> { }, cancellationToken));
    }
}
