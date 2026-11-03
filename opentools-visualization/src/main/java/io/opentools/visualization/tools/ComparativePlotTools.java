package io.opentools.visualization.tools;

import io.opentools.api.tool.AbstractOpenTool;
import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolResult;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolParameter;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ValidationResult;
import io.opentools.visualization.model.GenericPlotData;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ComparativePlotTools {
    private ComparativePlotTools() {
    }

    public static List<AbstractOpenTool> tools() {
        return List.of(new PlotCollinearityTool(), new PlotDotplotTool());
    }
}

final class PlotCollinearityTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = PlotToolSupport.descriptor(
            "plot.collinearity",
            "Collinearity Plot",
            "Plot MCScanX collinearity pairs as gene-order dots between two annotated genomes.",
            List.of(
                    ToolParameter.requiredFile("input", "Collinearity table", "Output from mcscan.import."),
                    ToolParameter.requiredFile("annotationA", "Genome A annotation",
                            "GFF3/GTF genes for the first genome; IDs must match gene_a."),
                    ToolParameter.requiredFile("annotationB", "Genome B annotation",
                            "GFF3/GTF genes for the second genome; IDs must match gene_b."),
                    ToolParameter.string("blockColumn", "Block column", "Block identifier column.", "block"),
                    ToolParameter.string("geneAColumn", "Genome A gene column", "Gene ID column for genome A.", "gene_a"),
                    ToolParameter.string("geneBColumn", "Genome B gene column", "Gene ID column for genome B.", "gene_b"),
                    ToolParameter.integer("maxBlocks", "Colored blocks",
                            "Top blocks by pair count are colored; remaining pairs are grouped as other.", 20, 1, 200)
            ),
            "Collinearity"
    );

    @Override public ToolDescriptor descriptor() { return DESCRIPTOR; }

    @Override public ValidationResult validate(ToolRequest request) {
        ValidationResult result = validateParameters(request).merge(PlotToolSupport.validate(request));
        for (String parameter : List.of("annotationA", "annotationB")) {
            if (!java.nio.file.Files.isRegularFile(request.path(parameter))) {
                result = result.merge(ValidationResult.of(io.opentools.api.tool.ValidationIssue.error(
                        parameter, "Annotation file not found: " + request.path(parameter),
                        "Choose a GFF3 or GTF file with gene features.")));
            }
        }
        return result;
    }

    @Override public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        Genome genomeA = readGenome(request.path("annotationA"));
        Genome genomeB = readGenome(request.path("annotationB"));
        PlotToolSupport.PlotTable table = PlotToolSupport.readTable(request.path("input"), null);
        int blockColumn = table.requiredColumn(request.string("blockColumn"));
        int geneA = table.requiredColumn(request.string("geneAColumn"));
        int geneB = table.requiredColumn(request.string("geneBColumn"));
        int maxBlocks = Math.max(1, request.integer("maxBlocks"));

        List<Pair> pairs = new java.util.ArrayList<>();
        Map<String, Integer> blockCounts = new LinkedHashMap<>();
        int unmapped = 0;
        for (var row : table.rows()) {
            context.cancellationToken().throwIfCancelled();
            String block = PlotToolSupport.cell(row, blockColumn);
            String idA = PlotToolSupport.cell(row, geneA);
            String idB = PlotToolSupport.cell(row, geneB);
            Integer x = genomeA.order().get(idA);
            Integer y = genomeB.order().get(idB);
            if (x == null || y == null) {
                unmapped++;
                continue;
            }
            String group = block.isBlank() ? "unassigned" : block;
            pairs.add(new Pair(group, x + 0.5, y + 0.5));
            blockCounts.merge(group, 1, Integer::sum);
        }
        if (pairs.isEmpty()) {
            throw new io.opentools.api.tool.OpenToolException(
                    "COLLINEARITY_EMPTY",
                    "No collinearity pairs matched gene IDs in both annotations.",
                    "Check that gene_a/gene_b use the same IDs as the GFF3/GTF ID or gene_id attributes."
            );
        }
        List<String> topBlocks = blockCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(maxBlocks)
                .map(Map.Entry::getKey)
                .toList();
        Map<String, GenericPlotData.GenericSeries.Builder> groups = new LinkedHashMap<>();
        for (Pair pair : pairs) {
            String group = topBlocks.contains(pair.block()) ? "Block " + pair.block() : "Other blocks";
            groups.computeIfAbsent(group, GenericPlotData.GenericSeries.Builder::new)
                    .add(pair.x(), pair.y());
        }
        GenericPlotData data = new GenericPlotData("SCATTER", "Genome A gene order",
                "Genome B gene order", List.of(), groups.values().stream()
                .map(GenericPlotData.GenericSeries.Builder::build).toList(),
                (double) genomeA.total(), (double) genomeB.total());
        context.log("Collinearity: " + pairs.size() + " plotted pairs, " + unmapped + " unmapped");
        ToolResult base = PlotToolSupport.renderGeneric(this, request, context, data,
                "collinearity", request.string("title"), pairs.size(), blockCounts.size());
        Map<String, Object> metrics = new LinkedHashMap<>(base.metrics());
        metrics.put("pairs", pairs.size());
        metrics.put("unmapped", unmapped);
        metrics.put("blocks", blockCounts.size());
        metrics.put("genesA", genomeA.total());
        metrics.put("genesB", genomeB.total());
        metrics.put("chromosomesA", genomeA.chromosomes());
        metrics.put("chromosomesB", genomeB.chromosomes());
        metrics.put("maxBlocks", maxBlocks);
        return new ToolResult(base.summary(), base.summaryArguments(), base.detail(),
                base.detailArguments(), base.table(), base.artifacts(), metrics);
    }

    private static Genome readGenome(java.nio.file.Path path) throws Exception {
        Map<String, Integer> order = new LinkedHashMap<>();
        Map<String, List<Gene>> bySequence = new LinkedHashMap<>();
        Map<String, Gene> seen = new LinkedHashMap<>();
        try (var reader = new io.opentools.formats.gxf.GxfReader(path, true)) {
            io.opentools.formats.gxf.GxfRecord record;
            while ((record = reader.next()) != null) {
                if (!isGeneFeature(record.type())) {
                    continue;
                }
                String id = normalizeGeneId(record.firstAttribute("ID", "gene_id", "Name", "locus_tag"));
                if (id == null || id.isBlank() || seen.containsKey(id)) {
                    continue;
                }
                Gene gene = new Gene(id, record.seqId(), record.start(), record.end());
                seen.put(id, gene);
                bySequence.computeIfAbsent(record.seqId(), ignored -> new java.util.ArrayList<>()).add(gene);
            }
        }
        if (seen.isEmpty()) {
            throw new io.opentools.api.tool.OpenToolException(
                    "COLLINEARITY_ANNOTATION_EMPTY",
                    "No gene features were found in " + path.getFileName() + ".",
                    "Use GFF3/GTF genes with ID or gene_id attributes."
            );
        }
        int offset = 0;
        for (List<Gene> genes : bySequence.values()) {
            genes.sort(java.util.Comparator.comparingLong(Gene::start).thenComparing(Gene::id));
            for (Gene gene : genes) {
                order.put(gene.id(), offset++);
            }
        }
        return new Genome(Map.copyOf(order), offset, bySequence.size());
    }

    private static boolean isGeneFeature(String type) {
        return type.equalsIgnoreCase("gene")
                || type.equalsIgnoreCase("mRNA")
                || type.equalsIgnoreCase("transcript");
    }

    private static String normalizeGeneId(String id) {
        if (id == null) {
            return null;
        }
        String value = id.trim();
        for (String prefix : List.of("gene:", "transcript:", "mRNA:")) {
            if (value.startsWith(prefix)) {
                return value.substring(prefix.length());
            }
        }
        return value;
    }

    private record Gene(String id, String sequence, long start, long end) {
    }

    private record Genome(Map<String, Integer> order, int total, int chromosomes) {
    }

    private record Pair(String block, double x, double y) {
    }
}

