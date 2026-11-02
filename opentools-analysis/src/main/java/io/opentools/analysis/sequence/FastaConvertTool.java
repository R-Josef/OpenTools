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
import io.opentools.api.tool.ValidationResult;
import io.opentools.formats.Compression;
import io.opentools.formats.FormatToolSupport;
import io.opentools.formats.fasta.FastaReader;
import io.opentools.formats.fasta.FastaRecord;
import io.opentools.formats.fasta.FastaWriter;
import io.opentools.formats.table.DelimitedTableReader;
import io.opentools.formats.table.DelimitedTableWriter;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FastaConvertTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "fasta.convert",
            "Convert FASTA and Tables",
            "Convert FASTA to sequence TSV or ID lists, and TSV back to FASTA or ID lists.",
            "Sequence",
            List.of("fasta to tsv", "tsv to fasta", "sequence table", "FASTA 转换"),
            List.of("FASTA", "FASTA.gz", "TSV"),
            List.of("FASTA", "FASTA.gz", "TSV", "ID list"),
            "1.1.0",
            List.of(
                    ToolParameter.requiredFile("input", "Input file", "FASTA or tab-separated sequence table."),
                    new ToolParameter("mode", "Conversion mode", "Input and output representation.",
                            ParameterType.ENUM, false, "FASTA_TO_TSV",
                            List.of("FASTA_TO_TSV", "TSV_TO_FASTA", "FASTA_TO_IDS", "TSV_TO_IDS"),
                            null, null, false, null),
                    ToolParameter.optionalOutputFile("output", "Output file",
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
                .merge(FormatToolSupport.validateOutput(request, request.path("input")));
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        String mode = request.string("mode");
        Path output = FormatToolSupport.outputPath(request, context,
                mode.endsWith("_TO_FASTA") ? "converted.fa"
                        : mode.endsWith("_TO_IDS") ? "ids.txt" : "sequences.tsv");
        long[] totals = new long[2];
        FormatToolSupport.writeAtomically(output, writer -> {
            if (mode.startsWith("FASTA_")) {
                totals[0] = fromFasta(request.path("input"), writer, mode);
            } else {
                totals[0] = fromTsv(request.path("input"), writer, mode);
            }
            totals[1] = totals[0];
            if (totals[0] == 0) {
                throw new OpenToolException("EMPTY_INPUT", "No sequence records were found.", "Check the input file.");
            }
        });
        context.progress(1.0, "Sequence conversion completed");
        return new ToolResult(
                "Converted " + totals[0] + " sequence records",
                "result.fasta.convert.summary", List.of(totals[0], mode),
                "Output format: " + outputFormat(mode) + ".",
                "result.fasta.convert.detail", List.of(),
                new ResultTable(List.of("Metric", "Value"), List.of(
                        List.of("Records", Long.toString(totals[0])),
                        List.of("Mode", mode)
                )),
                List.of(new OutputArtifact(output, "Converted sequence data", mediaType(mode, output))),
                Map.of("records", totals[0], "mode", mode)
        );
    }

    private static long fromFasta(Path input, java.io.BufferedWriter writer, String mode) throws Exception {
        long count = 0;
        try (FastaReader reader = new FastaReader(input)) {
            FastaRecord record;
            if ("FASTA_TO_TSV".equals(mode)) {
                DelimitedTableWriter.writeRow(writer, List.of("id", "header", "sequence", "length"), '\t');
            }
            while ((record = reader.next()) != null) {
                if ("FASTA_TO_TSV".equals(mode)) {
                    DelimitedTableWriter.writeRow(writer, List.of(
                            record.id(),
                            record.header(),
                            record.sequence(),
                            Integer.toString(record.sequence().length())
                    ), '\t');
                } else {
                    writer.write(record.id());
                    writer.newLine();
                }
                count++;
            }
        }
        return count;
    }

    private static long fromTsv(Path input, java.io.BufferedWriter writer, String mode) throws Exception {
        long count = 0;
        try (DelimitedTableReader reader = new DelimitedTableReader(input, '\t', true, true)) {
            List<String> fields;
            Map<String, Integer> header = null;
            while ((fields = reader.nextRow()) != null) {
                if (header == null) {
                    Map<String, Integer> candidate = index(fields);
                    if (candidate.containsKey("id")
                            && (candidate.containsKey("sequence") || "TSV_TO_IDS".equals(mode))) {
                        header = candidate;
                        continue;
                    }
                    if (fields.size() < 2) {
                        throw new OpenToolException("SEQUENCE_TSV_INVALID",
                                "Sequence TSV needs id and sequence columns.",
                                "Use id/header/sequence or id/sequence columns.");
                    }
                    header = Map.of("id", 0, "header", 0, "sequence", 1);
                }
                String id = field(fields, header.get("id"));
                String fullHeader = header.containsKey("header") ? field(fields, header.get("header")) : id;
                validateSingleLine(id, "id");
                fullHeader = fullHeader.replace('\r', ' ').replace('\n', ' ');
                if (!fullHeader.isBlank() && !fullHeader.equals(id)
                        && !fullHeader.matches(java.util.regex.Pattern.quote(id) + "\\s.*")) {
                    fullHeader = id + " " + fullHeader;
                }
                if ("TSV_TO_IDS".equals(mode)) {
                    writer.write(id);
                    writer.newLine();
                } else {
                    String sequence = field(fields, header.get("sequence")).replaceAll("\\s+", "");
                    FastaWriter.write(writer, new FastaRecord(id,
                            fullHeader.isBlank() ? id : fullHeader, sequence), 80);
                }
                count++;
            }
        }
        return count;
    }

    private static Map<String, Integer> index(List<String> fields) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int index = 0; index < fields.size(); index++) {
            result.put(fields.get(index).trim().toLowerCase(), index);
        }
        return result;
    }

    private static String field(List<String> fields, int index) {
        return index < fields.size() ? fields.get(index) : "";
    }

    private static String outputFormat(String mode) {
        return switch (mode) {
            case "FASTA_TO_TSV", "TSV_TO_IDS" -> "TSV/ID list";
            case "TSV_TO_FASTA" -> "FASTA";
            default -> "ID list";
        };
    }

    private static String mediaType(String mode, Path output) {
        if (mode.endsWith("_TO_FASTA")) {
            return Compression.isGzip(output) ? "application/gzip" : "text/x-fasta";
        }
        if (mode.endsWith("_TO_TSV")) {
            return "text/tab-separated-values";
        }
        return "text/plain";
    }

    private static void validateSingleLine(String value, String column) {
        if (value.indexOf('\t') >= 0 || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new OpenToolException(
                    "SEQUENCE_TSV_INVALID_FIELD",
                    "The " + column + " column must contain a single-line value.",
                    "Put line breaks only inside a quoted sequence field."
            );
        }
    }
}
