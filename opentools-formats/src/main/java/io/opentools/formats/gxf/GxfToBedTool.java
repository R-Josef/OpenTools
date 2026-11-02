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
import io.opentools.api.tool.ValidationResult;
import io.opentools.formats.FormatToolSupport;
import io.opentools.formats.bed.BedRecord;
import io.opentools.formats.bed.BedWriter;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class GxfToBedTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "gxf.to-bed",
            "Convert GFF3/GTF to BED",
            "Convert 1-based inclusive annotation intervals to 0-based half-open BED.",
            "Genome annotation",
            List.of("gff to bed", "gtf to bed", "annotation bed", "GFF 转 BED"),
            List.of("GFF3", "GTF", "GFF3.gz", "GTF.gz"),
            List.of("BED", "BED.gz"),
            "1.0.0",
            List.of(
                    ToolParameter.requiredFile("input", "Input annotation", "GFF3 or GTF."),
                    ToolParameter.string("nameAttribute", "Name attribute",
                            "Attribute used for BED column 4. AUTO prefers ID, transcript_id, gene_id, then Name.", "AUTO"),
                    ToolParameter.optionalOutputFile("output", "Output BED", "Leave blank to save in the run directory."),
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
        Path output = FormatToolSupport.outputPath(request, context, "annotation.bed");
        long[] totals = new long[2];
        FormatToolSupport.writeAtomically(output, writer -> {
            long records = 0;
            long bases = 0;
            try (GxfReader reader = new GxfReader(input)) {
                GxfRecord record;
                while ((record = reader.next()) != null) {
                    context.cancellationToken().throwIfCancelled();
                    String name = AnnotationTaskSupport.attribute(record, request.string("nameAttribute"));
                    BedWriter.write(writer, new BedRecord(
                            record.seqId(),
                            record.start() - 1,
                            record.end(),
                            name,
                            record.score(),
                            record.strand(),
                            List.of(),
                            record.lineNumber()
                    ));
                    records++;
                    bases += record.length();
                }
            }
            if (records == 0) {
                throw new OpenToolException("EMPTY_ANNOTATION", "No annotation records were found.",
                        "Check the input format.");
            }
            totals[0] = records;
            totals[1] = bases;
        });
        context.progress(1.0, "BED conversion completed");
        return new ToolResult(
                "Converted " + totals[0] + " annotation records to BED", List.of(totals[0]),
                "Output uses 0-based start and exclusive end.", List.of(),
                new ResultTable(List.of("Metric", "Value"), List.of(
                        List.of("Records", Long.toString(totals[0])),
                        List.of("Bases", Long.toString(totals[1]))
                )),
                List.of(new OutputArtifact(output, "BED annotation", "text/x-bed")),
                Map.of("records", totals[0], "bases", totals[1])
        );
    }
}