final class PlotDotplotTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = PlotToolSupport.descriptor(
            "plot.dotplot",
            "Dot Plot",
            "Plot numeric coordinates or BLAST outfmt 6 HSPs as a genome-position dot plot.",
            List.of(
                    ToolParameter.requiredFile("input", "Dot plot table",
                            "Delimited table with x/y columns, or BLAST outfmt 6 (qseqid, sseqid, pident, qstart, qend, sstart, send)."),
                    new ToolParameter("inputFormat", "Input format",
                            "AUTO detects BLAST outfmt 6 from its qseqid and sseqid headers.",
                            io.opentools.api.tool.ParameterType.ENUM, false, "AUTO",
                            List.of("AUTO", "X_Y_TABLE", "BLAST_M6"), null, null, false, null),
                    ToolParameter.string("xColumn", "X column", "Numeric x column for X_Y_TABLE input.", "x"),
                    ToolParameter.string("yColumn", "Y column", "Numeric y column for X_Y_TABLE input.", "y"),
                    ToolParameter.string("groupColumn", "Group column", "Optional series grouping column for X_Y_TABLE input.", ""),
                    ToolParameter.string("queryLengthColumn", "Query length column",
                            "Optional qlen column for BLAST_M6 input; derived from qstart/qend when absent.", "qlen"),
                    ToolParameter.string("subjectLengthColumn", "Subject length column",
                            "Optional slen column for BLAST_M6 input; derived from sstart/send when absent.", "slen"),
                    ToolParameter.integer("maxHits", "Maximum HSPs", "Maximum BLAST HSP rows to draw.", 5000, 1, 200000)
            ),
            "Dot Plot"
    );

    @Override public ToolDescriptor descriptor() { return DESCRIPTOR; }
    @Override public ValidationResult validate(ToolRequest request) { return PlotToolSupport.validate(request); }

    @Override public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        PlotToolSupport.PlotTable table = PlotToolSupport.readTable(request.path("input"), null);
        String format = request.string("inputFormat").toUpperCase(java.util.Locale.ROOT);
        boolean blast = switch (format) {
            case "BLAST_M6" -> true;
            case "X_Y_TABLE" -> false;
            default -> isBlastTable(table);
        };
        if (blast) {
            return renderBlastDotPlot(request, context, table);
        }
        int x = table.requiredColumn(request.string("xColumn"));
        int y = table.requiredColumn(request.string("yColumn"));
        int group = request.string("groupColumn").isBlank() ? -1 : table.column(request.string("groupColumn"));
        Map<String, GenericPlotData.GenericSeries.Builder> groups = new LinkedHashMap<>();
        for (var row : table.rows()) {
            String name = group >= 0 ? PlotToolSupport.cell(row, group) : "All";
            if (name.isBlank()) {
                name = "All";
            }
            groups.computeIfAbsent(name, GenericPlotData.GenericSeries.Builder::new)
                    .add(PlotToolSupport.number(row, x), PlotToolSupport.number(row, y));
        }
        GenericPlotData data = new GenericPlotData("SCATTER", request.string("xColumn"),
                request.string("yColumn"), List.of(), groups.values().stream()
                .map(GenericPlotData.GenericSeries.Builder::build).toList());
        return PlotToolSupport.renderGeneric(this, request, context, data,
                "dotplot", request.string("title"), groups.size());
    }

    private static boolean isBlastTable(PlotToolSupport.PlotTable table) {
        return table.column("qseqid") >= 0
                && table.column("sseqid") >= 0
                && table.column("qstart") >= 0
                && table.column("qend") >= 0
                && table.column("sstart") >= 0
                && table.column("send") >= 0;
    }

    private ToolResult renderBlastDotPlot(
            ToolRequest request, ToolExecutionContext context, PlotToolSupport.PlotTable table
    ) throws Exception {
        table.requiredColumn("qseqid");
        table.requiredColumn("sseqid");
        int identity = table.requiredColumn("pident");
        int queryStart = table.requiredColumn("qstart");
        int queryEnd = table.requiredColumn("qend");
        int subjectStart = table.requiredColumn("sstart");
        int subjectEnd = table.requiredColumn("send");
        int queryLength = optionalColumn(table, request.string("queryLengthColumn"));
        int subjectLength = optionalColumn(table, request.string("subjectLengthColumn"));
        int maxHits = request.integer("maxHits");
        if (maxHits <= 0) {
            maxHits = 5000;
        }

        Map<String, GenericPlotData.GenericSeries.Builder> bins = new LinkedHashMap<>();
        double maxQuery = 0;
        double maxSubject = 0;
        int plotted = 0;
        for (var row : table.rows()) {
            if (plotted >= maxHits) {
                break;
            }
            context.cancellationToken().throwIfCancelled();
            double qStart = PlotToolSupport.number(row, queryStart);
            double qEnd = PlotToolSupport.number(row, queryEnd);
            double sStart = PlotToolSupport.number(row, subjectStart);
            double sEnd = PlotToolSupport.number(row, subjectEnd);
            double percent = PlotToolSupport.number(row, identity);
            if (!Double.isFinite(qStart) || !Double.isFinite(qEnd)
                    || !Double.isFinite(sStart) || !Double.isFinite(sEnd)) {
                continue;
            }
            String bin = identityBin(percent);
            bins.computeIfAbsent(bin, GenericPlotData.GenericSeries.Builder::new)
                    .add(qStart, sStart)
                    .add(qEnd, sEnd);
            maxQuery = Math.max(maxQuery, Math.max(qStart, qEnd));
            maxSubject = Math.max(maxSubject, Math.max(sStart, sEnd));
            maxQuery = Math.max(maxQuery, lengthValue(row, queryLength));
            maxSubject = Math.max(maxSubject, lengthValue(row, subjectLength));
            plotted++;
        }
        if (plotted == 0) {
            throw new io.opentools.api.tool.OpenToolException(
                    "BLAST_DOTPLOT_EMPTY",
                    "No BLAST HSP rows were available to plot.",
                    "Check that the input is BLAST outfmt 6 with numeric coordinates."
            );
        }
        List<GenericPlotData.GenericSeries> series = orderedBins(bins).stream()
                .map(GenericPlotData.GenericSeries.Builder::build)
                .toList();
        GenericPlotData data = new GenericPlotData("SEGMENTS", "Query position", "Subject position",
                List.of(), series, maxQuery, maxSubject);
        context.log("BLAST dot plot: " + plotted + " HSPs across " + series.size() + " identity bins");
        ToolResult base = PlotToolSupport.renderGeneric(this, request, context, data,
                "blast-dotplot", request.string("title"), series.size());
        Map<String, Object> metrics = new LinkedHashMap<>(base.metrics());
        metrics.put("hits", plotted);
        metrics.put("identityBins", series.size());
        return new ToolResult(base.summary(), base.summaryArguments(), base.detail(),
                base.detailArguments(), base.table(), base.artifacts(), metrics);
    }

    private static int optionalColumn(PlotToolSupport.PlotTable table, String name) {
        return name == null || name.isBlank() ? -1 : table.column(name.trim());
    }

    private static double lengthValue(List<String> row, int column) {
        if (column < 0) {
            return 0;
        }
        try {
            double value = PlotToolSupport.number(row, column);
            return Double.isFinite(value) && value > 0 ? value : 0;
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    private static String identityBin(double percent) {
        if (percent >= 95) {
            return "95-100% identity";
        }
        if (percent >= 80) {
            return "80-95% identity";
        }
        if (percent >= 50) {
            return "50-80% identity";
        }
        return "<50% identity";
    }

    private static List<GenericPlotData.GenericSeries.Builder> orderedBins(
            Map<String, GenericPlotData.GenericSeries.Builder> bins
    ) {
        List<GenericPlotData.GenericSeries.Builder> ordered = new java.util.ArrayList<>();
        for (String name : List.of("95-100% identity", "80-95% identity", "50-80% identity", "<50% identity")) {
            GenericPlotData.GenericSeries.Builder builder = bins.get(name);
            if (builder != null) {
                ordered.add(builder);
            }
        }
        return ordered;
    }
}