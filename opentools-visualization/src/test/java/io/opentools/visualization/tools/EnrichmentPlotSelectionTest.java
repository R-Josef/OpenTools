package io.opentools.visualization.tools;

import io.opentools.api.tool.CancellationToken;
import io.opentools.api.tool.OpenTool;
import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolResult;
import io.opentools.api.tool.ToolParameter;
import io.opentools.core.tool.ToolRegistry;
import io.opentools.api.tool.ToolRequest;
import io.opentools.visualization.BuiltInVisualizations;
import io.opentools.visualization.io.PlotJson;
import io.opentools.visualization.model.GenericPlotData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EnrichmentPlotSelectionTest {
    @TempDir
    Path tempDir;

    @Test
    void selectsMostSignificantRowsAndReadsQuotedCsv() throws Exception {
        Path input = tempDir.resolve("enrichment.csv");
        Files.writeString(input, """
                term,adjusted_p_value
                "low
                term",0.5
                high,0.001
                """);
        Path output = tempDir.resolve("enrichment-plot");
        ToolResult result = run("plot.enrichment-bar", Map.of(
                "input", input,
                "maxTerms", 1,
                "selectionMode", "TOP_SIGNIFICANT",
                "exportFormats", "PNG",
                "output", output
        ));

        assertEquals("TOP_SIGNIFICANT", result.metrics().get("selectionMode"));
        assertEquals(2, result.metrics().get("inputRows"));
        assertEquals(1, result.metrics().get("selectedRows"));
        var model = PlotJson.readModel(Path.of(output + ".plot.json"));
        GenericPlotData data = (GenericPlotData) model.spec().data();
        assertEquals(java.util.List.of("high"), data.categories());
        assertTrue(Files.isRegularFile(Path.of(output + ".png")));
    }

    private ToolResult run(String id, Map<String, Object> supplied) throws Exception {
        ToolRegistry registry = new ToolRegistry();
        BuiltInVisualizations.register(registry);
        OpenTool tool = registry.find(id).orElseThrow();
        Map<String, Object> parameters = new LinkedHashMap<>();
        for (ToolParameter parameter : tool.descriptor().parameters()) {
            if (parameter.defaultValue() != null) {
                parameters.put(parameter.id(), parameter.defaultValue());
            }
        }
        parameters.putAll(supplied);
        ToolRequest request = new ToolRequest(id, parameters);
        var validation = tool.validate(request);
        assertTrue(validation.valid(), validation.issues().toString());
        Path run = tempDir.resolve("run-" + System.nanoTime());
        return tool.execute(request, new ToolExecutionContext(
                "enrichment-selection",
                tempDir,
                run,
                run.resolve("outputs"),
                ignored -> { },
                ignored -> { },
                new CancellationToken()
        ));
    }
}
