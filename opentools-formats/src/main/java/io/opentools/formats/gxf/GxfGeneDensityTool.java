package io.opentools.formats.gxf;

import io.opentools.api.tool.AbstractOpenTool;
import io.opentools.api.tool.OpenToolException;
import io.opentools.api.tool.OutputArtifact;
import io.opentools.api.tool.ResultTable;
import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolResult;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolParameter;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ValidationIssue;
import io.opentools.api.tool.ValidationResult;
import io.opentools.formats.FormatToolSupport;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class GxfGeneDensityTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "gxf.gene-density",
            "Gene Density Windows",
            "Count gene starts in fixed genomic windows and report genes per megabase.",
            "Genome annotation",
            List.of("gene density", "sliding window genes", "基因密度"),
            List.of("GFF3", "GTF", "GFF3.gz", "GTF.gz"),
            List.of("TSV"),
            "1.0.0",
            List.of(
                    ToolParameter.requiredFile("input", "Input annotation", "GFF3 or GTF."),
                    ToolParameter.string("geneTypes", "Gene types", "Feature types counted as genes.", "gene"),
                    ToolParameter.requiredInteger("windowSize", "Window size", "Window size in bases.", 10_000, 1, 10_000_000),
                    ToolParameter.requiredInteger("stepSize", "Step size", "Window step in bases.", 10_000, 1, 10_000_000),
                    ToolParameter.optionalOutputFile("output", "Density TSV", "Leave blank to save in the run directory."),
                    ToolParameter.bool("overwrite", "Overwrite output", "Allow replacing an existing output.", false)
            )
    );

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ValidationResult validate(ToolRequest request) {
        ValidationResult result = validateParameters(request)
                .merge(FormatToolSupport.validateOutput(request, request.path("input")));
        if (request.integer("windowSize") < 1 || request.integer("stepSize") < 1) {
            result = result.merge(ValidationResult.of(ValidationIssue.error(
                    "windowSize", "Window and step sizes must be positive.", "Use values of at least 1.")));
        }
        return result;
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        Path input = request.path("input");
        Path output = FormatToolSupport.outputPath(request, context, "gene-density.tsv");
        Set<String> geneTypes = AnnotationTaskSupport.tokens(request.string("geneTypes"), true);
        int windowSize = request.integer("windowSize");
        int stepSize = request.integer("stepSize");
        Map<String, TreeMap<Long, Long>> geneStarts = new LinkedHashMap<>();
        Map<String, Long> sequenceLengths = new LinkedHashMap<>();
        long genes = 0;
        try (GxfReader reader = new GxfReader(input)) {
            GxfRecord record;
            while ((record = reader.next()) != null) {
                context.cancellationToken().throwIfCancelled();
                sequenceLengths.merge(record.seqId(), record.end(), Math::max);
                if (geneTypes.contains(record.type().toLowerCase(Locale.ROOT))) {
                    geneStarts.computeIfAbsent(record.seqId(), ignored -> new TreeMap<>())
                            .merge(record.start(), 1L, Long::sum);
                    genes++;
                }
            }
        }
        if (sequenceLengths.isEmpty()) {
            throw new OpenToolException("EMPTY_ANNOTATION", "No annotation records were found.", "Check the input.");
        }
        long geneCount = genes;
        long[] totals = new long[2];
        FormatToolSupport.writeAtomically(output, writer -> {
            writer.write("seq_id\tstart\tend\tgene_count\tgenes_per_mb\n");
            long windows = 0;
            for (Map.Entry<String, Long> sequence : sequenceLengths.entrySet()) {
                TreeMap<Long, Long> starts = geneStarts.getOrDefault(sequence.getKey(), new TreeMap<>());
                for (long start = 1; start <= sequence.getValue(); start += stepSize) {
                    long end = Math.min(sequence.getValue(), start + windowSize - 1);
                    long count = starts.subMap(start, true, end, true).values().stream()
                            .mapToLong(Long::longValue).sum();
                    writer.write(sequence.getKey());
                    writer.write('\t');
                    writer.write(Long.toString(start));
                    writer.write('\t');
                    writer.write(Long.toString(end));
                    writer.write('\t');
                    writer.write(Long.toString(count));
                    writer.write('\t');
                    writer.write(String.format(Locale.ROOT, "%.4f", count * 1_000_000.0 / windowSize));
                    writer.newLine();
                    windows++;
                }
            }
            totals[0] = geneCount;
            totals[1] = windows;
        });
        context.progress(1.0, "Gene density calculation completed");
        return new ToolResult(
                "Calculated gene density for " + totals[1] + " windows",
                "result.gxf.gene_density.summary", List.of(totals[1]),
                totals[0] + " genes were assigned by start position.",
                "result.gxf.gene_density.detail", List.of(totals[0]),
                new ResultTable(List.of("Metric", "Value"), List.of(
                        List.of("Genes", Long.toString(totals[0])),
                        List.of("Windows", Long.toString(totals[1])),
                        List.of("Window size", Integer.toString(windowSize)),
                        List.of("Step size", Integer.toString(stepSize))
                )),
                List.of(new OutputArtifact(output, "Gene density", "text/tab-separated-values")),
                Map.of("genes", totals[0], "windows", totals[1],
                        "windowSize", windowSize, "stepSize", stepSize)
        );
    }
}
