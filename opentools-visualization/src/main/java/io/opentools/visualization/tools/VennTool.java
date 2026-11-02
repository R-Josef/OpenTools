package io.opentools.visualization.tools;

import io.opentools.api.tool.AbstractOpenTool;
import io.opentools.api.tool.OpenToolException;
import io.opentools.api.tool.OutputArtifact;
import io.opentools.api.tool.ParameterType;
import io.opentools.api.tool.ResultTable;
import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolResult;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolParameter;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ValidationIssue;
import io.opentools.api.tool.ValidationResult;
import io.opentools.visualization.model.PlotExport;
import io.opentools.visualization.model.PlotSpec;
import io.opentools.visualization.model.PlotStyle;
import io.opentools.visualization.model.PlotType;
import io.opentools.visualization.model.VennData;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class VennTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "visual.venn",
            "Venn / UpSet",
            "Calculate exact set intersections and render a Venn diagram or UpSet plot.",
            "Visualization",
            List.of("venn", "upset", "set intersection", "集合", "交集"),
            List.of("TSV", "CSV", "TSV.gz", "CSV.gz"),
            List.of("SVG", "PNG", "PDF", "JSON"),
            "1.0.0",
            List.of(
                    ToolParameter.requiredFile("input", "Set table", "A delimited table with one set per column and one member per row."),
                    enumParameter("delimiter", "Delimiter", "AUTO detects tab, comma, or semicolon.", "AUTO",
                            List.of("AUTO", "TAB", "COMMA", "SEMICOLON"), false),
                    enumParameter("displayMode", "Display mode", "AUTO uses a Venn diagram for up to three sets and UpSet otherwise.", "AUTO",
                            List.of("AUTO", "VENN", "UPSET"), false),
                    integerParameter("maximumIntersections", "Maximum intersections", "Maximum intersections drawn in UpSet mode.", 20, 1, 100, false),
                    ToolParameter.string("title", "Plot title", "Optional title written into the plot model.", "Set intersections"),
                    ToolParameter.string("subtitle", "Plot subtitle", "Optional subtitle written into the plot model.", ""),
                    integerParameter("width", "Image width", "Rendered width in pixels.", 1100, 320, 10000, true),
                    integerParameter("height", "Image height", "Rendered height in pixels.", 760, 240, 10000, true),
                    integerParameter("dpi", "PDF DPI", "Resolution metadata and physical PDF page size.", 144, 72, 600, true),
                    enumParameter("exportFormats", "Export formats", "Choose the headless artifact formats.", "PNG_SVG_PDF",
                            List.of("PNG_SVG_PDF", "PNG_SVG", "PNG"), true),
                    ToolParameter.bool("exportData", "Export intersections", "Write exact intersection counts as TSV.", true),
                    ToolParameter.optionalOutputFile("output", "Output prefix", "Optional base path for plot and image outputs."),
                    ToolParameter.bool("overwrite", "Overwrite outputs", "Allow replacing existing custom output files.", false)
            )
    );

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ValidationResult validate(ToolRequest request) {
        ValidationResult result = validateParameters(request);
        Path requested = request.path("output");
        if (requested != null && !request.bool("overwrite")) {
            Path base = PlotArtifactSupport.stripKnownExtensions(requested);
            List<Path> expected = new ArrayList<>();
            expected.add(PlotArtifactSupport.plotDocumentPath(base));
            exportFormats(request).forEach(format -> expected.add(Path.of(base + "." + format.toLowerCase(Locale.ROOT))));
            if (request.bool("exportData")) {
                expected.add(Path.of(base + ".intersections.tsv"));
            }
            if (PlotArtifactSupport.anyExists(expected)) {
                result = result.merge(ValidationResult.of(ValidationIssue.error(
                        "output",
                        "One or more set-plot outputs already exist.",
                        "Choose another output prefix or enable overwrite."
                )));
            }
        }
        return result;
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        context.progress(0.08, "Parsing set table");
        VennSetParser.ParseResult parsed = new VennSetParser().parse(
                request.path("input"),
                request.string("delimiter"),
                context.cancellationToken()
        );
        String displayMode = request.string("displayMode");
        PlotType plotType;
        if (displayMode.equals("VENN")) {
            plotType = parsed.setNames().size() <= 3 ? PlotType.VENN : PlotType.UPSET;
        } else if (displayMode.equals("UPSET")) {
            plotType = PlotType.UPSET;
        } else {
            plotType = parsed.setNames().size() <= 3 ? PlotType.VENN : PlotType.UPSET;
        }
        VennData data = new VennData(
                parsed.setNames(),
                parsed.setSizes(),
                parsed.intersections(),
                plotType.name(),
                request.integer("maximumIntersections")
        );
        Path base = PlotArtifactSupport.basePath(context, request.path("output"), "set-intersections");
        PlotSpec spec = new PlotSpec(
                "1.0",
                "set-intersections",
                plotType,
                request.string("title"),
                request.string("subtitle"),
                PlotStyle.defaults(),
                new PlotExport(
                        exportFormats(request),
                        base.getFileName().toString(),
                        request.integer("width"),
                        request.integer("height"),
                        request.integer("dpi"),
                        false,
                        request.bool("overwrite")
                ),
                Map.of(
                        "sourceRows", Long.toString(parsed.tableRows()),
                        "setCount", Integer.toString(parsed.setNames().size()),
                        "intersectionCount", Integer.toString(parsed.intersections().size()),
                        "displayMode", plotType.name(),
                        "delimiter", Character.toString(parsed.delimiter())
                ),
                data
        );
        Path intersectionTable = Path.of(base + ".intersections.tsv");
        if (request.bool("exportData")) {
            writeIntersections(intersectionTable, parsed);
        }
        context.progress(0.55, "Rendering set intersections");
        PlotArtifactSupport.RenderedPlot rendered = PlotArtifactSupport.render(
                descriptor().id(),
                spec,
                context,
                base,
                PlotArtifactSupport.lineage(descriptor().id(), spec, request.path("input"))
        );
        List<OutputArtifact> artifacts = new ArrayList<>(rendered.artifacts());
        if (request.bool("exportData")) {
            artifacts.add(new OutputArtifact(intersectionTable, "Exact set intersections", "text/tab-separated-values"));
        }
        int totalUnion = parsed.setNames().size() == 0 ? 0
                : parsed.intersections().stream().mapToInt(VennData.Intersection::count).sum();
        ResultTable table = new ResultTable(
                List.of("Intersection", "Count", "Examples"),
                parsed.intersections().stream().limit(50).map(intersection -> List.of(
                        String.join(" + ", intersection.sets()),
                        Integer.toString(intersection.count()),
                        String.join(", ", intersection.examples().stream().limit(5).toList())
                )).toList()
        );
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("sets", parsed.setNames().size());
        metrics.put("intersections", parsed.intersections().size());
        metrics.put("unionSize", totalUnion);
        metrics.put("displayMode", plotType.name());
        metrics.put("exportFormats", exportFormats(request));
        context.progress(1.0, "Set intersection plot completed");
        return new ToolResult(
                "Rendered " + parsed.setNames().size() + " sets and " + parsed.intersections().size() + " intersections",
                List.of(parsed.setNames().size(), parsed.intersections().size()),
                "Union contains " + totalUnion + " identifiers",
                List.of(totalUnion),
                table,
                artifacts,
                metrics
        );
    }

    private static void writeIntersections(Path output, VennSetParser.ParseResult parsed) throws Exception {
        Files.createDirectories(output.toAbsolutePath().getParent());
        try (var writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            writer.write("sets\tcount\texamples");
            writer.newLine();
            for (VennData.Intersection intersection : parsed.intersections()) {
                writer.write(String.join("+", intersection.sets()));
                writer.write('\t');
                writer.write(Integer.toString(intersection.count()));
                writer.write('\t');
                writer.write(String.join(",", intersection.examples()));
                writer.newLine();
            }
        }
    }

    private static List<String> exportFormats(ToolRequest request) {
        return switch (request.string("exportFormats")) {
            case "PNG_SVG" -> List.of("PNG", "SVG");
            case "PNG" -> List.of("PNG");
            default -> List.of("PNG", "SVG", "PDF");
        };
    }

    private static ToolParameter enumParameter(
            String id,
            String title,
            String description,
            String defaultValue,
            List<String> options,
            boolean advanced
    ) {
        return new ToolParameter(id, title, description, ParameterType.ENUM, false, defaultValue,
                options, null, null, advanced, null);
    }

    private static ToolParameter integerParameter(
            String id,
            String title,
            String description,
            int defaultValue,
            int minimum,
            int maximum,
            boolean advanced
    ) {
        return new ToolParameter(id, title, description, ParameterType.INTEGER, false, defaultValue,
                List.of(), (double) minimum, (double) maximum, advanced, null);
    }
}
