package io.opentools.formats.gxf;

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
import io.opentools.formats.FormatToolSupport;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class GxfRepresentativeTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "gxf.representative",
            "Select Representative Transcripts",
            "Select one representative transcript per gene by transcript span or CDS length.",
            "Genome annotation",
            List.of("representative transcript", "longest transcript", "one transcript per gene", "代表转录本"),
            List.of("GFF3", "GTF", "GFF3.gz", "GTF.gz"),
            List.of("GFF3", "GTF"),
            "1.1.0",
            List.of(
                    ToolParameter.requiredFile("input", "Input annotation", "GFF3 or GTF."),
                    ToolParameter.string("transcriptTypes", "Transcript types", "Types treated as transcript records.", "mRNA,transcript"),
                    ToolParameter.string("groupAttribute", "Gene attribute",
                            "Gene ID attribute. AUTO uses Parent for GFF3 and gene_id for GTF-style records.", "AUTO"),
                    new ToolParameter("scoreMode", "Selection score", "How the representative transcript is selected.",
                            ParameterType.ENUM, false, "LONGEST_TRANSCRIPT",
                            List.of("LONGEST_TRANSCRIPT", "LONGEST_CDS", "FIRST"), null, null, false, null),
                    ToolParameter.optionalOutputFile("output", "Output annotation", "Leave blank to save in the run directory."),
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
        Path input = request.path("input");
        Path output = FormatToolSupport.outputPath(request, context, "representative.gff3");
        Set<String> transcriptTypes = AnnotationTaskSupport.tokens(request.string("transcriptTypes"), true);
        long[] sourceRecords = new long[1];
        Map<String, Candidate> candidates;
        try (GxfReader reader = new GxfReader(input)) {
            candidates = collectCandidates(
                    reader,
                    transcriptTypes,
                    request.string("groupAttribute"),
                    sourceRecords,
                    context
            );
        }
        if (sourceRecords[0] == 0) {
            throw new OpenToolException("EMPTY_ANNOTATION", "No annotation records were found.", "Check the input.");
        }
        if (candidates.isEmpty()) {
            throw new OpenToolException("NO_TRANSCRIPTS", "No transcript candidates were found.",
                    "Check transcriptTypes or transcript_id/Parent attributes.");
        }
        Map<String, Candidate> representatives = chooseRepresentatives(candidates.values(), request.string("scoreMode"));
        Set<String> transcriptIds = representatives.keySet();
        Set<String> geneIds = representatives.values().stream().map(Candidate::geneId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        long[] totals = new long[2];

        FormatToolSupport.writeAtomically(output, writer -> {
            long written = 0;
            try (GxfReader reader = new GxfReader(input)) {
                GxfRecord record;
                while ((record = reader.next()) != null) {
                    context.cancellationToken().throwIfCancelled();
                    String id = first(record, "ID", "transcript_id");
                    boolean selected = transcriptIds.contains(id)
                            || (geneIds.contains(id) && "gene".equalsIgnoreCase(record.type()))
                            || AnnotationTaskSupport.values(record, "Parent").stream().anyMatch(transcriptIds::contains)
                            || transcriptIds.contains(record.firstAttribute("transcript_id"));
                    if (selected) {
                        GxfWriter.write(writer, record);
                        written++;
                    }
                }
            }
            totals[0] = representatives.size();
            totals[1] = written;
        });

        context.progress(1.0, "Representative transcript selection completed");
        return new ToolResult(
                "Selected " + totals[0] + " representative transcripts",
                "result.gxf.representative.summary", List.of(totals[0]),
                totals[1] + " annotation records were written.",
                "result.gxf.representative.detail", List.of(totals[1]),
                new ResultTable(List.of("Metric", "Value"), List.of(
                        List.of("Candidate transcripts", Integer.toString(candidates.size())),
                        List.of("Representative transcripts", Long.toString(totals[0])),
                        List.of("Output records", Long.toString(totals[1]))
                )),
                List.of(new OutputArtifact(output, "Representative annotation", "text/plain")),
                Map.of("candidates", candidates.size(), "representatives", totals[0],
                        "representativeIds", List.copyOf(transcriptIds), "outputRecords", totals[1],
                        "sourceRecords", sourceRecords[0], "annotationMemory", "metadata-only")
        );
    }

    private static Map<String, Candidate> collectCandidates(
            GxfReader reader,
            Set<String> transcriptTypes,
            String configuredGroupAttribute,
            long[] sourceRecords,
            ToolExecutionContext context
    ) throws Exception {
        Map<String, Candidate> candidates = new LinkedHashMap<>();
        Map<String, String> childToGene = new LinkedHashMap<>();
        Map<String, Long> cdsLengths = new LinkedHashMap<>();
        GxfRecord record;
        while ((record = reader.next()) != null) {
            context.cancellationToken().throwIfCancelled();
            sourceRecords[0]++;
            String transcriptId = first(record, "transcript_id");
            if (transcriptId == null && transcriptTypes.contains(record.type().toLowerCase(Locale.ROOT))) {
                transcriptId = first(record, "ID");
            }
            if (transcriptId == null) {
                continue;
            }
            String gene = groupAttribute(record, configuredGroupAttribute, transcriptId);
            childToGene.put(transcriptId, gene);
            if (transcriptTypes.contains(record.type().toLowerCase(Locale.ROOT))) {
                candidates.merge(transcriptId, new Candidate(gene, transcriptId, record.length(), record.lineNumber()),
                        Candidate::mergeSpan);
            } else if ("CDS".equalsIgnoreCase(record.type())) {
                cdsLengths.merge(transcriptId, record.length(), Long::sum);
            }
        }
        for (Map.Entry<String, String> entry : childToGene.entrySet()) {
            candidates.computeIfAbsent(entry.getKey(),
                    id -> new Candidate(entry.getValue(), id, 0, Long.MAX_VALUE));
        }
        return candidates.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().withCds(cdsLengths.getOrDefault(entry.getKey(), 0L)),
                (left, right) -> left,
                LinkedHashMap::new
        ));
    }

    private static Map<String, Candidate> chooseRepresentatives(
            java.util.Collection<Candidate> candidates,
            String scoreMode
    ) {
        Comparator<Candidate> comparator = switch (scoreMode) {
            case "LONGEST_CDS" -> Comparator.comparingLong(Candidate::cdsLength)
                    .thenComparingLong(Candidate::span).thenComparing(Candidate::transcriptId);
            case "FIRST" -> Comparator.comparingLong(Candidate::lineNumber);
            default -> Comparator.comparingLong(Candidate::span)
                    .thenComparingLong(Candidate::cdsLength).thenComparing(Candidate::transcriptId);
        };
        Map<String, Candidate> byGene = new LinkedHashMap<>();
        for (Candidate candidate : candidates) {
            byGene.merge(candidate.geneId(), candidate,
                    (left, right) -> comparator.compare(left, right) >= 0 ? left : right);
        }
        Map<String, Candidate> result = new LinkedHashMap<>();
        for (Candidate candidate : byGene.values()) {
            result.put(candidate.transcriptId(), candidate);
        }
        return result;
    }

    private static String groupAttribute(GxfRecord record, String configured, String transcriptId) {
        if (!"AUTO".equalsIgnoreCase(configured)) {
            String value = record.firstAttribute(configured);
            return value == null ? transcriptId : value;
        }
        String gene = record.firstAttribute("Parent");
        if (gene == null) {
            gene = record.firstAttribute("gene_id", "GeneID", "gene");
        }
        return gene == null ? transcriptId : gene;
    }

    private static String first(GxfRecord record, String... names) {
        return record.firstAttribute(names);
    }

    private record Candidate(String geneId, String transcriptId, long span, long lineNumber, long cdsLength) {
        Candidate(String geneId, String transcriptId, long span, long lineNumber) {
            this(geneId, transcriptId, span, lineNumber, 0L);
        }

        Candidate mergeSpan(Candidate other) {
            return new Candidate(geneId, transcriptId, Math.max(span, other.span),
                    Math.min(lineNumber, other.lineNumber), Math.max(cdsLength, other.cdsLength));
        }

        Candidate withCds(long value) {
            return new Candidate(geneId, transcriptId, span, lineNumber, value);
        }
    }
}
