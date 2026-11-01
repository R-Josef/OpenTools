package io.opentools.external.comparative;

import io.opentools.api.tool.AbstractOpenTool;
import io.opentools.api.tool.OutputArtifact;
import io.opentools.api.tool.ResultTable;
import io.opentools.api.tool.TaskContext;
import io.opentools.api.tool.TaskResult;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolParameter;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ValidationResult;
import io.opentools.formats.Compression;
import io.opentools.formats.FormatTaskSupport;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class McscanImportTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "mcscan.import", "Import MCScanX Collinearity", "Parse MCScanX collinearity blocks into a tidy TSV.",
            "Comparative genomics", List.of("MCScanX import", "共线性导入"),
            List.of("collinearity"), List.of("TSV"), "1.0.0",
            List.of(ToolParameter.requiredFile("input", "Input collinearity", "MCScanX .collinearity file."),
                    ToolParameter.optionalOutputFile("output", "Output TSV", "Leave blank to save in the run directory."),
                    ToolParameter.bool("overwrite", "Overwrite output", "Allow replacing an existing output.", false)));

    @Override public ToolDescriptor descriptor() { return DESCRIPTOR; }
    @Override public ValidationResult validate(ToolRequest request) {
        return validateParameters(request).merge(FormatTaskSupport.validateOutput(request, request.path("input")));
    }

    @Override public TaskResult execute(ToolRequest request, TaskContext context) throws Exception {
        Path output = FormatTaskSupport.outputPath(request, context, "collinearity.tsv");
        long[] totals = new long[3];
        FormatTaskSupport.writeAtomically(output, writer -> {
            writer.write("block\tblock_score\tgene_a\tgene_b\tpair_index\n");
            Pattern header = Pattern.compile("##\\s+Alignment\\s+(\\d+):\\s+score=([^\\s]+).*");
            int block = 0;
            double score = 0;
            int pair = 0;
            long pairs = 0;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    Compression.open(request.path("input")), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    Matcher matcher = header.matcher(line.trim());
                    if (matcher.matches()) {
                        block = Integer.parseInt(matcher.group(1));
                        score = Double.parseDouble(matcher.group(2));
                        pair = 0;
                        continue;
                    }
                    if (line.isBlank() || line.startsWith("#") || block == 0) {
                        continue;
                    }
                    String[] fields = line.trim().split("\\s+");
                    if (fields.length < 2) {
                        continue;
                    }
                    pair++;
                    writer.write(block + "\t" + score + "\t" + fields[fields.length - 2] + "\t"
                            + fields[fields.length - 1] + "\t" + pair + "\n");
                    pairs++;
                }
            }
            totals[0] = block;
            totals[1] = pairs;
        });
        if (totals[1] == 0) {
            throw new io.opentools.api.tool.OpenToolException("MCSCAN_NO_PAIRS",
                    "No collinearity pairs were parsed.", "Check MCScanX collinearity format.");
        }
        context.progress(1.0, "MCScanX collinearity import completed");
        return new TaskResult("Imported " + totals[1] + " collinearity pairs",
                "result.mcscan.import.summary", List.of(totals[1]),
                totals[0] + " blocks were parsed.", "result.mcscan.import.detail", List.of(totals[0]),
                new ResultTable(List.of("Metric", "Value"), List.of(
                        List.of("Blocks", Long.toString(totals[0])),
                        List.of("Pairs", Long.toString(totals[1])))),
                List.of(new OutputArtifact(output, "Collinearity table", "text/tab-separated-values")),
                Map.of("blocks", totals[0], "pairs", totals[1]));
    }
}
