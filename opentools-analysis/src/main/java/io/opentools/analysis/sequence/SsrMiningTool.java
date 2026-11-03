package io.opentools.analysis.sequence;

import io.opentools.api.tool.AbstractOpenTool;
import io.opentools.api.tool.OutputArtifact;
import io.opentools.api.tool.ResultTable;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolParameter;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ToolResult;
import io.opentools.api.tool.ValidationResult;
import io.opentools.formats.FormatToolSupport;
import io.opentools.formats.fasta.FastaReader;
import io.opentools.formats.fasta.FastaRecord;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Finds perfect simple sequence repeats in FASTA records.
 */
public final class SsrMiningTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "sequence.ssr",
            "SSR Mining",
            "Find perfect simple sequence repeats (microsatellites) in FASTA records.",
            "Sequence",
            List.of("SSR", "microsatellite", "simple sequence repeat", "SSR挖掘", "微卫星"),
            List.of("FASTA"),
            List.of("TSV"),
            "1.0.0",
            List.of(
                    ToolParameter.requiredFile("input", "Input FASTA",
                            "Nucleotide FASTA, optionally gzip-compressed."),
                    ToolParameter.integer("maxMotifLength", "Maximum motif length",
                            "Check every motif length from 1 through this value.", 6, 1, 10),
                    ToolParameter.integer("minRepeats", "Minimum repeats",
                            "Minimum number of consecutive motif copies.", 3, 2, 100),
                    ToolParameter.integer("maxLoci", "Maximum loci",
                            "Stop after this many SSR loci and mark the result truncated.", 100000, 1, 1000000),
                    ToolParameter.optionalOutputFile("output", "Output TSV",
                            "Leave blank to save in the run directory."),
                    ToolParameter.bool("overwrite", "Overwrite output", "Allow replacing an existing output.", false)
            )
    );

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ValidationResult validate(ToolRequest request) {
        return validateParameters(request)
                .merge(FormatToolSupport.validateOutput(request, request.path("input")));
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        int maxMotifLength = Math.max(1, Math.min(10, request.integer("maxMotifLength")));
        int minRepeats = Math.max(2, request.integer("minRepeats"));
        int maxLoci = Math.max(1, request.integer("maxLoci"));
        long[] stats = new long[3];
        boolean[] capped = new boolean[1];
        Map<Integer, Long> byLength = new LinkedHashMap<>();
        Path output = FormatToolSupport.outputPath(request, context, "ssr-loci.tsv");
        FormatToolSupport.writeAtomically(output, writer -> {
            writer.write("sequence	start	end	motif	repeats	length	motif_length\n");
            try (FastaReader reader = new FastaReader(request.path("input"))) {
                FastaRecord record;
                while (!capped[0] && (record = reader.next()) != null) {
                    context.cancellationToken().throwIfCancelled();
                    stats[0]++;
                    String sequence = record.sequence().toUpperCase(Locale.ROOT);
                    int index = 0;
                    while (index < sequence.length()) {
                        if (stats[1] >= maxLoci) {
                            capped[0] = true;
                            break;
                        }
                        boolean found = false;
                        for (int length = 1; length <= maxMotifLength
                                && index + length * minRepeats <= sequence.length(); length++) {
                            String motif = sequence.substring(index, index + length);
                            if (!isAcgt(motif)) {
                                continue;
                            }
                            int repeats = 1;
                            while (index + (repeats + 1) * length <= sequence.length()
                                    && sequence.regionMatches(index, sequence, index + repeats * length, length)) {
                                repeats++;
                            }
                            if (repeats >= minRepeats) {
                                int end = index + repeats * length;
                                writer.write(record.id());
                                writer.write('\t'); writer.write(Integer.toString(index + 1));
                                writer.write('\t'); writer.write(Integer.toString(end));
                                writer.write('\t'); writer.write(motif);
                                writer.write('\t'); writer.write(Integer.toString(repeats));
                                writer.write('\t'); writer.write(Integer.toString(repeats * length));
                                writer.write('\t'); writer.write(Integer.toString(length));
                                writer.newLine();
                                stats[1]++;
                                stats[2] += repeats * length;
                                byLength.merge(length, 1L, Long::sum);
                                index = end;
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            index++;
                        }
                    }
                }
            }
            if (capped[0]) {
                // A sentinel row keeps truncation visible in the artifact as well as the metrics.
                writer.write("# truncated at " + maxLoci + " loci\n");
            }
            if (stats[1] == 0) {
                // Keep the header-only artifact; no-amplification is a valid biological result.
                writer.flush();
            }
        });
        context.progress(1.0, "SSR mining completed");
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("records", stats[0]);
        metrics.put("loci", stats[1]);
        metrics.put("repeatedBases", stats[2]);
        metrics.put("truncated", capped[0]);
        metrics.put("maxMotifLength", maxMotifLength);
        metrics.put("minRepeats", minRepeats);
        for (Map.Entry<Integer, Long> entry : byLength.entrySet()) {
            metrics.put("motifLength" + entry.getKey(), entry.getValue());
        }
        return new ToolResult(
                "Found " + stats[1] + " SSR loci",
                List.of(stats[1]),
                stats[0] + " sequences scanned; repeated bases: " + stats[2]
                        + (metrics.get("truncated").equals(true) ? "; result truncated" : ""),
                List.of(stats[0]),
                new ResultTable(List.of("Metric", "Value"), List.of(
                        List.of("Sequences", Long.toString(stats[0])),
                        List.of("SSR loci", Long.toString(stats[1])),
                        List.of("Repeated bases", Long.toString(stats[2])),
                        List.of("Truncated", Boolean.toString(capped[0]))
                )),
                List.of(new OutputArtifact(output, "SSR loci", "text/tab-separated-values")),
                metrics
        );
    }

    private static boolean isAcgt(String motif) {
        for (int i = 0; i < motif.length(); i++) {
            char base = motif.charAt(i);
            if (base != 'A' && base != 'C' && base != 'G' && base != 'T') {
                return false;
            }
        }
        return true;
    }
}
