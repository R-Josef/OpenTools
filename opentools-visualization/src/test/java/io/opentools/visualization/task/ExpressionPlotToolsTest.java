package io.opentools.visualization.task;

import io.opentools.api.tool.CancellationToken;
import io.opentools.api.tool.OpenTool;
import io.opentools.api.tool.TaskContext;
import io.opentools.api.tool.TaskResult;
import io.opentools.api.tool.ToolParameter;
import io.opentools.core.tool.ToolRegistry;
import io.opentools.api.tool.ToolRequest;
import io.opentools.visualization.BuiltInVisualizations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ExpressionPlotToolsTest {
    @TempDir
    Path tempDir;

    @Test
    void rendersPcaVolcanoBoxViolinBarAndCorrelation() throws Exception {
        Path pca = tempDir.resolve("pca.tsv");
        Files.writeString(pca, """
                sample	group	PC1	PC2	variance_explained
                S1	A	-1.2	0.2	60
                S2	A	-0.8	-0.1	60
                S3	B	1.0	0.3	60
                """);
        TaskResult pcaResult = run("plot.pca", Map.of(
                "input", pca,
                "output", tempDir.resolve("pca-plot")
        ));
        assertEquals(2, pcaResult.metrics().get("series"));
        assertTrue(Files.size(tempDir.resolve("pca-plot.png")) > 0);

        Path volcano = tempDir.resolve("volcano.tsv");
        Files.writeString(volcano, """
                gene	log2FoldChange	padj
                g1	2.0	0.001
                g2	-2.5	0.01
                g3	0.2	0.5
                """);
        run("plot.volcano", Map.of(
                "input", volcano,
                "output", tempDir.resolve("volcano-plot")
        ));
        assertTrue(Files.isRegularFile(tempDir.resolve("volcano-plot.processed.tsv")));

        Path tidy = tempDir.resolve("tidy.tsv");
        Files.writeString(tidy, """
                gene	sample	group	value
                g1	S1	A	1.0
                g1	S2	A	1.2
                g2	S3	B	2.0
                g2	S4	B	2.4
                """);
        run("plot.box", Map.of("input", tidy, "output", tempDir.resolve("box-plot")));
        run("plot.violin", Map.of("input", tidy, "output", tempDir.resolve("violin-plot")));
        assertTrue(Files.isRegularFile(tempDir.resolve("box-plot.png")));
        assertTrue(Files.isRegularFile(tempDir.resolve("violin-plot.png")));

        Path bars = tempDir.resolve("bars.tsv");
        Files.writeString(bars, "category\tvalue\nA\t3\nB\t5\nC\t2\n");
        run("plot.bar", Map.of("input", bars, "output", tempDir.resolve("bar-plot")));
        assertTrue(Files.isRegularFile(tempDir.resolve("bar-plot.png")));

        Path correlation = tempDir.resolve("correlation.tsv");
        Files.writeString(correlation, "sample\tS1\tS2\nS1\t1\t0.8\nS2\t0.8\t1\n");
        run("plot.correlation", Map.of(
                "input", correlation,
                "output", tempDir.resolve("correlation-plot")
        ));
        assertTrue(Files.isRegularFile(tempDir.resolve("correlation-plot.png")));
    }

    @Test
    void rendersEnrichmentBarAndBubblePlots() throws Exception {
        Path enrichment = tempDir.resolve("enrichment.tsv");
        Files.writeString(enrichment, """
                term	hits	term_size	background_size	target_size	p_value	adjusted_p_value	fold_enrichment	genes
                GO:1	8	20	100	15	0.001	0.01	2.7	g1,g2,g3
                GO:2	3	10	100	15	0.02	0.08	2.0	g1,g4
                GO:3	2	30	100	15	0.10	0.20	0.4	g5,g6
                """);
        run("plot.enrichment-bar", Map.of(
                "input", enrichment,
                "output", tempDir.resolve("enrichment-bar")
        ));
        run("plot.enrichment-bubble", Map.of(
                "input", enrichment,
                "output", tempDir.resolve("enrichment-bubble")
        ));
        assertTrue(Files.isRegularFile(tempDir.resolve("enrichment-bar.png")));
        assertTrue(Files.isRegularFile(tempDir.resolve("enrichment-bubble.png")));
    }

    @Test
    void rendersTreeCollinearityAndDotPlot() throws Exception {
        Path tree = tempDir.resolve("tree.nwk");
        Files.writeString(tree, "((A:1,B:1):1,C:2)Root;\n");
        run("tree.render", Map.of("input", tree, "output", tempDir.resolve("tree-plot")));
        assertTrue(Files.isRegularFile(tempDir.resolve("tree-plot.png")));

        Path collinearity = tempDir.resolve("collinearity.tsv");
        Files.writeString(collinearity, """
                block	block_score	gene_a	gene_b	pair_index
                1	100	geneA	geneX	0
                1	100	geneB	geneY	1
                2	80	geneC	geneZ	0
                """);
        run("plot.collinearity", Map.of(
                "input", collinearity, "output", tempDir.resolve("collinearity-plot")));
        assertTrue(Files.isRegularFile(tempDir.resolve("collinearity-plot.png")));

        Path points = tempDir.resolve("points.tsv");
        Files.writeString(points, "x\ty\tgroup\n1\t2\tA\n2\t3\tA\n4\t1\tB\n");
        run("plot.dotplot", Map.of(
                "input", points, "groupColumn", "group", "output", tempDir.resolve("dotplot")));
        assertTrue(Files.isRegularFile(tempDir.resolve("dotplot.png")));
    }

    @Test
    void rendersAdvancedVisualizations() throws Exception {
        Path logo = tempDir.resolve("logo.tsv");
        Files.writeString(logo, """
                position	A	C	G	T
                1	0.8	0.1	0.05	0.05
                2	0.1	0.7	0.1	0.1
                """);
        run("plot.seqlogo", Map.of("input", logo, "output", tempDir.resolve("logo")));
        assertTrue(Files.isRegularFile(tempDir.resolve("logo.png")));

        Path gff = tempDir.resolve("genes.gff3");
        Files.writeString(gff, """
                ##gff-version 3
                chr1	test	gene	1	10	.	+	.	ID=g1
                chr1	test	gene	20	40	.	-	.	ID=g2
                """);
        run("plot.gene-location", Map.of("input", gff, "output", tempDir.resolve("genes")));
        assertTrue(Files.isRegularFile(tempDir.resolve("genes.png")));

        Path domains = tempDir.resolve("domains.tsv");
        Files.writeString(domains, "protein\tdomain\tstart\tend\np1\tKinase\t5\t30\np2\tRRM\t10\t25\n");
        run("plot.domain", Map.of("input", domains, "output", tempDir.resolve("domains")));
        assertTrue(Files.isRegularFile(tempDir.resolve("domains.png")));

        Path motifs = tempDir.resolve("motifs.tsv");
        Files.writeString(motifs, "seq_id\tmotif\tstart\ns1\tM1\t10\ns1\tM2\t30\n");
        run("plot.motif", Map.of("input", motifs, "output", tempDir.resolve("motifs")));
        assertTrue(Files.isRegularFile(tempDir.resolve("motifs.png")));

        Path circos = tempDir.resolve("circos.tsv");
        Files.writeString(circos, "seq_id\tstart\tend\tlabel\nchr1\t1\t100\tg1\nchr2\t10\t80\tg2\n");
        run("plot.circos", Map.of("input", circos, "output", tempDir.resolve("circos")));
        assertTrue(Files.isRegularFile(tempDir.resolve("circos.png")));

        Path expression = tempDir.resolve("efp.tsv");
        Files.writeString(expression, "gene\troot\tleaf\tflower\ng1\t1\t4\t2\ng2\t2\t3\t1\n");
        run("plot.efp", Map.of("input", expression, "output", tempDir.resolve("efp")));
        assertTrue(Files.isRegularFile(tempDir.resolve("efp.png")));
    }

    private TaskResult run(String id, Map<String, Object> supplied) throws Exception {
        ToolRegistry registry = new ToolRegistry();
        BuiltInVisualizations.register(registry);
        OpenTool tool = registry.find(id).orElseThrow();
        Map<String, Object> parameters = new LinkedHashMap<>();
        for (ToolParameter parameter : tool.descriptor().parameters()) {
            if (parameter.defaultValue() != null) {
                parameters.put(parameter.id(), parameter.defaultValue());
            }
        }
        parameters.put("exportFormats", "PNG");
        parameters.putAll(supplied);
        ToolRequest request = new ToolRequest(id, parameters);
        var validation = tool.validate(request);
        assertTrue(validation.valid(), validation.issues().toString());
        Path output = Files.createDirectories(tempDir.resolve("out"));
        return tool.execute(request, new TaskContext("plots", tempDir, output, output,
                ignored -> { }, ignored -> { }, new CancellationToken()));
    }
}
