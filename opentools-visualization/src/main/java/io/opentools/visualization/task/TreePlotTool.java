package io.opentools.visualization.task;

import io.opentools.api.tool.AbstractOpenTool;
import io.opentools.api.tool.TaskContext;
import io.opentools.api.tool.TaskResult;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolParameter;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ValidationResult;
import io.opentools.formats.Compression;
import io.opentools.formats.newick.NewickNode;
import io.opentools.formats.newick.NewickParser;
import io.opentools.visualization.model.GenericPlotData;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TreePlotTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = PlotToolSupport.descriptor(
            "tree.render",
            "Tree Render",
            "Render a rectangular cladogram from Newick as serializable line segments.",
            List.of(ToolParameter.requiredFile("input", "Input Newick", "Newick tree file.")),
            "Phylogenetic Tree"
    );

    @Override public ToolDescriptor descriptor() { return DESCRIPTOR; }
    @Override public ValidationResult validate(ToolRequest request) { return PlotToolSupport.validate(request); }

    @Override
    public TaskResult execute(ToolRequest request, TaskContext context) throws Exception {
        String text;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Compression.open(request.path("input")), StandardCharsets.UTF_8))) {
            text = reader.lines().filter(line -> !line.startsWith("#"))
                    .collect(java.util.stream.Collectors.joining());
        }
        NewickNode root = new NewickParser().parse(text);
        Map<NewickNode, double[]> positions = new LinkedHashMap<>();
        double[] leafIndex = {0};
        position(root, 0, leafIndex, positions);
        List<GenericPlotData.GenericSeries> edges = new ArrayList<>();
        collectEdges(root, positions, edges);
        GenericPlotData data = new GenericPlotData("LINE", "Branch depth", "Taxon position",
                List.of(), edges);
        return PlotToolSupport.renderGeneric(this, request, context, data,
                "tree", request.string("title"), "result.tree.render.summary", edges.size());
    }

    private static double position(
            NewickNode node, double depth, double[] leaf, Map<NewickNode, double[]> positions
    ) {
        double y;
        if (node.leaf()) {
            y = leaf[0]++;
        } else {
            double sum = 0;
            for (NewickNode child : node.children()) {
                sum += position(child, depth + 1, leaf, positions);
            }
            y = sum / node.children().size();
        }
        positions.put(node, new double[]{depth, y});
        return y;
    }

    private static void collectEdges(
            NewickNode node, Map<NewickNode, double[]> positions,
            List<GenericPlotData.GenericSeries> edges
    ) {
        double[] parent = positions.get(node);
        for (NewickNode child : node.children()) {
            double[] childPosition = positions.get(child);
            edges.add(new GenericPlotData.GenericSeries(node.name() + "-" + child.name(),
                    List.of(parent[0], childPosition[0]), List.of(parent[1], childPosition[1])));
            collectEdges(child, positions, edges);
        }
    }
}
