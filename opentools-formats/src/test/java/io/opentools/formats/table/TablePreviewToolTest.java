package io.opentools.formats.table;

import io.opentools.api.tool.CancellationToken;
import io.opentools.api.tool.TaskContext;
import io.opentools.api.tool.ToolRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TablePreviewToolTest {
    @TempDir
    Path tempDir;

    @Test
    void previewsQuotedCsvWithoutLoadingAllRows() throws Exception {
        Path input = tempDir.resolve("values.csv");
        Path output = tempDir.resolve("preview.tsv");
        Files.writeString(input, "gene,value,note\ng1,1,\"a,b\"\ng2,2,ok\n");
        TaskContext context = new TaskContext(
                "run",
                tempDir,
                tempDir.resolve("run"),
                tempDir.resolve("run"),
                ignored -> { },
                ignored -> { },
                new CancellationToken()
        );

        var result = new TablePreviewTool().execute(
                new ToolRequest("table.preview", Map.of(
                        "input", input.toString(),
                        "output", output.toString(),
                        "delimiter", "AUTO",
                        "firstRowHeader", true,
                        "previewRows", 1,
                        "maxColumns", 100,
                        "countRows", true
                )),
                context
        );

        assertEquals(2L, result.metrics().get("rows"));
        assertEquals(1, result.table().rows().size());
        assertEquals("a,b", result.table().rows().getFirst().get(2));
    }

    @Test
    void previewsMultilineQuotedFieldAsOneLogicalRow() throws Exception {
        Path input = tempDir.resolve("multiline.tsv");
        Path output = tempDir.resolve("multiline-preview.tsv");
        Files.writeString(input, "id\tnote\tvalue\ng1\t\"first line\nsecond line\"\t7\n");
        TaskContext context = new TaskContext(
                "run",
                tempDir,
                tempDir.resolve("run"),
                tempDir.resolve("run"),
                ignored -> { },
                ignored -> { },
                new CancellationToken()
        );

        var result = new TablePreviewTool().execute(
                new ToolRequest("table.preview", Map.of(
                        "input", input.toString(),
                        "output", output.toString(),
                        "delimiter", "TAB",
                        "firstRowHeader", true,
                        "previewRows", 10,
                        "maxColumns", 100,
                        "countRows", true
                )),
                context
        );

        assertEquals(1L, result.metrics().get("rows"));
        assertEquals("first line\nsecond line", result.table().rows().getFirst().get(1));
        assertTrue(Files.readString(output).contains("\"first line\nsecond line\""));
    }
}
