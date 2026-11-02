package io.opentools.examples.plugin;

import io.opentools.api.tool.CancellationToken;
import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ToolResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CoinRunnerToolTest {
    @TempDir
    Path tempDir;

    @Test
    void writesDataAndGraphicArtifacts() throws Exception {
        Path output = Files.createDirectories(tempDir.resolve("outputs"));
        ToolExecutionContext context = new ToolExecutionContext(
                "run-1",
                tempDir,
                tempDir,
                output,
                ignored -> { },
                ignored -> { },
                new CancellationToken()
        );
        ToolRequest request = new ToolRequest(
                "plugin.example.coin-runner",
                Map.of(
                        "playerName", "Ada",
                        "distance", 420,
                        "score", 1250,
                        "coins", 8,
                        "durationMs", 3200,
                        "endedReason", "SECOND_CLICK"
                )
        );

        ToolResult result = new CoinRunnerTool().execute(request, context);

        assertTrue(Files.isRegularFile(output.resolve("coin-runner-result.json")));
        assertTrue(Files.isRegularFile(output.resolve("coin-runner-result.png")));
        var image = ImageIO.read(output.resolve("coin-runner-result.png").toFile());
        assertTrue(image.getWidth() >= 1000);
        assertEquals(1000L * image.getHeight(), 460L * image.getWidth());
        assertFalse(result.table().rows().isEmpty());
        assertTrue(result.metrics().containsKey("distance"));
    }
}
