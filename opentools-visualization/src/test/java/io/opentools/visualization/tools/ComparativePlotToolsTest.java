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

final class ComparativePlotToolsTest {
    @TempDir
    Path tempDir;

    @Test
    void plotsCollinearityByGeneOrder() throws Exception {
        Path annotationA = tempDir.resolve("a.gff3");
        Files.writeString(annotationA, """
                ##gff-version 3
                chr1	test	gene	1	100	.	+	.	ID=gene:gA1
                chr1	test	gene	200	300	.	+	.	ID=gene:gA2
                chr2	test	gene	1	100	.	+	.	ID=gene:gA3
                """);
        Path annotationB = tempDir.resolve("b.gff3");
        Files.writeString(annotationB, """
                ##gff-version 3
                chr1	test	gene	1	100	.	+	.	ID=gene:gB1
                chr1	test	gene	200	300	.	+	.	ID=gene:gB2
                chr2	test	gene	1	100	.	+	.	ID=gene:gB3
                """);
        Path pairs = tempDir.resolve("collinearity.tsv");
        Files.writeString(pairs, """
                block	block_score	gene_a	gene_b	pair_index
                1	100	gA1	gB1	1
                1	100	gA2	gB2	2
                2	50	gA3	gB3	1
                2	50	gA3	missing	2
                """);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("input", pairs);
        parameters.put("blockColumn", "block");
        parameters.put("geneAColumn", "gene_a");
        parameters.put("geneBColumn", "gene_b");
        parameters.put("maxBlocks", 20);
        parameters.put("annotationA", annotationA);
        parameters.put("annotationB", annotationB);
        parameters.put("output", tempDir.resolve("collinearity"));
        ToolResult result = run("plot.collinearity", parameters);

        assertEquals(3, result.metrics().get("pairs"));
        assertEquals(1, result.metrics().get("unmapped"));
        assertEquals(3, result.metrics().get("genesA"));
        assertEquals(3, result.metrics().get("genesB"));
        assertEquals(2, result.metrics().get("chromosomesA"));
        assertTrue(Files.size(tempDir.resolve("collinearity.png")) > 0);
    }

    private ToolResult run(String id, Map<String, Object> supplied) throws Exception {
        ToolRegistry registry = new ToolRegistry();
        BuiltInVisualizations.register(registry);
        var tool = registry.find(id).orElseThrow();
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("width", 800);
        parameters.put("height", 600);
        parameters.put("dpi", 96);
        parameters.putAll(supplied);
        ToolRequest request = new ToolRequest(id, parameters);
        var validation = tool.validate(request);
        assertTrue(validation.valid(), validation.issues().toString());
        Path output = Files.createDirectories(tempDir.resolve("out"));
        return tool.execute(request, new ToolExecutionContext("collinearity", tempDir, output, output,
                ignored -> { }, ignored -> { }, new CancellationToken()));
    }
}
