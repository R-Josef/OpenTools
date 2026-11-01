package io.opentools.external.comparative;

import io.opentools.api.tool.AbstractOpenTool;
import io.opentools.api.tool.OutputArtifact;
import io.opentools.api.tool.TaskContext;
import io.opentools.api.tool.TaskResult;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolParameter;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ValidationResult;
import io.opentools.formats.FormatTaskSupport;
import io.opentools.formats.gxf.GxfReader;
import io.opentools.formats.gxf.GxfRecord;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class McscanInputTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "mcscan.input", "Prepare MCScanX Input", "Create MCScanX .gff and .blast files from annotation and BLAST tables.",
            "Comparative genomics", List.of("MCScanX input", "共线性输入"),
            List.of("GFF3/GTF", "BLAST tabular"), List.of("GFF", "BLAST"), "1.0.0",
            List.of(ToolParameter.requiredFile("annotation", "Annotation", "GFF3/GTF with gene features."),
                    ToolParameter.requiredFile("blast", "BLAST table", "Tabular BLAST hits."),
                    ToolParameter.string("outputPrefix", "Output prefix", "Prefix for .gff and .blast outputs.", "mcscan"),
                    ToolParameter.bool("overwrite", "Overwrite outputs", "Allow replacing existing outputs.", false)));

    @Override public ToolDescriptor descriptor() { return DESCRIPTOR; }
    @Override public ValidationResult validate(ToolRequest request) { return validateParameters(request); }

    @Override public TaskResult execute(ToolRequest request, TaskContext context) throws Exception {
        Path gff = Path.of(request.string("outputPrefix") + ".gff");
        Path blast = Path.of(request.string("outputPrefix") + ".blast");
        if ((Files.exists(gff) || Files.exists(blast)) && !request.bool("overwrite")) {
            throw new io.opentools.api.tool.OpenToolException("OUTPUT_EXISTS", "MCScanX input files already exist.",
                    "Choose another prefix or enable overwrite.");
        }
        boolean useGeneFeatures = containsFeatureType(request.path("annotation"), "gene");
        long[] count = new long[1];
        FormatTaskSupport.writeAtomically(gff, writer -> {
            try (GxfReader reader = new GxfReader(request.path("annotation"))) {
                GxfRecord record;
                while ((record = reader.next()) != null) {
                    boolean selected = useGeneFeatures
                            ? record.type().equalsIgnoreCase("gene")
                            : record.type().equalsIgnoreCase("mRNA");
                    if (!selected) {
                        continue;
                    }
                    String featureId = useGeneFeatures
                            ? record.firstAttribute("ID", "gene_id", "Name")
                            : record.firstAttribute("ID", "transcript_id", "gene_id", "Name");
                    if (featureId == null || featureId.isBlank()) {
                        throw new io.opentools.api.tool.OpenToolException(
                                "MCSCAN_FEATURE_ID_MISSING",
                                "Feature ID is missing at annotation line " + record.lineNumber() + ".",
                                "Add ID (GFF3) or gene_id/transcript_id (GTF) attributes."
                        );
                    }
                    writer.write(record.seqId() + "\t" + featureId + "\t"
                            + record.start() + "\t" + record.end() + "\n");
                    count[0]++;
                }
            }
        });
        Files.copy(request.path("blast"), blast, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        context.progress(1.0, "MCScanX input preparation completed");
        return new TaskResult("Prepared MCScanX inputs", "result.mcscan.input.summary", List.of(count[0]),
                "Created " + gff.getFileName() + " and " + blast.getFileName() + ".",
                "result.mcscan.input.detail", List.of(),
                io.opentools.api.tool.ResultTable.empty(),
                List.of(new OutputArtifact(gff, "MCScanX GFF", "text/plain"),
                        new OutputArtifact(blast, "MCScanX BLAST", "text/plain")),
                Map.of("features", count[0]));
    }

    private static boolean containsFeatureType(Path annotation, String type) throws Exception {
        try (GxfReader reader = new GxfReader(annotation)) {
            GxfRecord record;
            while ((record = reader.next()) != null) {
                if (record.type().equalsIgnoreCase(type)) {
                    return true;
                }
            }
        }
        return false;
    }
}
