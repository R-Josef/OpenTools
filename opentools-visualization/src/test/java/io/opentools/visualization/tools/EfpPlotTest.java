package io.opentools.visualization.tools;

import io.opentools.api.tool.CancellationToken;
import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ToolResult;
import io.opentools.core.tool.ToolRegistry;
import io.opentools.visualization.BuiltInVisualizations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EfpPlotTest {
    @TempDir
    Path tempDir;

    @Test
    void ordersSamplesByTissueAndDrawsGroupBands() throws Exception {
        Path matrix = tempDir.resolve("expression.tsv");
        Files.writeString(matrix, """
                gene	T1	T2	T3	T4
                g1	1	2	3	4
                g2	4	3	2	1
                """);
        Path sampleMap = tempDir.resolve("samples.tsv");
        Files.writeString(sampleMap, """
                sample	tissue
                T1	leaf
                T3	root
                T2	leaf
                T4	root
                """);
        Path output = tempDir.resolve("efp");
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("input", matrix);
        parameters.put("sampleMap", sampleMap);
        parameters.put("normalization", "NONE");
        parameters.put("exportData", true);
        parameters.put("exportFormats", "PNG_SVG");
        parameters.put("width", 800);
        parameters.put("height", 600);
        parameters.put("dpi", 96);
        parameters.put("output", output);

        ToolRegistry registry = new ToolRegistry();
        BuiltInVisualizations.register(registry);
        var tool = registry.find("plot.efp").orElseThrow();
        ToolRequest request = new ToolRequest("plot.efp", parameters);
        assertTrue(tool.validate(request).valid(), tool.validate(request).issues().toString());
        Path run = tempDir.resolve("run");
        ToolResult result = tool.execute(request, new ToolExecutionContext(
                "efp", tempDir, run, run, ignored -> { }, ignored -> { }, new CancellationToken()));

        assertEquals(2, result.metrics().get("sampleGroups"));
        assertEquals(4, result.metrics().get("samples"));
        assertTrue(Files.isRegularFile(tempDir.resolve("efp.png")));
        String processed = Files.readString(tempDir.resolve("efp.processed.tsv"));
        assertTrue(processed.startsWith("gene\tT1\tT2\tT3\tT4"), processed);
    }
}
