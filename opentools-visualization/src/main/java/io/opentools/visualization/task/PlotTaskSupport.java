package io.opentools.visualization.task;

import io.opentools.api.tool.OutputArtifact;
import io.opentools.api.tool.TaskContext;
import io.opentools.visualization.export.PlotExporter;
import io.opentools.visualization.io.PlotJson;
import io.opentools.visualization.model.PlotModel;
import io.opentools.visualization.model.PlotSpec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class PlotTaskSupport {
    private PlotTaskSupport() {
    }

    static Path basePath(TaskContext context, Path requestedOutput, String defaultName) {
        Path selected = requestedOutput == null || requestedOutput.toString().isBlank()
                ? context.defaultOutputPath(defaultName)
                : requestedOutput;
        return stripKnownExtensions(selected);
    }

    static List<Path> expectedOutputs(PlotSpec spec, Path basePath) {
        List<Path> paths = new ArrayList<>();
        paths.add(plotDocumentPath(basePath));
        for (String format : spec.export().formats()) {
            paths.add(Path.of(basePath + "." + format.toLowerCase(Locale.ROOT)));
        }
        return List.copyOf(paths);
    }

    static RenderedPlot render(
            String sourceTool,
            PlotSpec spec,
            TaskContext context,
            Path basePath,
            Map<String, String> lineage
    ) throws Exception {
        Path plotDocument = plotDocumentPath(basePath);
        PlotModel model = PlotModel.create(sourceTool, spec, lineage);
        PlotJson.write(model, plotDocument);
        List<Path> images = PlotExporter.export(spec, basePath);

        List<OutputArtifact> artifacts = new ArrayList<>();
        artifacts.add(new OutputArtifact(plotDocument, "Serializable plot model", "application/vnd.opentools.plot+json"));
        for (Path image : images) {
            artifacts.add(new OutputArtifact(image, artifactTitle(image), mediaType(image)));
        }
        context.log("Plot model: " + plotDocument);
        context.log("Exported plot artifacts: " + images.size());
        return new RenderedPlot(model, plotDocument, images, List.copyOf(artifacts));
    }

    static Map<String, String> lineage(String toolId, PlotSpec spec, Path input) {
        Map<String, String> lineage = new LinkedHashMap<>();
        lineage.put("toolId", toolId);
        lineage.put("input", input == null ? "" : input.toAbsolutePath().normalize().toString());
        lineage.put("plotType", spec.plotType().name());
        lineage.put("exportFormats", String.join(",", spec.export().formats()));
        lineage.put("width", Integer.toString(spec.export().width()));
        lineage.put("height", Integer.toString(spec.export().height()));
        lineage.put("dpi", Integer.toString(spec.export().dpi()));
        return lineage;
    }

    static Path plotDocumentPath(Path basePath) {
        return Path.of(basePath + ".plot.json");
    }

    static Path stripKnownExtensions(Path path) {
        String text = path.toString();
        String lower = text.toLowerCase(Locale.ROOT);
        for (String extension : List.of(".plot.json", ".json", ".png", ".svg", ".pdf", ".tsv")) {
            if (lower.endsWith(extension)) {
                return Path.of(text.substring(0, text.length() - extension.length()));
            }
        }
        return path;
    }

    static boolean anyExists(List<Path> paths) {
        return paths.stream().anyMatch(Files::exists);
    }

    static String fileStem(Path path) {
        String name = path.getFileName().toString();
        int separator = name.lastIndexOf('.');
        return separator <= 0 ? name : name.substring(0, separator);
    }

    private static String artifactTitle(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".png")) {
            return "Plot PNG";
        }
        if (name.endsWith(".svg")) {
            return "Plot SVG";
        }
        if (name.endsWith(".pdf")) {
            return "Plot PDF";
        }
        return "Plot output";
    }

    private static String mediaType(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".png")) {
            return "image/png";
        }
        if (name.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (name.endsWith(".pdf")) {
            return "application/pdf";
        }
        return "application/octet-stream";
    }

    record RenderedPlot(
            PlotModel model,
            Path plotDocument,
            List<Path> images,
            List<OutputArtifact> artifacts
    ) {
    }
}
