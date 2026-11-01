package io.opentools.analysis.sequence;

import io.opentools.api.tool.AbstractOpenTool;
import io.opentools.api.tool.OpenToolException;
import io.opentools.api.tool.OutputArtifact;
import io.opentools.api.tool.ParameterType;
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
import io.opentools.formats.fasta.FastaWriter;
import io.opentools.formats.fasta.GeneticCode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class SequenceOrfTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "sequence.orf",
            "Predict ORFs",
            "Find open reading frames in three forward and optionally three reverse frames using an NCBI genetic code.",
            "Sequence",
            List.of("ORF", "six frame", "open reading frame", "ORF 预测", "六框"),
            List.of("FASTA", "FASTA.gz"),
            List.of("FASTA", "FASTA.gz"),
            "1.2.0",
            List.of(
                    ToolParameter.requiredFile("input", "Input FASTA", "Nucleotide FASTA."),
                    ToolParameter.integer("threads", "Threads",
                            "0 uses AUTO for large files; 1 disables parallel record processing.",
                            0, 0, 64),
                    ToolParameter.integer("minAminoAcids", "Minimum amino acids", "Minimum ORF length.", 30, 1, 10_000_000),
                    new ToolParameter(
                            "geneticCode", "Genetic code",
                            "NCBI translation table code used for stop codons.",
                            ParameterType.ENUM, false, "1",
                            GeneticCode.supportedCodes(), null, null, false, null),
                    ToolParameter.string("startCodons", "Start codons", "Comma/whitespace-separated codons.", "ATG"),
                    ToolParameter.bool("bothStrands", "Both strands", "Scan reverse-complement frames.", true),
                    ToolParameter.bool("includePartial", "Include partial ORFs",
                            "Include start-to-end ORFs without a stop codon.", false),
                    ToolParameter.optionalOutputFile("output", "Output ORF FASTA",
                            "Leave blank to save in the run directory. A .gz suffix enables gzip."),
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
                .merge(FormatTaskSupport.validateOutput(request, request.path("input")));
    }

    @Override
    public TaskResult execute(ToolRequest request, TaskContext context) throws Exception {
        Path output = FormatTaskSupport.outputPath(request, context, "orfs.fa");
        String geneticCode = request.string("geneticCode");
        Set<String> stops = GeneticCode.stopCodons(geneticCode);
        Set<String> starts = codons(request.string("startCodons"));
        if (starts.isEmpty()) {
            throw new OpenToolException("START_CODON_REQUIRED", "At least one start codon is required.",
                    "Enter ATG or another triplet.");
        }
        int minimum = request.integer("minAminoAcids");
        int threads = OrderedParallelProcessor.resolveThreads(
                request.integer("threads"), Files.size(request.path("input")), 16L * 1024 * 1024);
        long[] totals = new long[5];
        FormatTaskSupport.writeAtomically(output, writer -> {
            try (FastaReader reader = new FastaReader(request.path("input"))) {
                OrderedParallelProcessor.process(
                        threads,
                        16,
                        context.cancellationToken(),
                        reader::next,
                        record -> {
                            java.util.List<OrfHit> hits = new ArrayList<>();
                            String plus = record.sequence().toUpperCase(Locale.ROOT);
                            hits.addAll(scan(
                                    record,
                                    plus,
                                    "+",
                                    starts,
                                    stops,
                                    minimum,
                                    request.bool("includePartial"),
                                    context
                            ));
                            if (request.bool("bothStrands")) {
                                String minus = SequenceAlphabet.reverseComplement(plus);
                                hits.addAll(scan(
                                        record,
                                        minus,
                                        "-",
                                        starts,
                                        stops,
                                        minimum,
                                        request.bool("includePartial"),
                                        context
                                ));
                            }
                            return hits;
                        },
                        hits -> {
                            for (OrfHit hit : hits) {
                                FastaWriter.write(writer, hit.record(), 80);
                                totals[0]++;
                                if (hit.complete()) {
                                    totals[1]++;
                                } else {
                                    totals[2]++;
                                }
                                if (hit.reverse()) {
                                    totals[4]++;
                                } else {
                                    totals[3]++;
                                }
                            }
                        }
                );
            }
        });
        if (totals[0] == 0) {
            throw new OpenToolException("NO_ORFS", "No ORFs passed the configured minimum length.",
                    "Lower minAminoAcids, add start codons, or include partial ORFs.");
        }
        context.progress(1.0, "ORF prediction completed");
        return new TaskResult(
                "Found " + totals[0] + " ORFs",
                "result.sequence.orf.summary", List.of(totals[0]),
                totals[3] + " forward ORFs and " + totals[4] + " reverse ORFs.",
                "result.sequence.orf.detail", List.of(totals[3], totals[4]),
                new ResultTable(List.of("Metric", "Value"), List.of(
                        List.of("ORFs", Long.toString(totals[0])),
                        List.of("Complete ORFs", Long.toString(totals[1])),
                        List.of("Partial ORFs", Long.toString(totals[2])),
                        List.of("Forward ORFs", Long.toString(totals[3])),
                        List.of("Reverse ORFs", Long.toString(totals[4]))
                )),
                List.of(new OutputArtifact(output, "Predicted ORFs",
                        io.opentools.formats.Compression.isGzip(output)
                                ? "application/gzip" : "text/x-fasta")),
                Map.of("orfs", totals[0], "complete", totals[1], "partial", totals[2],
                        "forward", totals[3], "reverse", totals[4],
                        "geneticCode", geneticCode, "geneticCodeName", GeneticCode.name(geneticCode),
                        "threads", threads, "parallelRecords", threads > 1)
        );
    }

    private static List<OrfHit> scan(
            FastaRecord record,
            String sequence,
            String strand,
            Set<String> starts,
            Set<String> stops,
            int minimum,
            boolean includePartial,
            TaskContext context
    ) throws Exception {
        List<OrfHit> hits = new ArrayList<>();
        for (int frame = 0; frame < 3; frame++) {
            int index = frame;
            while (index + 3 <= sequence.length()) {
                String codon = sequence.substring(index, index + 3);
                if (!starts.contains(codon)) {
                    index += 3;
                    continue;
                }
                int startCodon = index;
                int cursor = index + 3;
                boolean complete = false;
                while (cursor + 3 <= sequence.length()) {
                    if (stops.contains(sequence.substring(cursor, cursor + 3))) {
                        complete = true;
                        break;
                    }
                    cursor += 3;
                }
                if (!complete && !includePartial) {
                    index = startCodon + 3;
                    continue;
                }
                int endExclusive = complete ? cursor : sequence.length() - ((sequence.length() - startCodon) % 3);
                String orf = sequence.substring(startCodon, endExclusive);
                if (orf.length() / 3 >= minimum) {
                    long sourceStart = strand.equals("+")
                            ? startCodon + 1
                            : record.sequence().length() - endExclusive + 1;
                    long sourceEnd = strand.equals("+")
                            ? endExclusive
                            : record.sequence().length() - startCodon;
                    String id = record.id() + "|frame=" + strand + (frame + 1)
                            + "|start=" + sourceStart + "|end=" + sourceEnd;
                    String header = id + " partial=" + !complete + " source=" + record.id();
                    hits.add(new OrfHit(
                            new FastaRecord(id, header, orf),
                            complete,
                            strand.equals("-")
                    ));
                }
                index = complete ? cursor + 3 : sequence.length();
            }
            context.cancellationToken().throwIfCancelled();
        }
        return hits;
    }

    private static Set<String> codons(String value) {
        Set<String> result = new LinkedHashSet<>();
        for (String codon : value.toUpperCase(Locale.ROOT).split("[,;\\s]+")) {
            if (codon.matches("[ACGTU]{3}")) {
                result.add(codon.replace('U', 'T'));
            }
        }
        return result;
    }

    private record OrfHit(
            FastaRecord record,
            boolean complete,
            boolean reverse
    ) {
    }
}
