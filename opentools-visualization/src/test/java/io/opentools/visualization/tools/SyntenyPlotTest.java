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

final class SyntenyPlotTest {
    @TempDir
    Path tempDir;

    @Test
    void drawsLinksAcrossThreeGenomes() throws Exception {
        Path annotation = tempDir.resolve("combined.gff3");
        StringBuilder gff = new StringBuilder("##gff-version 3\n");
        for (String genome : new String[]{"A", "B", "C"}) {
            for (int i = 1; i <= 3; i++) {
                gff.append("chr").append(genome).append("\ttest\tgene\t")
                        .append(i * 1000).append("\t").append(i * 1000 + 500)
                        .append("\t.\t+\t.\tID=g").append(genome).append(i).append("\n");
            }
        }
        Files.writeString(annotation, gff.toString());
        Path pairs = tempDir.resolve("pairs.tsv");
        Files.writeString(pairs, """
                block	genome_a	gene_a	genome_b	gene_b
                1	A	gA1	B	gB1
                1	A	gA2	B	gB2
                2	A	gA3	C	gC3
                3	B	gB1	C	gC1
                """);
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("input", pairs);
        parameters.put("annotation", annotation);
        parameters.put("blockColumn", "block");
        parameters.put("genomeAColumn", "genome_a");
        parameters.put("geneAColumn", "gene_a");
        parameters.put("genomeBColumn", "genome_b");
        parameters.put("geneBColumn", "gene_b");
        parameters.put("maxLinks", 5000);
        parameters.put("width", 800);
        parameters.put("height", 800);
        parameters.put("dpi", 96);
        parameters.put("exportFormats", "PNG_SVG");
        parameters.put("output", tempDir.resolve("synteny"));

        ToolRegistry registry = new ToolRegistry();
        BuiltInVisualizations.register(registry);
        var tool = registry.find("plot.synteny").orElseThrow();
        ToolRequest request = new ToolRequest("plot.synteny", parameters);
        var validation = tool.validate(request);
        assertTrue(validation.valid(), validation.issues().toString());
        Path run = tempDir.resolve("run");
        ToolResult result = tool.execute(request, new ToolExecutionContext(
                "synteny", tempDir, run, run, ignored -> { }, ignored -> { }, new CancellationToken()));

        assertEquals(3, result.metrics().get("genomes"));
        assertEquals(4, result.metrics().get("links"));
        assertEquals(3, result.metrics().get("blocks"));
        assertTrue(Files.isRegularFile(tempDir.resolve("synteny.png")));
    }
}
