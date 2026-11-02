package io.opentools.external.annotation;

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
import io.opentools.formats.Compression;
import io.opentools.formats.FormatToolSupport;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class EggnogImportTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "annotation.eggnog.import",
            "Import eggNOG Annotations",
            "Normalize eggNOG-mapper TSV annotations and summarize GO, KEGG, COG, and descriptions.",
            "Annotation",
            List.of("eggnog import", "eggnog mapper", "eggNOG 导入"),
            List.of("eggNOG TSV", "TSV.gz"),
            List.of("TSV"),
            "1.0.0",
            List.of(
                    ToolParameter.requiredFile("input", "Input eggNOG table", "eggNOG-mapper annotation TSV with header."),
                    ToolParameter.optionalOutputFile("output", "Output TSV", "Leave blank to save in the run directory."),
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
        Path output = FormatToolSupport.outputPath(request, context, "eggnog-annotations.tsv");
        long[] totals = new long[3];
        FormatToolSupport.writeAtomically(output, writer -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    Compression.open(request.path("input")), StandardCharsets.UTF_8))) {
                String headerLine = reader.readLine();
                if (headerLine == null || headerLine.isBlank()) {
                    throw new OpenToolException("EMPTY_EGGNOG_TABLE", "eggNOG table is empty.",
                            "Provide eggNOG-mapper TSV output with a header.");
                }
                String[] header = headerLine.split("\\t", -1);
                Map<String, Integer> index = new LinkedHashMap<>();
                for (int i = 0; i < header.length; i++) {
                    index.put(header[i].trim(), i);
                }
                int query = requiredColumn(index, "query", "query_name", "#query");
                int preferred = optionalColumn(index, "Preferred_name");
                int go = optionalColumn(index, "GOs", "GO");
                int kegg = optionalColumn(index, "KEGG_ko", "KEGG");
                int cog = optionalColumn(index, "COG_category");
                int description = optionalColumn(index, "Description");
                int seed = optionalColumn(index, "seed_ortholog");
                int evalue = optionalColumn(index, "evalue");
                int score = optionalColumn(index, "score");
                writer.write("query\tseed_ortholog\tpreferred_name\tgo_terms\tkegg_ko\tcog_category"
                        + "\tevalue\tscore\tdescription\n");
                String line;
                long rows = 0;
                long goRows = 0;
                long keggRows = 0;
                while ((line = reader.readLine()) != null) {
                    context.cancellationToken().throwIfCancelled();
                    if (line.isBlank()) {
                        continue;
                    }
                    String[] fields = line.split("\\t", -1);
                    String goValue = field(fields, go);
                    String keggValue = field(fields, kegg);
                    writer.write(escape(field(fields, query)));
                    writer.write('\t');
                    writer.write(escape(field(fields, seed)));
                    writer.write('\t');
                    writer.write(escape(field(fields, preferred)));
                    writer.write('\t');
                    writer.write(escape(goValue));
                    writer.write('\t');
                    writer.write(escape(keggValue));
                    writer.write('\t');
                    writer.write(escape(field(fields, cog)));
                    writer.write('\t');
                    writer.write(escape(field(fields, evalue)));
                    writer.write('\t');
                    writer.write(escape(field(fields, score)));
                    writer.write('\t');
                    writer.write(escape(field(fields, description)));
                    writer.newLine();
                    rows++;
                    if (!goValue.isBlank() && !goValue.equals("-")) {
                        goRows++;
                    }
                    if (!keggValue.isBlank() && !keggValue.equals("-")) {
                        keggRows++;
                    }
                }
                totals[0] = rows;
                totals[1] = goRows;
                totals[2] = keggRows;
            }
        });
        if (totals[0] == 0) {
            throw new OpenToolException("EMPTY_EGGNOG_TABLE", "No eggNOG annotation rows were found.",
                    "Check the input table.");
        }
        context.progress(1.0, "eggNOG import completed");
        return new ToolResult(
                "Imported " + totals[0] + " eggNOG rows",
                "result.eggnog.import.summary", List.of(totals[0]),
                totals[1] + " rows have GO terms; " + totals[2] + " rows have KEGG KO terms.",
                "result.eggnog.import.detail", List.of(totals[1], totals[2]),
                new ResultTable(List.of("Metric", "Value"), List.of(
                        List.of("Rows", Long.toString(totals[0])),
                        List.of("GO rows", Long.toString(totals[1])),
                        List.of("KEGG rows", Long.toString(totals[2]))
                )),
                List.of(new OutputArtifact(output, "eggNOG annotation", "text/tab-separated-values")),
                Map.of("rows", totals[0], "goRows", totals[1], "keggRows", totals[2])
        );
    }

    private static int requiredColumn(Map<String, Integer> index, String... names) {
        int value = optionalColumn(index, names);
        if (value < 0) {
            throw new OpenToolException("EGGNOG_COLUMN_MISSING",
                    "Required eggNOG column is missing: " + String.join("/", names),
                    "Use eggNOG-mapper TSV output or rename the required query column.");
        }
        return value;
    }

    private static int optionalColumn(Map<String, Integer> index, String... names) {
        for (String name : names) {
            Integer value = index.get(name);
            if (value != null) {
                return value;
            }
        }
        return -1;
    }

    private static String field(String[] fields, int index) {
        return index >= 0 && index < fields.length ? fields[index] : "";
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\t", " ").replace("\n", " ");
    }
}
