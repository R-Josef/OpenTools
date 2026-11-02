package io.opentools.formats.fasta;

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
import io.opentools.formats.FormatToolSupport;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CdsTranslateTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "fasta.translate-cds",
            "Translate CDS to Protein",
            "Translate nucleotide FASTA records using an NCBI genetic code.",
            "Sequence",
            List.of("CDS translation", "protein translation", "CDS 翻译", "蛋白翻译"),
            List.of("FASTA", "FASTA.gz"),
            List.of("FASTA"),
            "1.2.0",
            List.of(
                    ToolParameter.requiredFile("input", "Input CDS FASTA", "Nucleotide sequences in FASTA format."),
                    ToolParameter.integer("threads", "Threads",
                            "0 uses AUTO for large files; 1 disables parallel record processing.",
                            0, 0, 64),
                    new ToolParameter(
                            "frame",
                            "Reading frame",
                            "Translate from the first, second, or third base.",
                            ParameterType.ENUM,
                            false,
                            "1",
                            List.of("1", "2", "3"),
                            null,
                            null,
                            false,
                            null
                    ),
                    new ToolParameter(
                            "geneticCode",
                            "Genetic code",
                            "NCBI translation table code.",
                            ParameterType.ENUM,
                            false,
                            "1",
                            GeneticCode.supportedCodes(),
                            null,
                            null,
                            false,
                            null
                    ),
                    ToolParameter.bool("trimTrailingStop", "Trim trailing stop", "Remove one terminal '*' from each translated record.", true),
                    ToolParameter.optionalOutputFile("output", "Output protein FASTA", "Leave blank to save inside the run directory."),
                    ToolParameter.bool("overwrite", "Overwrite output", "Allow replacing an existing custom output file.", false)
            )
    );

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ValidationResult validate(ToolRequest request) {
        ValidationResult base = validateParameters(request);
        Path output = request.path("output");
        if (output != null && Files.exists(output) && !request.bool("overwrite")) {
            base = base.merge(ValidationResult.of(ValidationIssue.error(
                    "output", "Output file already exists: " + output, "Choose another path or enable overwrite."
            )));
        }
        return base;
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        Path output = request.path("output");
        if (output == null || output.toString().isBlank()) {
            output = context.defaultOutputPath("translated.fa");
        }
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        int frame = Math.max(1, Math.min(3, request.integer("frame")));
        String geneticCode = request.string("geneticCode");
        Map<String, Character> translationTable = GeneticCode.table(geneticCode);
        boolean trimTrailingStop = request.bool("trimTrailingStop");
        int threads = OrderedParallelProcessor.resolveThreads(
                request.integer("threads"), Files.size(request.path("input")), 16L * 1024 * 1024);
        long[] totals = new long[4];

        FormatToolSupport.writeAtomically(output, writer -> {
            try (FastaReader reader = new FastaReader(request.path("input"))) {
                OrderedParallelProcessor.process(
                        threads,
                        32,
                        context.cancellationToken(),
                        reader::next,
                        record -> translateRecord(
                                record,
                                frame,
                                translationTable,
                                trimTrailingStop
                        ),
                        result -> {
                            if (result.incomplete()) {
                                totals[2]++;
                            }
                            if (result.translated() == null) {
                                return;
                            }
                            totals[3] += result.ambiguousCodons();
                            FastaWriter.write(writer, result.translated(), 80);
                            totals[0]++;
                            totals[1] += result.translated().sequence().length();
                        }
                );
            }
            if (totals[0] == 0) {
                throw new OpenToolException(
                        "NO_TRANSLATABLE_RECORDS",
                        "No CDS records could be translated.",
                        "Check reading frame and sequence length."
                );
            }
        });

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("sequences", totals[0]);
        metrics.put("aminoAcids", totals[1]);
        metrics.put("incompleteRecords", totals[2]);
        metrics.put("ambiguousCodons", totals[3]);
        metrics.put("frame", frame);
        metrics.put("geneticCode", geneticCode);
        metrics.put("geneticCodeName", GeneticCode.name(geneticCode));
        metrics.put("threads", threads);
        metrics.put("parallelRecords", threads > 1);
        ResultTable table = new ResultTable(
                List.of("Metric", "Value"),
                List.of(
                        List.of("Translated sequences", Long.toString(totals[0])),
                        List.of("Amino acids", Long.toString(totals[1])),
                        List.of("Incomplete records", Long.toString(totals[2])),
                        List.of("Ambiguous codons", Long.toString(totals[3]))
                )
        );
        context.progress(1.0, "CDS translation completed");
        return new ToolResult(
                "Translated " + totals[0] + " sequences",
                "result.fasta.translate.summary",
                List.of(totals[0]),
                totals[2] + " incomplete records; " + totals[3] + " ambiguous codons",
                "result.fasta.translate.detail",
                List.of(totals[2], totals[3]),
                table,
                List.of(new OutputArtifact(output, "Translated protein FASTA", "text/x-fasta")),
                metrics
        );
    }

    private static TranslationResult translateRecord(
            FastaRecord record,
            int frame,
            Map<String, Character> translationTable,
            boolean trimTrailingStop
    ) {
        String nucleotide = record.sequence().toUpperCase(Locale.ROOT).replace('U', 'T');
        int offset = frame - 1;
        if (offset >= nucleotide.length()) {
            return new TranslationResult(null, 0, true);
        }
        String coding = nucleotide.substring(offset);
        boolean incomplete = coding.length() % 3 != 0;
        StringBuilder protein = new StringBuilder(coding.length() / 3);
        long ambiguous = 0;
        for (int start = 0; start + 3 <= coding.length(); start += 3) {
            String codon = coding.substring(start, start + 3);
            Character aminoAcid = translationTable.get(codon);
            if (aminoAcid == null) {
                aminoAcid = 'X';
                ambiguous++;
            }
            protein.append(aminoAcid);
        }
        if (trimTrailingStop && !protein.isEmpty()
                && protein.charAt(protein.length() - 1) == '*') {
            protein.setLength(protein.length() - 1);
        }
        String header = record.header() + " frame=" + frame;
        return new TranslationResult(
                new FastaRecord(record.id(), header, protein.toString()),
                ambiguous,
                incomplete
        );
    }

    private record TranslationResult(
            FastaRecord translated,
            long ambiguousCodons,
            boolean incomplete
    ) {
    }

}
