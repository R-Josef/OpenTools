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
import io.opentools.api.tool.ValidationIssue;
import io.opentools.api.tool.ValidationResult;
import io.opentools.formats.fasta.FastaSequenceStore;
import io.opentools.formats.fasta.FastaWriter;
import io.opentools.formats.fasta.FastaRecord;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class GxfSequenceExtractTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "gxf.sequence.extract",
            "Extract Sequences from GFF3/GTF",
            "Extract feature, upstream, downstream, or flanked sequences from a genome and annotation.",
            "Genome annotation",
            List.of("extract CDS", "extract promoter", "GFF sequence", "启动子提取", "按注释提取序列"),
            List.of("FASTA", "FASTA.gz", "GFF3", "GTF"),
            List.of("FASTA", "TSV"),
            "1.1.0",
            List.of(
                    ToolParameter.requiredFile("genome", "Genome FASTA", "Reference genome whose sequence IDs match the annotation."),
                    ToolParameter.requiredFile("annotation", "GFF3/GTF", "Annotation defining the feature coordinates."),
                    ToolParameter.string("featureType", "Feature type", "GFF/GTF type such as CDS, exon, mRNA, or gene.", "CDS"),
                    ToolParameter.string("groupAttribute", "Group attribute", "Attribute used to group segments. Use AUTO for a type-aware default.", "AUTO"),
                    new ToolParameter(
                            "mode",
                            "Extraction mode",
                            "FEATURE concatenates feature segments. UPSTREAM and DOWNSTREAM extract strand-aware flanks. WITH_FLANKS uses the full genomic span plus flanks.",
                            ParameterType.ENUM,
                            false,
                            "FEATURE",
                            List.of("FEATURE", "UPSTREAM", "DOWNSTREAM", "WITH_FLANKS"),
                            null,
                            null,
                            false,
                            null
                    ),
                    ToolParameter.integer("upstream", "Upstream bases", "Upstream length relative to feature strand.", 0, 0, 100_000_000),
                    ToolParameter.integer("downstream", "Downstream bases", "Downstream length relative to feature strand.", 0, 0, 100_000_000),
                    ToolParameter.optionalOutputFile("output", "Output FASTA", "Leave blank to save inside the run directory."),
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
        if (request.string("featureType").isBlank()) {
            base = base.merge(ValidationResult.of(ValidationIssue.error(
                    "featureType", "Feature type is required.", "For example: CDS, exon, mRNA, or gene."
            )));
        }
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
            output = context.defaultOutputPath("extracted-features.fa");
        }
        Path errorsOutput = output.resolveSibling(stripExtension(output.getFileName().toString()) + "-errors.tsv");
        createParent(output);

        String featureType = request.string("featureType");
        String requestedAttribute = request.string("groupAttribute");
        String mode = request.string("mode").isBlank() ? "FEATURE" : request.string("mode");
        int upstream = request.integer("upstream");
        int downstream = request.integer("downstream");

        context.progress(0.05, "Reading annotation");
        Map<String, FeatureGroup> groups = new LinkedHashMap<>();
        long matchingRecords = 0;
        try (GxfReader reader = new GxfReader(request.path("annotation"))) {
            GxfRecord record;
            while ((record = reader.next()) != null) {
                context.cancellationToken().throwIfCancelled();
                if (!record.type().equalsIgnoreCase(featureType)) {
                    continue;
                }
                matchingRecords++;
                String groupId = groupId(record, requestedAttribute);
                String key = record.seqId() + "\u0000" + groupId;
                groups.computeIfAbsent(key, ignored -> new FeatureGroup(groupId))
                        .records.add(record);
            }
        }
        if (groups.isEmpty()) {
            throw new OpenToolException(
                    "FEATURE_TYPE_NOT_FOUND",
                    "No records with feature type '" + featureType + "' were found.",
                    "Run GFF3/GTF inspection to see available feature types."
            );
        }

        context.progress(0.25, "Loading genome");
        FastaSequenceStore genome = FastaSequenceStore.load(request.path("genome"));
        List<ExtractionError> errors = new ArrayList<>();
        long extracted = 0;
        long writtenBases = 0;
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            int groupIndex = 0;
            for (FeatureGroup group : groups.values()) {
                groupIndex++;
                context.cancellationToken().throwIfCancelled();
                try {
                    ExtractedSequence extractedSequence = extractGroup(group, genome, mode, upstream, downstream);
                    if (extractedSequence.sequence().isEmpty()) {
                        throw new OpenToolException(
                                "EMPTY_EXTRACTION",
                                "The requested region is empty after clipping to the chromosome.",
                                "Check the feature coordinates and flank lengths."
                        );
                    }
                    String header = safeHeader(extractedSequence.groupId())
                            + " " + extractedSequence.description();
                    FastaWriter.write(writer, new FastaRecord(
                            safeHeader(extractedSequence.groupId()),
                            header,
                            extractedSequence.sequence()
                    ), 80);
                    extracted++;
                    writtenBases += extractedSequence.sequence().length();
                } catch (Exception ex) {
                    errors.add(new ExtractionError(group.groupId, rootMessage(ex)));
                }
                if (groupIndex % 500 == 0) {
                    context.progress(0.25 + (0.65 * groupIndex / groups.size()),
                            "Processed " + groupIndex + " feature groups");
                }
            }
        }

        if (extracted == 0) {
            Files.deleteIfExists(output);
            throw new OpenToolException(
                    "NO_SEQUENCES_EXTRACTED",
                    "No feature sequences could be extracted: " + errors.stream().findFirst()
                            .map(ExtractionError::reason).orElse("unknown error"),
                    "Check genome/annotation sequence names, coordinates, and group attribute."
            );
        }
        writeErrors(errorsOutput, errors);
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("matchingRecords", matchingRecords);
        metrics.put("featureGroups", groups.size());
        metrics.put("extracted", extracted);
        metrics.put("failed", errors.size());
        metrics.put("writtenBases", writtenBases);
        metrics.put("genomeStorage", genome.storageMode());

        List<OutputArtifact> artifacts = new ArrayList<>();
        artifacts.add(new OutputArtifact(output, "Extracted feature sequences", "text/x-fasta"));
        artifacts.add(new OutputArtifact(errorsOutput, "Extraction errors", "text/tab-separated-values"));
        ResultTable table = new ResultTable(
                List.of("Metric", "Value"),
                List.of(
                        List.of("Matching records", Long.toString(matchingRecords)),
                        List.of("Feature groups", Integer.toString(groups.size())),
                        List.of("Extracted sequences", Long.toString(extracted)),
                        List.of("Failed groups", Integer.toString(errors.size())),
                        List.of("Written bases", Long.toString(writtenBases))
                )
        );
        context.progress(1.0, "Feature extraction completed");
        return new ToolResult(
                "Extracted " + extracted + " of " + groups.size() + " feature groups",
                List.of(extracted, groups.size()),
                errors.isEmpty() ? "No extraction errors" : errors.size() + " groups failed",
                List.of(errors.size()),
                table,
                artifacts,
                metrics
        );
    }

    private static ExtractedSequence extractGroup(
            FeatureGroup group,
            FastaSequenceStore genome,
            String mode,
            int upstream,
            int downstream
    ) {
        List<GxfRecord> records = group.records.stream()
                .sorted(Comparator.comparingLong(GxfRecord::start).thenComparingLong(GxfRecord::end))
                .toList();
        GxfRecord first = records.getFirst();
        for (GxfRecord record : records) {
            if (!record.seqId().equals(first.seqId())) {
                throw new OpenToolException(
                        "GROUP_SEQUENCE_CONFLICT",
                        "Group " + group.groupId + " spans multiple sequence IDs.",
                        "Choose a group attribute unique within each sequence."
                );
            }
            if (record.strand() != first.strand()) {
                throw new OpenToolException(
                        "GROUP_STRAND_CONFLICT",
                        "Group " + group.groupId + " contains conflicting strands.",
                        "Check the annotation."
                );
            }
        }
        long chromosomeLength = genome.length(first.seqId());
        if (chromosomeLength < 0) {
            throw new OpenToolException(
                    "SEQUENCE_NOT_FOUND",
                    "Sequence is missing from the genome: " + first.seqId(),
                    "Check genome and annotation naming."
            );
        }

        char strand = first.strand() == '.' ? '+' : first.strand();
        long minStart = records.stream().mapToLong(GxfRecord::start).min().orElseThrow();
        long maxEnd = records.stream().mapToLong(GxfRecord::end).max().orElseThrow();
        String sequence;
        String description;
        switch (mode) {
            case "UPSTREAM" -> {
                long[] range = upstreamRange(strand, minStart, maxEnd, upstream, chromosomeLength);
                sequence = orient(genome.subsequence(first.seqId(), range[0], range[1]), strand);
                description = first.seqId() + ":" + range[0] + "-" + range[1] + "(" + strand + ") upstream=" + upstream;
            }
            case "DOWNSTREAM" -> {
                long[] range = downstreamRange(strand, minStart, maxEnd, downstream, chromosomeLength);
                sequence = orient(genome.subsequence(first.seqId(), range[0], range[1]), strand);
                description = first.seqId() + ":" + range[0] + "-" + range[1] + "(" + strand + ") downstream=" + downstream;
            }
            case "WITH_FLANKS" -> {
                long rangeStart = Math.max(1, minStart - upstream);
                long rangeEnd = Math.min(chromosomeLength, maxEnd + downstream);
                sequence = orient(genome.subsequence(first.seqId(), rangeStart, rangeEnd), strand);
                description = first.seqId() + ":" + rangeStart + "-" + rangeEnd + "(" + strand
                        + ") mode=WITH_FLANKS";
            }
            default -> {
                sequence = featureSequence(records, genome, strand);
                description = first.seqId() + ":" + minStart + "-" + maxEnd + "(" + strand
                        + ") mode=FEATURE segments=" + records.size();
            }
        }
        return new ExtractedSequence(group.groupId, sequence, description);
    }

    private static String featureSequence(List<GxfRecord> records, FastaSequenceStore genome, char strand) {
        StringBuilder sequence = new StringBuilder();
        List<GxfRecord> ordered = new ArrayList<>(records);
        if (strand == '-') {
            ordered.sort(Comparator.comparingLong(GxfRecord::start).reversed()
                    .thenComparing(Comparator.comparingLong(GxfRecord::end).reversed()));
        } else {
            ordered.sort(Comparator.comparingLong(GxfRecord::start)
                    .thenComparingLong(GxfRecord::end));
        }
        for (GxfRecord record : ordered) {
            String segment = genome.subsequence(record.seqId(), record.start(), record.end());
            sequence.append(strand == '-' ? FastaSequenceStore.reverseComplement(segment) : segment);
        }
        int trim = firstCodonOffset(ordered);
        return trim > 0 && sequence.length() > trim ? sequence.substring(trim) : sequence.toString();
    }

    /**
     * Returns how many leading bases belong to a codon started upstream of the
     * first CDS segment. GFF3 phase 1 or 2 on a 5'-partial CDS means the
     * concatenated sequence starts mid-codon.
     */
    private static int firstCodonOffset(List<GxfRecord> ordered) {
        if (ordered.isEmpty()) {
            return 0;
        }
        boolean allCds = ordered.stream().allMatch(record -> "cds".equalsIgnoreCase(record.type()));
        if (!allCds) {
            return 0;
        }
        try {
            int phase = Integer.parseInt(ordered.getFirst().phase().trim());
            return phase == 1 || phase == 2 ? phase : 0;
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    private static long[] upstreamRange(char strand, long minStart, long maxEnd, int length, long chromosomeLength) {
        if (strand == '-') {
            long start = Math.max(1, maxEnd + 1);
            long end = Math.min(chromosomeLength, maxEnd + length);
            return new long[]{start, end};
        }
        long start = Math.max(1, minStart - length);
        long end = Math.max(0, minStart - 1);
        return new long[]{start, end};
    }

    private static long[] downstreamRange(char strand, long minStart, long maxEnd, int length, long chromosomeLength) {
        if (strand == '-') {
            long start = Math.max(1, minStart - length);
            long end = Math.max(0, minStart - 1);
            return new long[]{start, end};
        }
        long start = Math.max(1, maxEnd + 1);
        long end = Math.min(chromosomeLength, maxEnd + length);
        return new long[]{start, end};
    }

    private static String orient(String sequence, char strand) {
        return strand == '-' ? FastaSequenceStore.reverseComplement(sequence) : sequence;
    }

    private static String groupId(GxfRecord record, String requestedAttribute) {
        String value;
        if (requestedAttribute == null || requestedAttribute.isBlank() || requestedAttribute.equalsIgnoreCase("AUTO")) {
            String type = record.type().toLowerCase(Locale.ROOT);
            if (type.equals("cds") || type.equals("exon") || type.contains("utr")) {
                value = firstAvailable(record, "Parent", "transcript_id", "gene_id", "ID");
            } else if (type.equals("gene")) {
                value = firstAvailable(record, "ID", "gene_id", "Name");
            } else if (type.equals("mrna") || type.equals("transcript")) {
                value = firstAvailable(record, "ID", "transcript_id", "Parent");
            } else {
                value = firstAvailable(record, "ID", "Parent", "gene_id", "Name");
            }
        } else {
            value = record.firstAttribute(requestedAttribute);
        }
        if (value != null && !value.isBlank()) {
            return value;
        }
        return record.type() + ":" + record.seqId() + ":" + record.start() + "-" + record.end();
    }

    private static String firstAvailable(GxfRecord record, String... names) {
        return record.firstAttribute(names);
    }

    private static void writeErrors(Path output, List<ExtractionError> errors) throws Exception {
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            writer.write("group\treason");
            writer.newLine();
            for (ExtractionError error : errors) {
                writer.write(error.groupId);
                writer.write('\t');
                writer.write(error.reason.replace('\t', ' ').replace('\n', ' '));
                writer.newLine();
            }
        }
    }

    private static String safeHeader(String value) {
        return value.replaceAll("\\s+", "_");
    }

    private static void createParent(Path path) throws Exception {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private static String stripExtension(String fileName) {
        int separator = fileName.lastIndexOf('.');
        return separator < 0 ? fileName : fileName.substring(0, separator);
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static final class FeatureGroup {
        private final String groupId;
        private final List<GxfRecord> records = new ArrayList<>();

        private FeatureGroup(String groupId) {
            this.groupId = groupId;
        }
    }

    private record ExtractedSequence(String groupId, String sequence, String description) {
    }

    private record ExtractionError(String groupId, String reason) {
    }
}
