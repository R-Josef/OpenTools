package io.opentools.visualization;

import io.opentools.visualization.export.PlotExporter;
import io.opentools.visualization.model.HeatmapData;
import io.opentools.visualization.model.PlotDiagnostic;
import io.opentools.visualization.model.PlotExport;
import io.opentools.visualization.model.PlotSpec;
import io.opentools.visualization.model.PlotStyle;
import io.opentools.visualization.model.PlotType;
import io.opentools.visualization.render.PlotSceneFactory;
import io.opentools.visualization.scene.SceneText;
import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PlotExporterTest {
    @TempDir
    Path tempDir;

    @Test
    void exportsHeadlessPngSvgAndPdf() throws Exception {
        PlotSpec spec = spec();
        List<Path> outputs = PlotExporter.export(spec, tempDir.resolve("heatmap"));

        assertEquals(3, outputs.size());
        assertTrue(Files.size(outputs.get(0)) > 500);
        BufferedImage image = ImageIO.read(outputs.get(0).toFile());
        assertEquals(PlotSceneFactory.create(spec).width(), image.getWidth());
        assertEquals(PlotSceneFactory.create(spec).height(), image.getHeight());

        String svg = Files.readString(outputs.get(1));
        assertTrue(svg.contains("<svg"));
        assertTrue(svg.contains("Long row label"));

        assertTrue(Files.size(outputs.get(2)) > 500);
        try (var document = Loader.loadPDF(outputs.get(2).toFile())) {
            assertEquals(1, document.getNumberOfPages());
        }
    }

    @Test
    void pngContainsNonUniformChartPixels() throws Exception {
        Path output = tempDir.resolve("plot.png");
        PlotExporter.export(spec(), Path.of(output.toString().replace(".png", "")));
        BufferedImage image = ImageIO.read(output.toFile());
        int first = image.getRGB(10, 10);
        boolean foundDifferent = false;
        for (int y = 0; y < image.getHeight() && !foundDifferent; y += 7) {
            for (int x = 0; x < image.getWidth(); x += 7) {
                if (image.getRGB(x, y) != first) {
                    foundDifferent = true;
                    break;
                }
            }
        }
        assertTrue(foundDifferent);
        assertFalse(Files.readString(tempDir.resolve("plot.svg")).isBlank());
    }

    @Test
    void sceneKeepsLongHeatmapLabelsWithoutEllipsis() {
        String rowLabel = "very_long_gene_identifier_with_annotation_context_and_sample_replicate";
        String columnLabel = "very_long_condition_name_with_treatment_and_replicate_context";
        HeatmapData data = new HeatmapData(
                List.of(rowLabel),
                List.of(columnLabel),
                List.of(List.of(1.5)),
                "ORIGINAL",
                "ORIGINAL",
                1,
                1,
                0,
                0,
                0,
                1,
                1,
                List.of()
        );
        PlotSpec longLabelSpec = new PlotSpec(
                "1.0",
                "labels",
                PlotType.HEATMAP,
                "Label test",
                "",
                PlotStyle.defaults(),
                new PlotExport(List.of("PNG"), "labels", 640, 420, 144, false, true),
                Map.of(),
                data
        );

        List<String> renderedText = PlotSceneFactory.create(longLabelSpec).primitives().stream()
                .filter(SceneText.class::isInstance)
                .map(SceneText.class::cast)
                .map(SceneText::text)
                .toList();

        assertTrue(renderedText.contains(rowLabel));
        assertTrue(renderedText.contains(columnLabel));
        assertTrue(renderedText.stream().noneMatch(text -> text.contains("...")));
    }

    private static PlotSpec spec() {
        HeatmapData data = new HeatmapData(
                List.of("Long row label"),
                List.of("A", "B", "C"),
                List.of(Arrays.asList(1.0, 2.0, null)),
                "ORIGINAL",
                "ORIGINAL",
                1,
                3,
                1,
                0,
                0,
                1,
                1,
                List.of(new PlotDiagnostic("WARNING", "visual.diagnostic.missing_value", "Missing C", 2, 1, 3))
        );
        return new PlotSpec(
                "1.0",
                "heatmap",
                PlotType.HEATMAP,
                "Expression",
                "Export test",
                PlotStyle.heatmap(PlotStyle.VIRIDIS, true),
                new PlotExport(List.of("PNG", "SVG", "PDF"), "heatmap", 640, 420, 144, false, true),
                Map.of("tool", "visual.heatmap"),
                data
        );
    }
}
