package io.opentools.analysis.phylogeny;

import io.opentools.api.tool.AbstractOpenTool;
import io.opentools.api.tool.OpenToolException;
import io.opentools.api.tool.OutputArtifact;
import io.opentools.api.tool.ResultTable;
import io.opentools.api.tool.TaskContext;
import io.opentools.api.tool.TaskResult;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolParameter;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ValidationResult;
import io.opentools.formats.Compression;
import io.opentools.formats.FormatTaskSupport;
import io.opentools.formats.newick.NewickNode;
import io.opentools.formats.newick.NewickParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class NewickTools {
    private NewickTools() {
    }

    public static final class SummaryTool extends AbstractOpenTool {
        private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
                "tree.newick.summary", "Newick Summary", "Summarize leaves, nodes, depth, and branch lengths.",
                "Phylogeny", List.of("newick summary", "树摘要"), List.of("Newick"), List.of("TSV"), "1.0.0",
                List.of(ToolParameter.requiredFile("input", "Input Newick", "Newick tree file."),
                        ToolParameter.optionalOutputFile("output", "Output TSV", "Leave blank to save in the run directory."),
                        ToolParameter.bool("overwrite", "Overwrite output", "Allow replacing an existing output.", false))
        );

        @Override public ToolDescriptor descriptor() { return DESCRIPTOR; }
        @Override public ValidationResult validate(ToolRequest request) {
            return validateParameters(request).merge(FormatTaskSupport.validateOutput(request, request.path("input")));
        }
        @Override public TaskResult execute(ToolRequest request, TaskContext context) throws Exception {
            NewickNode root = new NewickParser().parse(read(request.path("input")));
            Stats stats = stats(root);
            Path output = FormatTaskSupport.outputPath(request, context, "newick-summary.tsv");
            FormatTaskSupport.writeAtomically(output, writer -> {
                writer.write("metric\tvalue\nleaves\t" + stats.leaves
                        + "\nnodes\t" + stats.nodes + "\nmax_depth\t" + stats.depth
                        + "\ntotal_branch_length\t" + stats.branchLength + "\n");
            });
            context.progress(1.0, "Newick summary completed");
            return new TaskResult("Summarized Newick tree", "result.tree.summary.summary",
                    List.of(stats.leaves), stats.leaves + " leaves and " + stats.nodes + " nodes.",
                    "result.tree.summary.detail", List.of(stats.nodes),
                    new ResultTable(List.of("Metric", "Value"), List.of(
                            List.of("Leaves", Integer.toString(stats.leaves)),
                            List.of("Nodes", Integer.toString(stats.nodes)),
                            List.of("Max depth", Integer.toString(stats.depth)))),
                    List.of(new OutputArtifact(output, "Newick summary", "text/tab-separated-values")),
                    Map.of("leaves", stats.leaves, "nodes", stats.nodes, "maxDepth", stats.depth));
        }
    }

    public static final class SubsetTool extends AbstractOpenTool {
        private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
                "tree.newick.subset", "Subset Newick Taxa", "Retain selected taxa and prune empty branches.",
                "Phylogeny", List.of("newick subset", "子树提取"), List.of("Newick"), List.of("Newick"), "1.0.0",
                List.of(ToolParameter.requiredFile("input", "Input Newick", "Newick tree file."),
                        ToolParameter.requiredText("taxa", "Taxa", "One retained leaf name per line."),
                        ToolParameter.optionalOutputFile("output", "Output Newick", "Leave blank to save in the run directory."),
                        ToolParameter.bool("overwrite", "Overwrite output", "Allow replacing an existing output.", false))
        );

        @Override public ToolDescriptor descriptor() { return DESCRIPTOR; }
        @Override public ValidationResult validate(ToolRequest request) {
            return validateParameters(request).merge(FormatTaskSupport.validateOutput(request, request.path("input")));
        }
        @Override public TaskResult execute(ToolRequest request, TaskContext context) throws Exception {
            NewickNode root = new NewickParser().parse(read(request.path("input")));
            Set<String> taxa = new LinkedHashSet<>(request.string("taxa").lines()
                    .map(String::trim).filter(line -> !line.isBlank() && !line.startsWith("#")).toList());
            NewickNode subset = prune(root, taxa);
            if (subset == null || subset.leaf() && subset.name().isBlank()) {
                throw new OpenToolException("NO_TREE_TAXA", "None of the requested taxa were found.", "Check taxon names.");
            }
            Path output = FormatTaskSupport.outputPath(request, context, "subset.nwk");
            String newick = new NewickParser().write(subset);
            FormatTaskSupport.writeAtomically(output, writer -> writer.write(newick + "\n"));
            long leaves = stats(subset).leaves;
            context.progress(1.0, "Newick subset completed");
            return new TaskResult("Retained " + leaves + " taxa", "result.tree.subset.summary", List.of(leaves),
                    "Empty branches were pruned.", "result.tree.subset.detail", List.of(),
                    new ResultTable(List.of("Metric", "Value"), List.of(List.of("Leaves", Long.toString(leaves)))),
                    List.of(new OutputArtifact(output, "Subset Newick tree", "text/plain")),
                    Map.of("leaves", leaves));
        }
    }

    static String read(Path path) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Compression.open(path), StandardCharsets.UTF_8))) {
            return reader.lines().filter(line -> !line.startsWith("#")).collect(java.util.stream.Collectors.joining());
        }
    }

    static Stats stats(NewickNode root) {
        int[] counts = new int[2];
        long[] branch = {0};
        int depth = depth(root, counts, branch);
        return new Stats(counts[0], counts[1], depth, branch[0]);
    }

    private static int depth(NewickNode node, int[] counts, long[] branch) {
        counts[1]++;
        if (node.length() != null) {
            branch[0] += Math.round(node.length() * 1_000_000);
        }
        if (node.leaf()) {
            counts[0]++;
            return 1;
        }
        return 1 + node.children().stream().mapToInt(child -> depth(child, counts, branch)).max().orElse(0);
    }

    static NewickNode prune(NewickNode node, Set<String> taxa) {
        if (node.leaf()) {
            return taxa.contains(node.name()) ? node : null;
        }
        List<NewickNode> retained = new ArrayList<>();
        for (NewickNode child : node.children()) {
            NewickNode pruned = prune(child, taxa);
            if (pruned != null) {
                retained.add(pruned);
            }
        }
        if (retained.isEmpty()) {
            return null;
        }
        if (retained.size() == 1 && node.name().isBlank()) {
            return retained.getFirst();
        }
        return new NewickNode(node.name(), node.length(), retained);
    }

    record Stats(int leaves, int nodes, int depth, long branchLength) {
    }
}
