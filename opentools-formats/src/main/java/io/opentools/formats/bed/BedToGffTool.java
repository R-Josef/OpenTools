package io.opentools.formats.bed;

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

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class BedToGffTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "bed.to-gff",
            "Convert BED to GFF3",
            "Convert 0-based half-open BED intervals to 1-based inclusive GFF3.",
            "Genome annotation",
            List.of("bed to gff", "bed to gff3", "BED 转 GFF"),
            List.of("BED", "BED.gz"),
            List.of("GFF3"),
            "1.0.0",
            List.of(
                    ToolParameter.requiredFile("input", "Input BED", "BED3 or BED3+."),
                    ToolParameter.string("featureType", "Feature type", "GFF3 feature type.", "region"),
                    ToolParameter.string("source", "Source", "GFF3 source column.", "OpenTools"),
                    ToolParameter.optionalOutputFile("output", "Output GFF3", "Leave blank to save in the run directory."),
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
        Path output = FormatToolSupport.outputPath(request, context, "annotation.gff3");
        long[] totals = new long[2];
        FormatToolSupport.writeAtomically(output, writer -> {
            writer.write("##gff-version 3\n");
            long records = 0;
            long bases = 0;
            try (BedReader reader = new BedReader(input)) {
                BedRecord record;
                while ((record = reader.next()) != null) {
                    context.cancellationToken().throwIfCancelled();
                    records++;
                    bases += record.length();
                    String id = record.name().equals(".") ? "bed_" + records : record.name();
                    String attributes = "ID=" + escapeAttribute(id);
                    if (!record.name().equals(".") && !record.name().equals(id)) {
                        attributes += ";Name=" + escapeAttribute(record.name());
                    }
                    writer.write(record.seqId());
                    writer.write('\t');
                    writer.write(request.string("source"));
                    writer.write('\t');
                    writer.write(request.string("featureType"));
                    writer.write('\t');
                    writer.write(Long.toString(record.start() + 1));
                    writer.write('\t');
                    writer.write(Long.toString(record.end()));
                    writer.write('\t');
                    writer.write(record.score());
                    writer.write('\t');
                    writer.write(Character.toString(record.strand()));
                    writer.write("\t.\t");
                    writer.write(attributes);
                    writer.newLine();
                }
            }
            if (records == 0) {
                throw new OpenToolException("EMPTY_BED", "No BED records were found.", "Check the input format.");
            }
            totals[0] = records;
            totals[1] = bases;
        });
        context.progress(1.0, "GFF3 conversion completed");
        return new ToolResult(
                "Converted " + totals[0] + " BED intervals to GFF3", List.of(totals[0]),
                "Output uses 1-based inclusive coordinates.", List.of(),
                new ResultTable(List.of("Metric", "Value"), List.of(
                        List.of("Records", Long.toString(totals[0])),
                        List.of("Bases", Long.toString(totals[1]))
                )),
                List.of(new OutputArtifact(output, "GFF3 annotation", "text/x-gff3")),
                Map.of("records", totals[0], "bases", totals[1])
        );
    }

    private static String escapeAttribute(String value) {
        return value.replace("%", "%25").replace(";", "%3B").replace("=", "%3D")
                .replace(",", "%2C").replace("\t", "%09");
    }
}
