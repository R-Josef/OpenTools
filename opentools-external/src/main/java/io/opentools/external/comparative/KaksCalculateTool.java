package io.opentools.external.comparative;

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
import io.opentools.core.util.OrderedParallelProcessor;
import io.opentools.formats.FormatTaskSupport;
import io.opentools.formats.fasta.FastaReader;
import io.opentools.formats.fasta.FastaRecord;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class KaksCalculateTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "kaks.calculate", "Ka/Ks Calculation", "Estimate pairwise Ka/Ks from adjacent codon-aligned CDS records.",
            "Comparative genomics", List.of("Ka/Ks", "dN/dS", "选择压力"),
            List.of("Aligned CDS FASTA"), List.of("TSV"), "1.1.0",
            List.of(ToolParameter.requiredFile("input", "Aligned CDS FASTA", "Adjacent records are interpreted as pairs."),
                    ToolParameter.integer("threads", "Threads",
                            "0 uses AUTO for large inputs; 1 disables parallel sequence pairs.",
                            0, 0, 64),
                    ToolParameter.optionalOutputFile("output", "Output TSV", "Leave blank to save in the run directory."),
                    ToolParameter.bool("overwrite", "Overwrite output", "Allow replacing an existing output.", false)));

    @Override public ToolDescriptor descriptor() { return DESCRIPTOR; }
    @Override public ValidationResult validate(ToolRequest request) {
        return validateParameters(request).merge(FormatTaskSupport.validateOutput(request, request.path("input")));
    }

    @Override public TaskResult execute(ToolRequest request, TaskContext context) throws Exception {
        List<FastaRecord> records = new ArrayList<>();
        try (FastaReader reader = new FastaReader(request.path("input"))) {
            FastaRecord record;
            while ((record = reader.next()) != null) {
                records.add(record);
            }
        }
        if (records.size() < 2 || records.size() % 2 != 0) {
            throw new OpenToolException("KAKS_PAIR_INPUT_INVALID",
                    "Provide an even number of adjacent aligned CDS records.",
                    "Each sequence pair must have equal aligned length.");
        }
        Path output = FormatTaskSupport.outputPath(request, context, "kaks.tsv");
        int threads = OrderedParallelProcessor.resolveThreads(
                request.integer("threads"), Files.size(request.path("input")));
        long[] pairs = new long[1];
        FormatTaskSupport.writeAtomically(output, writer -> {
            writer.write("sequence_a\tsequence_b\tlength\tka\tks\tka_ks\n");
            int pairCount = records.size() / 2;
            int[] nextPair = {0};
            OrderedParallelProcessor.processChunks(
                    threads,
                    32,
                    context.cancellationToken(),
                    () -> nextPair[0] < pairCount ? nextPair[0]++ : null,
                    chunk -> {
                        List<String> lines = new ArrayList<>(chunk.size());
                        for (int pairIndex : chunk) {
                            lines.add(calculatePair(
                                    records.get(pairIndex * 2),
                                    records.get(pairIndex * 2 + 1)
                            ));
                        }
                        return lines;
                    },
                    lines -> {
                        for (String line : lines) {
                            writer.write(line);
                            writer.newLine();
                            pairs[0]++;
                        }
                    }
            );
        });
        context.progress(1.0, "Ka/Ks calculation completed");
        return new TaskResult("Calculated Ka/Ks for " + pairs[0] + " sequence pairs",
                "result.kaks.summary", List.of(pairs[0]),
                "Nei-Gojobori style approximate codon distances.", "result.kaks.detail", List.of(),
                new ResultTable(List.of("Metric", "Value"), List.of(List.of("Pairs", Long.toString(pairs[0])))),
                List.of(new OutputArtifact(output, "Ka/Ks results", "text/tab-separated-values")),
                Map.of("pairs", pairs[0], "threads", threads,
                        "parallelPairs", threads > 1));
    }

    private static String calculatePair(FastaRecord a, FastaRecord b) {
        if (a.sequence().length() != b.sequence().length()
                || a.sequence().length() % 3 != 0) {
            throw new OpenToolException("KAKS_ALIGNMENT_INVALID",
                    "Pair " + a.id() + "/" + b.id()
                            + " is not a codon-aligned equal-length pair.",
                    "Align CDS sequences in frame before Ka/Ks calculation.");
        }
        long synonymousSites = 0;
        long nonsynonymousSites = 0;
        long synonymousDiff = 0;
        long nonsynonymousDiff = 0;
        for (int codon = 0; codon < a.sequence().length(); codon += 3) {
            String left = a.sequence().substring(codon, codon + 3)
                    .toUpperCase(Locale.ROOT);
            String right = b.sequence().substring(codon, codon + 3)
                    .toUpperCase(Locale.ROOT);
            if (left.contains("-") || right.contains("-")
                    || left.contains("N") || right.contains("N")) {
                continue;
            }
            int fold = fourfoldPositions(left);
            synonymousSites += fold;
            nonsynonymousSites += 3L - fold;
            if (!left.equals(right)) {
                if (translate(left).equals(translate(right))) {
                    synonymousDiff++;
                } else {
                    nonsynonymousDiff++;
                }
            }
        }
        double ps = synonymousSites == 0 ? 0
                : synonymousDiff / (double) synonymousSites;
        double pa = nonsynonymousSites == 0 ? 0
                : nonsynonymousDiff / (double) nonsynonymousSites;
        double ks = jukes(ps);
        double ka = jukes(pa);
        double ratio = ks <= 0 ? Double.NaN : ka / ks;
        return a.id() + "\t" + b.id() + "\t" + a.sequence().length() + "\t"
                + format(ka) + "\t" + format(ks) + "\t" + format(ratio);
    }

    private static int fourfoldPositions(String codon) {
        String[] bases = {"A", "C", "G", "T"};
        int count = 0;
        for (int position = 0; position < 3; position++) {
            boolean synonymous = true;
            String reference = translate(codon);
            for (String base : bases) {
                StringBuilder changed = new StringBuilder(codon);
                changed.setCharAt(position, base.charAt(0));
                if (!translate(changed.toString()).equals(reference)) {
                    synonymous = false;
                    break;
                }
            }
            if (synonymous) {
                count++;
            }
        }
        return count;
    }

    private static String translate(String codon) {
        String table = "FFLLSSSSYY**CC*W"
                + "LLLLPPPPHHQQRRRR"
                + "IIIMTTTTNNKKSSRR"
                + "VVVVAAAADDEEGGGG";
        String bases = "TCAG";
        int index = bases.indexOf(codon.charAt(0)) * 16
                + bases.indexOf(codon.charAt(1)) * 4
                + bases.indexOf(codon.charAt(2));
        return index < 0 || index >= table.length() ? "X" : table.substring(index, index + 1);
    }

    private static double jukes(double p) {
        double value = 1 - 4.0 / 3.0 * p;
        return value <= 0 ? Double.NaN : -0.75 * Math.log(value);
    }

    private static String format(double value) {
        return Double.isNaN(value) ? "NA" : String.format(Locale.ROOT, "%.6g", value);
    }
}
