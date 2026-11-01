package io.opentools.external.alignment;

import io.opentools.api.tool.AbstractOpenTool;
import io.opentools.api.tool.OpenToolException;
import io.opentools.api.tool.OutputArtifact;
import io.opentools.api.tool.ResultTable;
import io.opentools.api.tool.TaskContext;
import io.opentools.api.tool.TaskResult;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolParameter;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ValidationResult;
import io.opentools.formats.FormatTaskSupport;

import java.io.BufferedWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BlastReciprocalTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "alignment.blast.reciprocal",
            "Reciprocal Best BLAST Hits",
            "Combine forward and reverse BLAST tables and retain mutual best-hit pairs.",
            "Alignment",
            List.of("reciprocal blast", "RBH", "双向最佳比对"),
            List.of("BLAST tabular"),
            List.of("TSV"),
            "1.0.0",
            List.of(
                    ToolParameter.requiredFile("forward", "Forward BLAST table", "Query-to-subject BLAST outfmt 6 output."),
                    ToolParameter.requiredFile("reverse", "Reverse BLAST table", "Subject-to-query BLAST outfmt 6 output."),
                    ToolParameter.optionalOutputFile("output", "Output table", "Leave blank to save in the run directory."),
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
                .merge(FormatTaskSupport.validateOutput(request, request.path("forward")));
    }

    @Override
    public TaskResult execute(ToolRequest request, TaskContext context) throws Exception {
        Path output = FormatTaskSupport.outputPath(request, context, "reciprocal-best-hits.tsv");
        List<BlastTabularHit> forward = new BlastTabularReader().read(request.path("forward"));
        List<BlastTabularHit> reverse = new BlastTabularReader().read(request.path("reverse"));
        Map<String, BlastTabularHit> bestForward = bestByQuery(forward);
        Map<String, BlastTabularHit> bestReverse = bestByQuery(reverse);
        List<BlastTabularHit> reciprocal = new ArrayList<>();
        for (BlastTabularHit hit : bestForward.values()) {
            BlastTabularHit back = bestReverse.get(hit.subject());
            if (back != null && back.subject().equals(hit.query())) {
                reciprocal.add(hit);
            }
        }
        if (reciprocal.isEmpty()) {
            throw new OpenToolException("NO_RECIPROCAL_HITS", "No reciprocal best-hit pairs were found.",
                    "Check that both tables use compatible sequence identifiers and outfmt 6 columns.");
        }
        FormatTaskSupport.writeAtomically(output, writer -> write(writer, reciprocal));
        context.progress(1.0, "Reciprocal BLAST analysis completed");
        return new TaskResult(
                "Found " + reciprocal.size() + " reciprocal best-hit pairs",
                "result.blast.reciprocal.summary", List.of(reciprocal.size()),
                forward.size() + " forward hits and " + reverse.size() + " reverse hits were read.",
                "result.blast.reciprocal.detail", List.of(forward.size(), reverse.size()),
                new ResultTable(List.of("Metric", "Value"), List.of(
                        List.of("Forward hits", Integer.toString(forward.size())),
                        List.of("Reverse hits", Integer.toString(reverse.size())),
                        List.of("Reciprocal pairs", Integer.toString(reciprocal.size()))
                )),
                List.of(new OutputArtifact(output, "Reciprocal best hits", "text/tab-separated-values")),
                Map.of("forwardHits", forward.size(), "reverseHits", reverse.size(),
                        "reciprocalPairs", reciprocal.size())
        );
    }

    private static Map<String, BlastTabularHit> bestByQuery(List<BlastTabularHit> hits) {
        Map<String, BlastTabularHit> best = new LinkedHashMap<>();
        for (BlastTabularHit hit : hits) {
            best.merge(hit.query(), hit, (left, right) -> better(left, right) ? left : right);
        }
        return best;
    }

    private static boolean better(BlastTabularHit left, BlastTabularHit right) {
        int evalue = Double.compare(left.evalue(), right.evalue());
        return evalue < 0 || (evalue == 0 && left.bitscore() >= right.bitscore());
    }

    private static void write(BufferedWriter writer, List<BlastTabularHit> hits) throws Exception {
        for (BlastTabularHit hit : hits) {
            writer.write(String.join("\t",
                    hit.query(), hit.subject(), Double.toString(hit.percentIdentity()),
                    Long.toString(hit.alignmentLength()), Long.toString(hit.mismatches()),
                    Long.toString(hit.gapOpenings()), Long.toString(hit.queryStart()),
                    Long.toString(hit.queryEnd()), Long.toString(hit.subjectStart()),
                    Long.toString(hit.subjectEnd()), Double.toString(hit.evalue()),
                    Double.toString(hit.bitscore())
            ));
            writer.newLine();
        }
    }
}
