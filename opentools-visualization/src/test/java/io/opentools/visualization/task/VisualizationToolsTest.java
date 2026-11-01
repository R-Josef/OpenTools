package io.opentools.visualization.task;

import io.opentools.api.tool.CancellationToken;
import io.opentools.api.tool.OutputArtifact;
import io.opentools.api.tool.TaskContext;
import io.opentools.api.tool.TaskResult;
import io.opentools.api.tool.ToolRequest;
import io.opentools.visualization.io.PlotJson;
import io.opentools.visualization.model.HeatmapData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VisualizationToolsTest {
    @TempDir
    Path tempDir;

    @Test
    void heatmapTaskWritesReproducibleModelAndImages() throws Exception {
        Path input = tempDir.resolve("heatmap.tsv");
        Files.writeString(input, """
                gene\tctrl\ttreated
                beta\t1\t3
                alpha\t2\tNA
                gamma\t5\t7
                """);
        TaskContext context = context("heatmap-run");
        Map<String, Object> params = heatmapParameters(input, tempDir.resolve("heatmap-output"));

        TaskResult result = new HeatmapTool().execute(new ToolRequest("visual.heatmap", params), context);

        Path modelPath = result.artifacts().stream()
                .filter(artifact -> artifact.mediaType().equals("application/vnd.opentools.plot+json"))
                .map(OutputArtifact::path)
                .findFirst()
                .orElseThrow();
        var model = PlotJson.readModel(modelPath);
        assertEquals("visual.heatmap", model.sourceTool());
        assertEquals(
                java.util.List.of("alpha", "beta", "gamma"),
                ((HeatmapData) model.spec().data()).rowLabels()
        );
        assertTrue(result.artifacts().stream().anyMatch(artifact -> artifact.mediaType().equals("image/png")));
        assertTrue(result.artifacts().stream().anyMatch(artifact -> artifact.mediaType().equals("image/svg+xml")));
        assertTrue(result.artifacts().stream().anyMatch(artifact -> artifact.mediaType().equals("application/pdf")));
        assertTrue(Files.isRegularFile(Path.of(tempDir.resolve("heatmap-output").toString() + ".processed.tsv")));
    }

    @Test
    void heatmapHierarchicalOrderingIsDeterministicAndRecordedInLineage() throws Exception {
        Path input = tempDir.resolve("clustered-heatmap.tsv");
        Files.writeString(input, """
                gene\tctrl_a\tctrl_b\ttreated
                alpha_2\t1.0\t1.1\t4.0
                beta_2\t-1.0\t-1.1\t-5.0
                alpha_1\t1.1\t1.0\t4.2
                beta_1\t-1.1\t-1.0\t-4.8
                """);
        Path firstBase = tempDir.resolve("cluster-first");
        Path secondBase = tempDir.resolve("cluster-second");
        Map<String, Object> firstParameters = heatmapParameters(input, firstBase);
        firstParameters.put("rowOrder", "HIERARCHICAL");
        firstParameters.put("columnOrder", "HIERARCHICAL");
        firstParameters.put("maxClusterItems", 100);
        Map<String, Object> secondParameters = heatmapParameters(input, secondBase);
        secondParameters.put("rowOrder", "HIERARCHICAL");
        secondParameters.put("columnOrder", "HIERARCHICAL");
        secondParameters.put("maxClusterItems", 100);

        TaskResult first = new HeatmapTool().execute(
                new ToolRequest("visual.heatmap", firstParameters),
                context("cluster-first-run")
        );
        new HeatmapTool().execute(
                new ToolRequest("visual.heatmap", secondParameters),
                context("cluster-second-run")
        );

        String firstMatrix = Files.readString(Path.of(firstBase + ".processed.tsv"));
        String secondMatrix = Files.readString(Path.of(secondBase + ".processed.tsv"));
        assertEquals(firstMatrix, secondMatrix);
        assertEquals(HierarchicalClusterer.ALGORITHM, first.metrics().get("clusteringAlgorithm"));
        String model = Files.readString(Path.of(firstBase + ".plot.json"));
        assertTrue(model.contains(HierarchicalClusterer.ALGORITHM));
        assertTrue(model.contains("\"rowOrder\" : \"HIERARCHICAL\""));
    }

    @Test
    void vennTaskCalculatesExactIntersections() throws Exception {
        Path input = tempDir.resolve("sets.tsv");
        Files.writeString(input, """
                A\tB\tC
                a\ta\ta
                ab\tab\t
                ac\t\tac
                b\tb\t
                """);
        Map<String, Object> params = commonPlotParameters(tempDir.resolve("sets-output"));
        params.put("input", input.toString());
        params.put("delimiter", "AUTO");
        params.put("displayMode", "AUTO");
        params.put("maximumIntersections", 20);
        params.put("title", "Sets");
        params.put("subtitle", "");

        TaskResult result = new VennTool().execute(
                new ToolRequest("visual.venn", params),
                context("venn-run")
        );

        assertEquals(3, result.metrics().get("sets"));
        assertEquals(3, result.metrics().get("intersections"));
        assertTrue(result.artifacts().stream().anyMatch(artifact -> artifact.mediaType().equals("image/png")));
        assertTrue(Files.isRegularFile(Path.of(tempDir.resolve("sets-output").toString() + ".intersections.tsv")));
    }

    @Test
    void geneStructureTaskRendersTranscriptFeatures() throws Exception {
        Path input = tempDir.resolve("genes.gff3");
        Files.writeString(input, """
                ##gff-version 3
                chr1\ttest\tgene\t100\t500\t.\t+\t.\tID=g1;Name=GeneA
                chr1\ttest\tmRNA\t100\t500\t.\t+\t.\tID=t1;Parent=g1
                chr1\ttest\texon\t100\t180\t.\t+\t.\tID=e1;Parent=t1
                chr1\ttest\tCDS\t220\t400\t.\t+\t.\tID=c1;Parent=t1
                """);
        Map<String, Object> params = commonPlotParameters(tempDir.resolve("gene-output"));
        params.put("input", input.toString());
        params.put("geneIds", "g1");
        params.put("featureTypes", "exon,CDS");
        params.put("maxGenes", 20);
        params.put("title", "Gene model");
        params.put("subtitle", "");

        TaskResult result = new GeneStructureTool().execute(
                new ToolRequest("visual.gene-structure", params),
                context("gene-run")
        );

        assertEquals(1, result.metrics().get("genesRendered"));
        assertEquals(2L, result.metrics().get("featuresRendered"));
        assertTrue(result.artifacts().stream().anyMatch(artifact -> artifact.mediaType().equals("image/svg+xml")));
        assertTrue(Files.readString(Path.of(tempDir.resolve("gene-output").toString() + ".plot.json"))
                .contains("\"id\" : \"g1\""));
    }

    @Test
    void geneSelectionByNameIncludesFollowingTranscriptRecords() throws Exception {
        Path input = tempDir.resolve("genes-by-name.gff3");
        Files.writeString(input, """
                ##gff-version 3
                chr1\ttest\tgene\t100\t500\t.\t+\t.\tID=g1;Name=GeneA
                chr1\ttest\tmRNA\t100\t500\t.\t+\t.\tID=t1;Parent=g1
                chr1\ttest\texon\t100\t180\t.\t+\t.\tID=e1;Parent=t1
                chr1\ttest\tCDS\t220\t400\t.\t+\t.\tID=c1;Parent=t1
                """);
        Map<String, Object> params = commonPlotParameters(tempDir.resolve("gene-name-output"));
        params.put("input", input.toString());
        params.put("geneIds", "GeneA");
        params.put("featureTypes", "exon,CDS");
        params.put("maxGenes", 20);
        params.put("title", "Gene model");
        params.put("subtitle", "");

        TaskResult result = new GeneStructureTool().execute(
                new ToolRequest("visual.gene-structure", params),
                context("gene-name-run")
        );

        assertEquals(2L, result.metrics().get("featuresRendered"));
    }

    private Map<String, Object> heatmapParameters(Path input, Path output) {
        Map<String, Object> params = commonPlotParameters(output);
        params.put("input", input.toString());
        params.put("delimiter", "AUTO");
        params.put("missingPolicy", "GRAY");
        params.put("invalidPolicy", "AS_MISSING");
        params.put("normalization", "NONE");
        params.put("rowOrder", "MEAN_ASC");
        params.put("columnOrder", "ORIGINAL");
        params.put("palette", "VIRIDIS");
        params.put("showValues", false);
        params.put("rangeMode", "AUTO");
        params.put("rangeMin", -1);
        params.put("rangeMax", 1);
        params.put("title", "Heatmap");
        params.put("subtitle", "");
        params.put("maxRows", 200);
        params.put("maxColumns", 200);
        params.put("maxClusterItems", 1000);
        params.put("exportData", true);
        return params;
    }

    private Map<String, Object> commonPlotParameters(Path output) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("width", 700);
        params.put("height", 480);
        params.put("dpi", 144);
        params.put("exportFormats", "PNG_SVG_PDF");
        params.put("exportData", true);
        params.put("output", output.toString());
        params.put("overwrite", true);
        return params;
    }

    private TaskContext context(String runId) {
        Path run = tempDir.resolve(runId);
        return new TaskContext(
                runId,
                tempDir,
                run,
                run.resolve("outputs"),
                ignored -> { },
                ignored -> { },
                new CancellationToken()
        );
    }
}
