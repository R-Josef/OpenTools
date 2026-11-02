package io.opentools.analysis.sequence;

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
import io.opentools.core.util.OrderedParallelProcessor;
import io.opentools.formats.fasta.FastaReader;
import io.opentools.formats.fasta.FastaRecord;
import io.opentools.formats.fasta.FastaWriter;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class FastaTransformTool extends AbstractOpenTool {
    private static final Set<Character> NUCLEOTIDE_CHARACTERS = Set.of(
            'A', 'C', 'G', 'T', 'U', 'R', 'Y', 'S', 'W', 'K', 'M', 'B', 'D', 'H', 'V', 'N', 'X', '-', '.'
    );
    private static final Set<Character> PROTEIN_CHARACTERS = Set.of(
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'K', 'L', 'M', 'N',
            'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', '*', '-', '.'
    );
    private static final Set<Character> PROTEIN_MARKERS = Set.of('E', 'F', 'I', 'L', 'P', 'Q', 'J', 'O', 'Z');

    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "fasta.transform",
            "Transform FASTA",
            "Stream FASTA records through strand, complement, case, and invalid-character transformations.",
            "Sequence",
            List.of("fasta transform", "reverse complement", "complement", "sequence case", "FASTA 变换", "反向互补"),
            List.of("FASTA", "FASTA.gz"),
            List.of("FASTA", "FASTA.gz", "TSV report"),
            "1.1.0",
            List.of(
                    ToolParameter.requiredFile("input", "Input FASTA", "A FASTA file, optionally gzip-compressed."),
                    ToolParameter.integer("threads", "Threads",
                            "0 uses AUTO for large files; 1 disables parallel record processing.",
                            0, 0, 64),
                    new ToolParameter(
                            "direction",
                            "Direction",
                            "Transform sequence direction. Nucleotide complements preserve case.",
                            ParameterType.ENUM,
                            false,
                            "FORWARD",
                            List.of("FORWARD", "REVERSE", "COMPLEMENT", "REVERSE_COMPLEMENT"),
                            null,
                            null,
                            false,
                            null
                    ),
                    new ToolParameter(
                            "caseMode",
                            "Letter case",
                            "Preserve, uppercase, or lowercase sequence letters.",
                            ParameterType.ENUM,
                            false,
                            "KEEP",
                            List.of("KEEP", "UPPERCASE", "LOWERCASE"),
                            null,
                            null,
                            false,
                            null
                    ),
                    new ToolParameter(
                            "sequenceType",
                            "Sequence type",
                            "AUTO infers nucleotide or protein from the sequence alphabet.",
                            ParameterType.ENUM,
                            false,
                            "AUTO",
                            List.of("AUTO", "NUCLEIC_ACID", "PROTEIN"),
                            null,
                            null,
                            false,
                            null
                    ),
                    ToolParameter.bool("cleanInvalid", "Clean invalid characters",
                            "Remove characters outside the selected sequence alphabet and report each affected record.",
                            false),
                    ToolParameter.optionalOutputFile("output", "Output FASTA",
                            "Leave blank to save inside the run directory. A .gz suffix enables gzip output."),
                    ToolParameter.bool("overwrite", "Overwrite output",
                            "Allow replacing an existing custom output file.", false)
            )
    );

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ValidationResult validate(ToolRequest request) {
        ValidationResult result = validateParameters(request)
                .merge(SequenceToolSupport.validateOutput(request, request.path("input")));
        boolean complement = "COMPLEMENT".equals(request.string("direction"))
                || "REVERSE_COMPLEMENT".equals(request.string("direction"));
        if (complement && "PROTEIN".equals(request.string("sequenceType"))) {
            result = result.merge(ValidationResult.of(ValidationIssue.error(
                    "sequenceType",
                    "Protein sequences cannot be complemented with a nucleotide complement table.",
                    "Choose AUTO or NUCLEIC_ACID, or use REVERSE instead."
            )));
        }
        return result;
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        Path input = request.path("input");
        Path output = SequenceToolSupport.outputPath(request, context, "transformed.fa");
        Path report = context.defaultOutputPath("transform-report.tsv");
        String direction = request.string("direction");
        String caseMode = request.string("caseMode");
        String configuredType = request.string("sequenceType");
        boolean cleanInvalid = request.bool("cleanInvalid");
        int threads = OrderedParallelProcessor.resolveThreads(
                request.integer("threads"), Files.size(input), 16L * 1024 * 1024);

        long[] totals = new long[6];
        context.log("Transforming FASTA: " + input);
        SequenceToolSupport.writeAtomically(output, report, (outputWriter, reportWriter) -> {
            writeReportHeader(reportWriter);

            try (FastaReader reader = new FastaReader(input)) {
                long[] sourceIndex = new long[1];
                OrderedParallelProcessor.process(
                        threads,
                        32,
                        context.cancellationToken(),
                        () -> {
                            FastaRecord record = reader.next();
                            return record == null
                                    ? null
                                    : new NumberedRecord(++sourceIndex[0], record);
                        },
                        numbered -> transformRecord(
                                numbered,
                                direction,
                                caseMode,
                                configuredType,
                                cleanInvalid
                        ),
                        result -> {
                            totals[0]++;
                            FastaWriter.write(outputWriter, result.transformed(), 80);
                            totals[1] += result.inputLength();
                            totals[2] += result.outputLength();
                            totals[3] += result.removedCount();
                            if ("PROTEIN".equals(result.type())) {
                                totals[5]++;
                            } else {
                                totals[4]++;
                            }
                            writeReportRow(
                                    reportWriter,
                                    result.recordNumber(),
                                    result.source(),
                                    result.type(),
                                    result.inputLength(),
                                    result.outputLength(),
                                    result.removedCount(),
                                    result.removedCharacters()
                            );
                            if (totals[0] % 10_000 == 0) {
                                context.log("Processed " + totals[0] + " FASTA records");
                            }
                        }
                );
            }
            if (totals[0] == 0) {
                throw new OpenToolException(
                        "EMPTY_FASTA",
                        "No FASTA records were found.",
                        "Check that the input starts with '>' headers."
                );
            }
        });

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("direction", direction);
        metrics.put("caseMode", caseMode);
        metrics.put("sequenceType", configuredType);
        metrics.put("cleanInvalid", cleanInvalid);
        metrics.put("sequences", totals[0]);
        metrics.put("inputBases", totals[1]);
        metrics.put("outputBases", totals[2]);
        metrics.put("removedCharacters", totals[3]);
        metrics.put("nucleotideSequences", totals[4]);
        metrics.put("proteinSequences", totals[5]);
        metrics.put("threads", threads);
        metrics.put("parallelRecords", threads > 1);
        ResultTable table = new ResultTable(
                List.of("Metric", "Value"),
                List.of(
                        List.of("Sequences", Long.toString(totals[0])),
                        List.of("Input bases", Long.toString(totals[1])),
                        List.of("Output bases", Long.toString(totals[2])),
                        List.of("Removed characters", Long.toString(totals[3])),
                        List.of("Nucleotide sequences", Long.toString(totals[4])),
                        List.of("Protein sequences", Long.toString(totals[5]))
                )
        );
        context.progress(1.0, "FASTA transform completed");
        return new ToolResult(
                "Transformed " + totals[0] + " FASTA records",
                "result.fasta.transform.summary",
                List.of(totals[0]),
                "Removed " + totals[3] + " invalid characters; details are in transform-report.tsv.",
                "result.fasta.transform.detail",
                List.of(totals[3]),
                table,
                List.of(
                        new OutputArtifact(output, "Transformed FASTA", mediaType(output)),
                        new OutputArtifact(report, "Transform report", "text/tab-separated-values")
                ),
                metrics
        );
    }

    private static TransformResult transformRecord(
            NumberedRecord numbered,
            String direction,
            String caseMode,
            String configuredType,
            boolean cleanInvalid
    ) {
        FastaRecord record = numbered.record();
        requireId(record);
        String type = resolveSequenceType(configuredType, record.sequence());
        String source = record.sequence();
        StringBuilder cleaned = new StringBuilder(source.length());
        Set<Character> removed = new LinkedHashSet<>();
        for (int index = 0; index < source.length(); index++) {
            char value = source.charAt(index);
            if (cleanInvalid && !isAllowed(type, value)) {
                removed.add(value);
            } else {
                cleaned.append(value);
            }
        }
        if (!cleanInvalid) {
            assertValidAlphabet(cleaned, type, record.id());
        }
        String transformed = transform(cleaned.toString(), direction, type, record.id());
        transformed = applyCase(transformed, caseMode);
        return new TransformResult(
                numbered.recordNumber(),
                record,
                new FastaRecord(record.id(), record.header(), transformed),
                type,
                source.length(),
                transformed.length(),
                source.length() - cleaned.length(),
                Collections.unmodifiableSet(new LinkedHashSet<>(removed))
        );
    }

    private static String resolveSequenceType(String configuredType, String sequence) {
        if (!"AUTO".equals(configuredType)) {
            return configuredType;
        }
        for (int index = 0; index < sequence.length(); index++) {
            if (PROTEIN_MARKERS.contains(Character.toUpperCase(sequence.charAt(index)))) {
                return "PROTEIN";
            }
        }
        return "NUCLEIC_ACID";
    }

    private static boolean isAllowed(String type, char value) {
        char upper = Character.toUpperCase(value);
        return "PROTEIN".equals(type)
                ? PROTEIN_CHARACTERS.contains(upper)
                : NUCLEOTIDE_CHARACTERS.contains(upper);
    }

    private static void assertValidAlphabet(StringBuilder sequence, String type, String id) {
        for (int index = 0; index < sequence.length(); index++) {
            char value = sequence.charAt(index);
            if (!isAllowed(type, value)) {
                throw new OpenToolException(
                        "INVALID_SEQUENCE_CHARACTER",
                        "Sequence " + id + " contains an invalid " + type.toLowerCase(Locale.ROOT)
                                + " character "
                                + SequenceToolSupport.codepoint(value) + ".",
                        "Enable cleanInvalid or correct the sequence alphabet."
                );
            }
        }
    }

    private static void requireNucleotide(String type, String id) {
        if ("PROTEIN".equals(type)) {
            throw new OpenToolException(
                    "SEQUENCE_TYPE_MISMATCH",
                    "Sequence " + id + " was detected as protein and cannot be complemented.",
                    "Use REVERSE, or set sequenceType to NUCLEIC_ACID for nucleotide data."
            );
        }
    }

    private static String transform(String sequence, String direction, String type, String id) {
        return switch (direction) {
            case "REVERSE" -> new StringBuilder(sequence).reverse().toString();
            case "COMPLEMENT" -> {
                requireNucleotide(type, id);
                yield complement(sequence);
            }
            case "REVERSE_COMPLEMENT" -> {
                requireNucleotide(type, id);
                yield complement(new StringBuilder(sequence).reverse().toString());
            }
            default -> sequence;
        };
    }

    private static String complement(String sequence) {
        StringBuilder result = new StringBuilder(sequence.length());
        for (int index = 0; index < sequence.length(); index++) {
            char source = sequence.charAt(index);
            char upper = Character.toUpperCase(source);
            char mapped = switch (upper) {
                case 'A' -> 'T';
                case 'T' -> 'A';
                case 'U' -> 'A';
                case 'C' -> 'G';
                case 'G' -> 'C';
                case 'R' -> 'Y';
                case 'Y' -> 'R';
                case 'S' -> 'S';
                case 'W' -> 'W';
                case 'K' -> 'M';
                case 'M' -> 'K';
                case 'B' -> 'V';
                case 'V' -> 'B';
                case 'D' -> 'H';
                case 'H' -> 'D';
                case 'N' -> 'N';
                case 'X' -> 'X';
                default -> upper;
            };
            result.append(Character.isLowerCase(source) ? Character.toLowerCase(mapped) : mapped);
        }
        return result.toString();
    }

    private static String applyCase(String sequence, String caseMode) {
        return switch (caseMode) {
            case "UPPERCASE" -> sequence.toUpperCase(Locale.ROOT);
            case "LOWERCASE" -> sequence.toLowerCase(Locale.ROOT);
            default -> sequence;
        };
    }

    private static void requireId(FastaRecord record) {
        if (record.id().isBlank()) {
            throw new OpenToolException(
                    "FASTA_EMPTY_ID",
                    "A FASTA record has no identifier.",
                    "Add a non-empty identifier after the '>' character."
            );
        }
    }

    private static void writeReportHeader(BufferedWriter writer) throws Exception {
        writer.write("record\tid\tstatus\ttype\tinput_length\toutput_length\tremoved_count\tremoved_characters\theader");
        writer.newLine();
    }

    private static void writeReportRow(
            BufferedWriter writer,
            long recordNumber,
            FastaRecord record,
            String type,
            long inputLength,
            long outputLength,
            long removedCount,
            Set<Character> removed
    ) throws Exception {
        writer.write(Long.toString(recordNumber));
        writer.write('\t');
        writer.write(SequenceToolSupport.escapeTsv(record.id()));
        writer.write("\tOK\t");
        writer.write(type);
        writer.write('\t');
        writer.write(Long.toString(inputLength));
        writer.write('\t');
        writer.write(Long.toString(outputLength));
        writer.write('\t');
        writer.write(Long.toString(removedCount));
        writer.write('\t');
        writer.write(String.join(",", removed.stream().map(SequenceToolSupport::codepoint).toList()));
        writer.write('\t');
        writer.write(SequenceToolSupport.escapeTsv(record.header()));
        writer.newLine();
    }

    private static String mediaType(Path output) {
        return io.opentools.formats.Compression.isGzip(output)
                ? "application/gzip"
                : "text/x-fasta";
    }

    private record NumberedRecord(long recordNumber, FastaRecord record) {
    }

    private record TransformResult(
            long recordNumber,
            FastaRecord source,
            FastaRecord transformed,
            String type,
            long inputLength,
            long outputLength,
            long removedCount,
            Set<Character> removedCharacters
    ) {
    }
}
