package io.opentools.formats.bed;

import io.opentools.api.tool.CancellationToken;
import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ToolResult;
import io.opentools.core.tool.ToolRegistry;
import io.opentools.formats.BuiltInTools;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BedBlocksToolTest {
    @TempDir
    Path tempDir;

    @Test
    void emitsBlocksAndIntronsWithValidatedCoordinates() throws Exception {
        Path input = tempDir.resolve("blocks.bed");
        Files.writeString(input, "chr1\t100\t200\texon1\t0\t+\t100\t200\t0\t2\t20,30,\t0,70,\n");
        Path output = tempDir.resolve("blocks.tsv");
        ToolRegistry registry = new ToolRegistry();
        BuiltInTools.register(registry);
        ToolRequest request = new ToolRequest("bed.blocks", Map.of(
                "input", input,
                "output", output
        ));
        ToolResult result = registry.find("bed.blocks").orElseThrow().execute(request, context());

        assertEquals(2L, ((Number) result.metrics().get("blocks")).longValue());
        assertEquals(1L, ((Number) result.metrics().get("introns")).longValue());
        String text = Files.readString(output);
        assertTrue(text.contains("BLOCK\t1\t100\t120\t20"));
        assertTrue(text.contains("INTRON\t1\t120\t170\t50"));
        assertTrue(text.contains("BLOCK\t2\t170\t200\t30"));
    }

    @Test
    void rejectsNonBed12Rows() throws Exception {
        Path input = tempDir.resolve("six.bed");
        Files.writeString(input, "chr1\t1\t10\tx\t0\t+\n");
        ToolRegistry registry = new ToolRegistry();
        BuiltInTools.register(registry);
        ToolRequest request = new ToolRequest("bed.blocks", Map.of("input", input));
        var error = org.junit.jupiter.api.Assertions.assertThrows(
                io.opentools.api.tool.OpenToolException.class,
                () -> registry.find("bed.blocks").orElseThrow().execute(request, context()));
        assertEquals("BED12_FORMAT_ERROR", error.code());
    }

    private ToolExecutionContext context() throws Exception {
        Path output = Files.createDirectories(tempDir.resolve("out"));
        return new ToolExecutionContext("bed-blocks", tempDir, output, output,
                ignored -> { }, ignored -> { }, new CancellationToken());
    }
}
