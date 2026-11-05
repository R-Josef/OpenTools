package io.opentools.external.annotation;

import io.opentools.api.tool.AbstractOpenTool;
import io.opentools.api.tool.OpenToolException;
import io.opentools.api.tool.OutputArtifact;
import io.opentools.api.tool.ResultTable;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolParameter;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ToolResult;
import io.opentools.api.tool.ValidationResult;
import io.opentools.formats.FormatToolSupport;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MemeResultTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "annotation.meme.results",
            "MEME/MAST Result Tables",
            "Convert MEME motifs or MAST hits from XML into reviewable motif and hit tables.",
            "Annotation",
            List.of("MEME results", "MAST results", "motif table", "MEME 结果", "MAST 结果"),
            List.of("MEME XML", "MAST XML"),
            List.of("TSV"),
            "1.0.0",
            List.of(
                    ToolParameter.requiredFile("input", "Input MEME/MAST XML",
                            "meme.xml or mast.xml output from the MEME Suite."),
                    ToolParameter.optionalOutputFile("motifOutput", "Motif table",
                            "Leave blank to save meme-motifs.tsv in the run directory."),
                    ToolParameter.optionalOutputFile("hitOutput", "Hit table",
                            "Leave blank to save mast-hits.tsv in the run directory."),
                    ToolParameter.bool("overwrite", "Overwrite output",
                            "Allow replacing existing outputs.", false)
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
        List<MemeResults.Motif> motifs = MemeResults.readMotifs(request.path("input"));
        List<MemeResults.MastHit> hits = MemeResults.readMastHits(request.path("input"));
        if (motifs.isEmpty() && hits.isEmpty()) {
            throw new OpenToolException(
                    "MEME_RESULT_EMPTY",
                    "No MEME motifs or MAST hits were found in the XML.",
                    "Choose a MEME or MAST result XML file."
            );
        }
        Path motifOutput = FormatToolSupport.outputPath(request, context, "meme-motifs.tsv");
        Path hitOutput = FormatToolSupport.outputPath(request, context, "mast-hits.tsv");
        FormatToolSupport.writeAtomically(motifOutput, writer -> {
            writer.write("motif_id\tname\twidth\tsites\tevalue\n");
            for (MemeResults.Motif motif : motifs) {
                writer.write(input(motif.id()) + "\t" + input(motif.name()) + "\t"
                        + motif.width() + "\t" + motif.sites() + "\t"
                        + Double.toString(motif.evalue()) + "\n");
            }
        });
        FormatToolSupport.writeAtomically(hitOutput, writer -> {
            writer.write("sequence\tmotif\tstart\tend\tstrand\tpvalue\tmatch\n");
            for (MemeResults.MastHit hit : hits) {
                writer.write(input(hit.sequence()) + "\t" + input(hit.motif()) + "\t"
                        + hit.start() + "\t" + hit.end() + "\t" + input(hit.strand()) + "\t"
                        + Double.toString(hit.pvalue()) + "\t" + input(hit.match()) + "\n");
            }
        });
        context.progress(1.0, "MEME/MAST result tables completed");
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("motifs", motifs.size());
        metrics.put("hits", hits.size());
        return new ToolResult(
                "Read " + motifs.size() + " motifs and " + hits.size() + " MAST hits",
                List.of(motifs.size(), hits.size()),
                "Motif and hit tables are available for review.",
                List.of(),
                new ResultTable(List.of("Metric", "Value"), List.of(
                        List.of("Motifs", Integer.toString(motifs.size())),
                        List.of("MAST hits", Integer.toString(hits.size()))
                )),
                List.of(
                        new OutputArtifact(motifOutput, "MEME motifs", "text/tab-separated-values"),
                        new OutputArtifact(hitOutput, "MAST hits", "text/tab-separated-values")
                ),
                metrics
        );
    }

    private static String input(String value) {
        return value == null ? "" : value.replace('\t', ' ').replace('\n', ' ');
    }
}
