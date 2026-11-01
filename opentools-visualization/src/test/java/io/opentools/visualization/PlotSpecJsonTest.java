package io.opentools.visualization;

import io.opentools.visualization.io.PlotJson;
import io.opentools.visualization.model.GeneStructureData;
import io.opentools.visualization.model.HeatmapData;
import io.opentools.visualization.model.PlotExport;
import io.opentools.visualization.model.PlotModel;
import io.opentools.visualization.model.PlotSpec;
import io.opentools.visualization.model.PlotStyle;
import io.opentools.visualization.model.PlotType;
import io.opentools.visualization.model.VennData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PlotSpecJsonTest {
    @TempDir
    Path tempDir;

    @Test
    void roundTripsHeatmapSpecAndPlotModel() throws Exception {
        PlotSpec spec = heatmapSpec();

        PlotSpec restored = PlotJson.read(PlotJson.write(spec));
        assertEquals(spec, restored);

        Path output = tempDir.resolve("plot.plot.json");
        PlotModel model = PlotModel.create("visual.heatmap", spec, Map.of("input", "matrix.tsv"));
        PlotJson.write(model, output);
        PlotModel restoredModel = PlotJson.readModel(output);

        assertEquals(model.spec(), restoredModel.spec());
        assertEquals("visual.heatmap", restoredModel.sourceTool());
        assertTrue(Files.readString(output).contains("\"plotType\" : \"HEATMAP\""));
    }

    @Test
    void supportsAllPlotDataVariants() throws Exception {
        PlotSpec venn = new PlotSpec(
                "1.0",
                "venn",
                PlotType.UPSET,
                "Sets",
                "",
                PlotStyle.defaults(),
                PlotExport.defaults("sets"),
                Map.of(),
                new VennData(
                        List.of("A", "B", "C"),
                        List.of(3, 3, 2),
                        List.of(
                                new VennData.Intersection(List.of("A"), 1, List.of("a")),
                                new VennData.Intersection(List.of("A", "B"), 2, List.of("ab"))
                        ),
                        "UPSET",
                        20
                )
        );
        PlotSpec genes = new PlotSpec(
                "1.0",
                "genes",
                PlotType.GENE_STRUCTURE,
                "Genes",
                "",
                PlotStyle.defaults(),
                PlotExport.defaults("genes"),
                Map.of(),
                new GeneStructureData(List.of(
                        new GeneStructureData.GeneLocus(
                                "gene1",
                                "chr1",
                                100,
                                500,
                                '+',
                                List.of(new GeneStructureData.Transcript(
                                        "tx1",
                                        100,
                                        500,
                                        '+',
                                        List.of(
                                                new GeneStructureData.Feature("exon", 100, 180, '+'),
                                                new GeneStructureData.Feature("CDS", 220, 400, '+')
                                        )
                                ))
                        )
                ))
        );

        assertEquals(venn, PlotJson.read(PlotJson.write(venn)));
        assertEquals(genes, PlotJson.read(PlotJson.write(genes)));
    }

    private static PlotSpec heatmapSpec() {
        List<List<Double>> values = Arrays.asList(
                Arrays.asList(1.0, null, 3.0),
                Arrays.asList(4.0, 5.0, 6.0)
        );
        HeatmapData data = new HeatmapData(
                List.of("row long label", "row 2"),
                List.of("sample A", "sample B", "sample C"),
                values,
                "MEAN_ASC",
                "ORIGINAL",
                2,
                3,
                1,
                0,
                0,
                1,
                1,
                List.of()
        );
        return new PlotSpec(
                "1.0",
                "heatmap",
                PlotType.HEATMAP,
                "Heatmap",
                "round trip",
                PlotStyle.heatmap(PlotStyle.VIRIDIS, false),
                PlotExport.defaults("heatmap"),
                Map.of("source", "test"),
                data
        );
    }
}
