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
import io.opentools.formats.fasta.FastaSequenceStore;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GxfGenomeMatchTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "gxf.genome.match",
            "Check Genome and Annotation Match",
            "Compare annotation sequence IDs and coordinates with a genome FASTA.",
            "Genome annotation",
            List.of("GFF genome match", "GTF genome match", "基因组注释匹配"),
            List.of("FASTA", "FASTA.gz", "GFF3", "GTF"),
            List.of("TSV"),
            "1.0.0",
            List.of(
                    ToolParameter.requiredFile("genome", "Genome FASTA", "Reference genome used by the annotation."),
                    ToolParameter.requiredFile("annotation", "GFF3/GTF", "Annotation to validate."),
                    ToolParameter.optionalOutputFile("report", "Report TSV", "Optional mismatch report path.")
            )
    );

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        Path genomePath = request.path("genome");
        Path annotationPath = request.path("annotation");
        Path report = request.path("report");
        if (report == null || report.toString().isBlank()) {
            report = context.defaultOutputPath("genome-annotation-match.tsv");
        }
        Path parent = report.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        context.progress(0.1, "Loading genome sequences");
        FastaSequenceStore genome = FastaSequenceStore.load(genomePath);
        Set<String> annotationSeqIds = new LinkedHashSet<>();
        Set<String> missing = new LinkedHashSet<>();
        List<String[]> issues = new ArrayList<>();
        long outOfRange = 0;
        long records = 0;

        context.progress(0.35, "Checking annotation records");
        try (GxfReader reader = new GxfReader(annotationPath)) {
            GxfRecord record;
            while ((record = reader.next()) != null) {
                context.cancellationToken().throwIfCancelled();
                records++;
                annotationSeqIds.add(record.seqId());
                long length = genome.length(record.seqId());
                if (length < 0) {
                    missing.add(record.seqId());
                    issues.add(new String[]{"MISSING_SEQUENCE", record.seqId(), Long.toString(record.lineNumber()),
                            record.start() + ".." + record.end(), "Sequence ID does not exist in the genome FASTA"});
                } else if (record.end() > length) {
                    outOfRange++;
                    issues.add(new String[]{"COORDINATE_OUT_OF_RANGE", record.seqId(), Long.toString(record.lineNumber()),
                            record.start() + ".." + record.end(), "Annotation end exceeds genome length " + length});
                }
            }
        }
        if (records == 0) {
            throw new OpenToolException("EMPTY_GXF", "No annotation records were found.", "Check the GFF3/GTF input.");
        }
        long matchedSeqIds = annotationSeqIds.stream().filter(genome::contains).count();
        if (matchedSeqIds == 0) {
            throw new OpenToolException(
                    "NO_SEQUENCE_MATCH",
                    "No annotation sequence IDs match the genome FASTA.",
                    "Check the reference genome assembly and sequence naming."
            );
        }

        writeReport(report, issues);
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("annotationSequences", annotationSeqIds.size());
        metrics.put("matchedSequences", matchedSeqIds);
        metrics.put("missingSequences", missing.size());
        metrics.put("outOfRangeRecords", outOfRange);
        metrics.put("records", records);

        ResultTable table = new ResultTable(
                List.of("Metric", "Value"),
                List.of(
                        List.of("Annotation sequence IDs", Long.toString(annotationSeqIds.size())),
                        List.of("Matched sequence IDs", Long.toString(matchedSeqIds)),
                        List.of("Missing sequence IDs", Long.toString(missing.size())),
                        List.of("Out-of-range records", Long.toString(outOfRange))
                )
        );
        context.progress(1.0, "Genome and annotation match check completed");
        String summary = issues.isEmpty() ? "Genome and annotation are compatible" :
                "Found " + issues.size() + " compatibility issues";
        return new ToolResult(
                summary,
                issues.isEmpty() ? "result.gxf.match.summary.ok" : "result.gxf.match.summary.issues",
                List.of(issues.size()),
                "Matched " + matchedSeqIds + " of " + annotationSeqIds.size() + " sequence IDs",
                "result.gxf.match.detail",
                List.of(matchedSeqIds, annotationSeqIds.size()),
                table,
                List.of(new OutputArtifact(report, "Compatibility report", "text/tab-separated-values")),
                metrics
        );
    }

    private static void writeReport(Path report, List<String[]> issues) throws Exception {
        try (BufferedWriter writer = Files.newBufferedWriter(report, StandardCharsets.UTF_8)) {
            writer.write("issue\tseq_id\tline\tcoordinates\tdetail");
            writer.newLine();
            for (String[] issue : issues) {
                writer.write(String.join("\t", issue));
                writer.newLine();
            }
        }
    }
}
