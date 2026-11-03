package io.opentools.visualization.tools;

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
import io.opentools.api.tool.ValidationResult;
import io.opentools.formats.Compression;
import io.opentools.formats.fasta.FastaReader;
import io.opentools.formats.fasta.FastaRecord;
import io.opentools.formats.fasta.FastaSequenceStore;
import io.opentools.formats.gxf.GxfReader;
import io.opentools.formats.gxf.GxfRecord;
import io.opentools.visualization.model.GenericPlotData;
import io.opentools.visualization.model.HeatmapData;
import io.opentools.visualization.model.PlotExport;
import io.opentools.visualization.model.PlotSpec;
import io.opentools.visualization.model.PlotStyle;
import io.opentools.visualization.model.PlotType;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AdvancedPlotTools {
    private AdvancedPlotTools() {
    }

    public static List<AbstractOpenTool> tools() {
        return List.of(
                new PlotSeqLogoTool(),
                new PlotGeneLocationTool(),
                new PlotDomainTool(),
                new PlotMotifTool(),
                new PlotCircosTool(),
                new PlotEfpTool()
        );
    }
}

final class PlotSeqLogoTool extends AbstractOpenTool {
    private static final List<String> BASES = List.of("A", "C", "G", "T");
    private static final ToolDescriptor DESCRIPTOR = createDescriptor();

    @Override public ToolDescriptor descriptor() { return DESCRIPTOR; }
    @Override public ValidationResult validate(ToolRequest request) {
        ValidationResult result = PlotToolSupport.validate(request);
        if (request.path("output") != null && !request.bool("overwrite")) {
            Path base = PlotArtifactSupport.stripKnownExtensions(request.path("output"));
            Path processed = Path.of(base + ".processed.tsv");
            if (Files.exists(processed)) {
                result = result.merge(ValidationResult.of(
                        io.opentools.api.tool.ValidationIssue.error(
                                "output", "Sequence-logo processed output already exists.",
                                "Choose another prefix or enable overwrite.")));
            }
        }
        return result;
    }
    @Override public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        InputMode mode = inputMode(request);
        PwmInput pwm = mode == InputMode.FASTA
                ? readFastaPwm(request, context)
                : readTablePwm(request);
        List<String> categories = new ArrayList<>();
        for (int position = 1; position <= pwm.positions(); position++) {
            categories.add(Integer.toString(position));
        }
        List<GenericPlotData.GenericSeries> series = new ArrayList<>();
        for (int baseIndex = 0; baseIndex < BASES.size(); baseIndex++) {
            GenericPlotData.GenericSeries.Builder builder =
                    new GenericPlotData.GenericSeries.Builder(BASES.get(baseIndex));
            for (int position = 0; position < pwm.positions(); position++) {
                builder.add(position + 1, pwm.values()[position][baseIndex]);
            }
            series.add(builder.build());
        }
        GenericPlotData data = new GenericPlotData(
                "STACKED_BAR", "Position", mode == InputMode.FASTA ? "Frequency" : "Bits",
                categories, series);
        return render(request, context, data, pwm, mode);
    }

    private static ToolDescriptor createDescriptor() {
        List<ToolParameter> parameters = new ArrayList<>(List.of(
                ToolParameter.requiredFile("input", "Aligned FASTA or position matrix",
                        "Aligned nucleotide FASTA, or TSV with position, A, C, G, T columns."),
                new ToolParameter(
                        "inputMode", "Input mode",
                        "AUTO detects FASTA from its header or extension; POSITION_TABLE preserves the original TSV mode.",
                        ParameterType.ENUM, false, "AUTO",
                        List.of("AUTO", "FASTA", "POSITION_TABLE"),
                        null, null, false, null),
                ToolParameter.string("positionColumn", "Position column",
                        "Position column used in POSITION_TABLE mode.", "position"),
                ToolParameter.decimal("pseudocount", "Pseudocount",
                        "Added to each A/C/G/T count per FASTA position.", 0.0, 0.0, 1_000_000.0),
                ToolParameter.bool("bothStrands", "Both strands",
                        "Count each aligned FASTA sequence and its reverse complement.", false)
        ));
        parameters.add(ToolParameter.string("title", "Plot title", "Chart title.", "Sequence Logo"));
        parameters.add(ToolParameter.string("subtitle", "Plot subtitle", "Optional subtitle.", ""));
        parameters.add(ToolParameter.string("valueLabel", "Value label", "Y-axis value label.", ""));
        parameters.add(ToolParameter.integer("width", "Image width", "Export width in pixels.", 1200, 320, 10000));
        parameters.add(ToolParameter.integer("height", "Image height", "Export height in pixels.", 800, 240, 10000));
        parameters.add(ToolParameter.integer("dpi", "PDF DPI", "PDF export resolution.", 144, 72, 600));
        parameters.add(new ToolParameter("exportFormats", "Export formats", "Plot artifact formats.",
                ParameterType.ENUM, false, "PNG_SVG_PDF", List.of("PNG_SVG_PDF", "PNG_SVG", "PNG"),
                null, null, true, null));
        parameters.add(ToolParameter.optionalOutputFile("output", "Output prefix",
                "Leave blank to save in the run directory."));
        parameters.add(ToolParameter.bool("overwrite", "Overwrite outputs",
                "Allow replacing existing plot outputs.", false));
        return new ToolDescriptor(
                "plot.seqlogo", "Sequence Logo",
                "Compute a nucleotide PWM from aligned FASTA or render an existing position matrix.",
                "Visualization",
                List.of("sequence logo", "seqlogo", "PWM", "序列标识"),
                List.of("FASTA", "FASTA.gz", "TSV"),
                List.of("PNG", "SVG", "PDF", "JSON", "TSV"),
                "1.1.0",
                parameters
        );
    }

    private static InputMode inputMode(ToolRequest request) throws Exception {
        String configured = request.string("inputMode").toUpperCase(Locale.ROOT);
        if ("FASTA".equals(configured)) {
            return InputMode.FASTA;
        }
        if ("POSITION_TABLE".equals(configured)) {
            return InputMode.POSITION_TABLE;
        }
        String name = request.path("input").getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".fa") || name.endsWith(".fasta") || name.endsWith(".fna")
                || name.endsWith(".fa.gz") || name.endsWith(".fasta.gz")
                || name.endsWith(".fna.gz")) {
            return InputMode.FASTA;
        }
        try (BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(
                Compression.open(request.path("input")), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String normalized = line.strip();
                if (!normalized.isEmpty() && !normalized.startsWith("#")) {
                    return normalized.startsWith(">")
                            ? InputMode.FASTA : InputMode.POSITION_TABLE;
                }
            }
        }
        return InputMode.POSITION_TABLE;
    }

    private static PwmInput readTablePwm(ToolRequest request) throws Exception {
        var table = PlotToolSupport.readTable(request.path("input"), null);
        int position = table.requiredColumn(request.string("positionColumn"));
        double[][] values = new double[table.rows().size()][4];
        int[] columns = new int[4];
        for (int base = 0; base < 4; base++) {
            columns[base] = table.requiredColumn(BASES.get(base));
        }
        for (int rowIndex = 0; rowIndex < table.rows().size(); rowIndex++) {
            List<String> row = table.rows().get(rowIndex);
            Double.parseDouble(PlotToolSupport.cell(row, position));
            for (int base = 0; base < 4; base++) {
                values[rowIndex][base] = PlotToolSupport.number(row, columns[base]);
            }
        }
        return new PwmInput(values, 0, 0, false, 0);
    }

    private static PwmInput readFastaPwm(ToolRequest request, ToolExecutionContext context) throws Exception {
        List<String> sequences = new ArrayList<>();
        try (FastaReader reader = new FastaReader(request.path("input"))) {
            FastaRecord record;
            while ((record = reader.next()) != null) {
                context.cancellationToken().throwIfCancelled();
                sequences.add(record.sequence().toUpperCase(Locale.ROOT));
            }
        }
        if (sequences.isEmpty()) {
            throw new OpenToolException(
                    "SEQ_LOGO_FASTA_EMPTY", "Aligned FASTA contains no records.",
                    "Provide at least one nucleotide sequence."
            );
        }
        int length = sequences.getFirst().length();
        if (length == 0) {
            throw new OpenToolException(
                    "SEQ_LOGO_ALIGNMENT_EMPTY", "Aligned FASTA sequences are empty.",
                    "Provide a non-empty aligned FASTA."
            );
        }
        for (int index = 0; index < sequences.size(); index++) {
            if (sequences.get(index).length() != length) {
                throw new OpenToolException(
                        "SEQ_LOGO_ALIGNMENT_LENGTH",
                        "Aligned FASTA sequences must have equal lengths; record "
                                + (index + 1) + " has length " + sequences.get(index).length()
                                + " instead of " + length + ".",
                        "Align or pad the sequences before calculating a PWM."
                );
            }
        }
        boolean bothStrands = request.bool("bothStrands");
        long observedSequences = sequences.size() * (bothStrands ? 2L : 1L);
        double[][] counts = new double[length][4];
        long ambiguousBases = 0;
        for (String sequence : sequences) {
            ambiguousBases += countSequence(sequence, counts);
            if (bothStrands) {
                ambiguousBases += countSequence(FastaSequenceStore.reverseComplement(sequence), counts);
            }
        }
        double pseudocount = number(request.value("pseudocount"));
        double denominator = observedSequences + 4.0 * pseudocount;
        double[][] frequencies = new double[length][4];
        for (int position = 0; position < length; position++) {
            for (int base = 0; base < 4; base++) {
                frequencies[position][base] =
                        (counts[position][base] + pseudocount) / denominator;
            }
        }
        return new PwmInput(frequencies, sequences.size(), ambiguousBases,
                bothStrands, pseudocount);
    }

    private static long countSequence(String sequence, double[][] counts) {
        long ambiguous = 0;
        for (int position = 0; position < sequence.length(); position++) {
            char character = sequence.charAt(position);
            int[] bases = switch (character) {
                case 'A' -> new int[]{0};
                case 'C' -> new int[]{1};
                case 'G' -> new int[]{2};
                case 'T', 'U' -> new int[]{3};
                case 'R' -> new int[]{0, 2};
                case 'Y' -> new int[]{1, 3};
                case 'S' -> new int[]{1, 2};
                case 'W' -> new int[]{0, 3};
                case 'K' -> new int[]{2, 3};
                case 'M' -> new int[]{0, 1};
                case 'B' -> new int[]{1, 2, 3};
                case 'D' -> new int[]{0, 2, 3};
                case 'H' -> new int[]{0, 1, 3};
                case 'V' -> new int[]{0, 1, 2};
                case 'N' -> new int[]{0, 1, 2, 3};
                default -> throw new OpenToolException(
                        "SEQ_LOGO_BASE_INVALID",
                        "Unsupported aligned FASTA character '" + character + "' at position "
                                + (position + 1) + ".",
                        "Use A/C/G/T/U, IUPAC ambiguity codes, or replace gaps before plotting."
                );
            };
            if (bases.length > 1) {
                ambiguous++;
            }
            for (int base : bases) {
                counts[position][base] += 1.0 / bases.length;
            }
        }
        return ambiguous;
    }

    private static ToolResult render(
            ToolRequest request,
            ToolExecutionContext context,
            GenericPlotData data,
            PwmInput pwm,
            InputMode mode
    ) throws Exception {
        Path base = PlotArtifactSupport.basePath(context, request.path("output"), "seqlogo");
        PlotSpec spec = PlotToolSupport.spec(request, data);
        Map<String, String> lineage = new LinkedHashMap<>(
                PlotArtifactSupport.lineage("plot.seqlogo", spec, request.path("input")));
        lineage.put("inputMode", mode.name());
        lineage.put("sourceSequences", Integer.toString(pwm.sourceSequences()));
        lineage.put("positions", Integer.toString(pwm.positions()));
        lineage.put("pseudocount", Double.toString(pwm.pseudocount()));
        lineage.put("bothStrands", Boolean.toString(pwm.bothStrands()));
        lineage.put("ambiguousBases", Long.toString(pwm.ambiguousBases()));
        PlotArtifactSupport.RenderedPlot rendered = PlotArtifactSupport.render(
                "plot.seqlogo", spec, context, base, lineage);
        List<OutputArtifact> artifacts = new ArrayList<>(rendered.artifacts());
        Path processed = Path.of(base + ".processed.tsv");
        writePwm(processed, pwm);
        artifacts.add(new OutputArtifact(
                processed, "Sequence logo PWM", "text/tab-separated-values"));
        context.progress(1.0, "Sequence logo completed");
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("series", data.series().size());
        metrics.put("chart", data.chart());
        metrics.put("inputMode", mode.name());
        metrics.put("sourceSequences", pwm.sourceSequences());
        metrics.put("positions", pwm.positions());
        metrics.put("pseudocount", pwm.pseudocount());
        metrics.put("bothStrands", pwm.bothStrands());
        metrics.put("ambiguousBases", pwm.ambiguousBases());
        return new ToolResult(
                "Computed sequence logo from " + pwm.positions() + " positions", List.of(pwm.positions()),
                pwm.sourceSequences() == 0
                        ? "Input mode: POSITION_TABLE."
                        : pwm.sourceSequences() + " sequences; " + pwm.ambiguousBases()
                                + " IUPAC ambiguous bases distributed.", List.of(),
                new ResultTable(List.of("Metric", "Value"), List.of(
                        List.of("Input mode", mode.name()),
                        List.of("Positions", Integer.toString(pwm.positions())),
                        List.of("Sequences", Integer.toString(pwm.sourceSequences()))
                )),
                artifacts,
                metrics
        );
    }

    private static void writePwm(Path output, PwmInput pwm) throws Exception {
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            writer.write("position\tA\tC\tG\tT\n");
            for (int position = 0; position < pwm.positions(); position++) {
                writer.write(Integer.toString(position + 1));
                for (int base = 0; base < 4; base++) {
                    writer.write('\t');
                    writer.write(Double.toString(pwm.values()[position][base]));
                }
                writer.newLine();
            }
        }
    }

    private static double number(Object value) {
        return value == null ? 0 : Double.parseDouble(String.valueOf(value));
    }

    private enum InputMode {
        AUTO,
        FASTA,
        POSITION_TABLE
    }

    private record PwmInput(
            double[][] values,
            int sourceSequences,
            long ambiguousBases,
            boolean bothStrands,
            double pseudocount
    ) {
        int positions() {
            return values.length;
        }
    }
}

final class PlotGeneLocationTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = PlotToolSupport.descriptor(
            "plot.gene-location", "Gene Location Plot", "Render gene spans from GFF3/GTF.",
            List.of(
                    ToolParameter.requiredFile("input", "Annotation", "GFF3/GTF gene records."),
                    ToolParameter.string("geneTypes", "Gene types", "Feature types drawn as genes.", "gene"),
                    ToolParameter.string("seqFilter", "Sequence filter", "Optional exact sequence ID filter.", "")
            ),
            "Gene Locations"
    );

    @Override public ToolDescriptor descriptor() { return DESCRIPTOR; }
    @Override public ValidationResult validate(ToolRequest request) { return PlotToolSupport.validate(request); }
    @Override public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        var types = new java.util.LinkedHashSet<String>();
        for (String value : request.string("geneTypes").split("[,;\\s]+")) {
            if (!value.isBlank()) {
                types.add(value.toLowerCase(Locale.ROOT));
            }
        }
        List<GenericPlotData.GenericSeries> series = new ArrayList<>();
        List<List<String>> rows = new ArrayList<>();
        try (GxfReader reader = new GxfReader(request.path("input"))) {
            GxfRecord record;
            int index = 0;
            while ((record = reader.next()) != null) {
                if (!types.isEmpty() && !types.contains(record.type().toLowerCase(Locale.ROOT))) {
                    continue;
                }
                if (!request.string("seqFilter").isBlank()
                        && !record.seqId().equals(request.string("seqFilter"))) {
                    continue;
                }
                series.add(new GenericPlotData.GenericSeries(record.firstAttribute("ID") + "",
                        List.of((double) record.start(), (double) record.end()),
                        List.of((double) index, (double) index)));
                rows.add(List.of(record.seqId(), record.firstAttribute("ID"),
                        Long.toString(record.start()), Long.toString(record.end()),
                        Character.toString(record.strand())));
                index++;
            }
        }
        GenericPlotData data = new GenericPlotData("LINE", "Genomic coordinate", "Gene order",
                List.of(), series);
        return PlotToolSupport.renderGenericWithData(this, request, context, data,
                "gene-location", request.string("title"),
                rows, List.of("seq_id", "gene_id", "start", "end", "strand"), rows.size());
    }
}

final class PlotDomainTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = PlotToolSupport.descriptor(
            "plot.domain", "Domain Plot", "Render protein domains as interval points by protein row.",
            List.of(ToolParameter.requiredFile("input", "Domain table", "TSV with protein, domain, start, end."),
                    ToolParameter.string("proteinColumn", "Protein column", "Protein identifier column.", "protein"),
                    ToolParameter.string("domainColumn", "Domain column", "Domain label column.", "domain"),
                    ToolParameter.string("startColumn", "Start column", "Domain start.", "start"),
                    ToolParameter.string("endColumn", "End column", "Domain end.", "end")),
            "Protein Domains"
    );

    @Override public ToolDescriptor descriptor() { return DESCRIPTOR; }
    @Override public ValidationResult validate(ToolRequest request) { return PlotToolSupport.validate(request); }
    @Override public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        var table = PlotToolSupport.readTable(request.path("input"), null);
        int protein = table.requiredColumn(request.string("proteinColumn"));
        int start = table.requiredColumn(request.string("startColumn"));
        int end = table.requiredColumn(request.string("endColumn"));
        Map<String, GenericPlotData.GenericSeries.Builder> rows = new LinkedHashMap<>();
        for (var row : table.rows()) {
            String name = PlotToolSupport.cell(row, protein);
            double mid = (PlotToolSupport.number(row, start) + PlotToolSupport.number(row, end)) / 2;
            rows.computeIfAbsent(name, GenericPlotData.GenericSeries.Builder::new)
                    .add(mid, rows.size());
        }
        GenericPlotData data = new GenericPlotData("SCATTER", "Protein position", "Protein",
                List.of(), rows.values().stream().map(GenericPlotData.GenericSeries.Builder::build).toList());
        return PlotToolSupport.renderGeneric(this, request, context, data,
                "domain", request.string("title"), rows.size());
    }
}

final class PlotMotifTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = PlotToolSupport.descriptor(
            "plot.motif", "Motif Plot", "Plot motif hits by start position and sequence row.",
            List.of(ToolParameter.requiredFile("input", "Motif table", "TSV with seq_id, motif, start."),
                    ToolParameter.string("sequenceColumn", "Sequence column", "Sequence identifier column.", "seq_id"),
                    ToolParameter.string("startColumn", "Start column", "Motif start column.", "start"),
                    ToolParameter.string("motifColumn", "Motif column", "Motif label column.", "motif")),
            "Motif Hits"
    );

    @Override public ToolDescriptor descriptor() { return DESCRIPTOR; }
    @Override public ValidationResult validate(ToolRequest request) { return PlotToolSupport.validate(request); }
    @Override public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        var table = PlotToolSupport.readTable(request.path("input"), null);
        int seq = table.requiredColumn(request.string("sequenceColumn"));
        int start = table.requiredColumn(request.string("startColumn"));
        int motif = table.requiredColumn(request.string("motifColumn"));
        Map<String, GenericPlotData.GenericSeries.Builder> rows = new LinkedHashMap<>();
        for (var row : table.rows()) {
            String name = PlotToolSupport.cell(row, motif);
            rows.computeIfAbsent(name, GenericPlotData.GenericSeries.Builder::new)
                    .add(PlotToolSupport.number(row, start), rows.size());
        }
        GenericPlotData data = new GenericPlotData("SCATTER", "Position", "Motif",
                List.of(), rows.values().stream().map(GenericPlotData.GenericSeries.Builder::build).toList());
        return PlotToolSupport.renderGeneric(this, request, context, data,
                "motif", request.string("title"), rows.size());
    }
}

final class PlotCircosTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = PlotToolSupport.descriptor(
            "plot.circos", "Circos Overview", "Render sequence spans as a circular multi-track overview.",
            List.of(ToolParameter.requiredFile("input", "Feature table", "TSV with sequence, start, end, label."),
                    ToolParameter.string("sequenceColumn", "Sequence column", "Sequence identifier column.", "seq_id"),
                    ToolParameter.string("startColumn", "Start column", "Feature start column.", "start"),
                    ToolParameter.string("endColumn", "End column", "Feature end column.", "end"),
                    ToolParameter.string("labelColumn", "Label column", "Feature label column.", "label")),
            "Circos Overview"
    );

    @Override public ToolDescriptor descriptor() { return DESCRIPTOR; }
    @Override public ValidationResult validate(ToolRequest request) { return PlotToolSupport.validate(request); }
    @Override public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        var table = PlotToolSupport.readTable(request.path("input"), null);
        int sequence = table.requiredColumn(request.string("sequenceColumn"));
        int start = table.requiredColumn(request.string("startColumn"));
        int end = table.requiredColumn(request.string("endColumn"));
        Map<String, GenericPlotData.GenericSeries.Builder> series = new LinkedHashMap<>();
        for (var row : table.rows()) {
            String name = PlotToolSupport.cell(row, sequence);
            series.computeIfAbsent(name, GenericPlotData.GenericSeries.Builder::new)
                    .add(PlotToolSupport.number(row, start), PlotToolSupport.number(row, end));
        }
        GenericPlotData data = new GenericPlotData("CIRCOS", "Genomic coordinate", "",
                List.copyOf(series.keySet()), series.values().stream()
                .map(GenericPlotData.GenericSeries.Builder::build).toList());
        return PlotToolSupport.renderGeneric(this, request, context, data,
                "circos", request.string("title"), series.size());
    }
}

final class PlotEfpTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = PlotToolSupport.descriptor(
            "plot.efp", "eFP Expression",
            "Render a tissue-grouped expression map with sample bands, ordering, and a reproducible heatmap.",
            List.of(
                    ToolParameter.requiredFile("input", "Expression matrix", "Genes in rows and tissues/samples in header."),
                    ToolParameter.optionalFile("sampleMap", "Sample map",
                            "Optional TSV with sample and tissue columns; samples are grouped and sorted by tissue."),
                    new ToolParameter("normalization", "Value normalization",
                            "Optionally calculate a z-score within each gene row.",
                            ParameterType.ENUM, false, "NONE",
                            List.of("NONE", "ROW_ZSCORE"), null, null, false, null),
                    ToolParameter.bool("exportData", "Export processed data", "Write the reordered matrix.", true)
            ),
            "Expression eFP"
    );

    @Override public ToolDescriptor descriptor() { return DESCRIPTOR; }

    @Override
    public ValidationResult validate(ToolRequest request) {
        ValidationResult result = PlotToolSupport.validate(request);
        try {
            readSampleMap(request.path("sampleMap"));
        } catch (Exception ex) {
            result = result.merge(ValidationResult.of(io.opentools.api.tool.ValidationIssue.error(
                    "sampleMap", ex.getMessage(), "Use a TSV with sample and tissue columns.")));
        }
        return result;
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        Map<String, String> tissueBySample = readSampleMap(request.path("sampleMap"));
        MatrixParser.Config config = new MatrixParser.Config(
                request.path("input"), "AUTO", "ROW_MEAN", "AS_MISSING",
                "ORIGINAL", "ORIGINAL",
                request.string("normalization") == null ? "NONE" : request.string("normalization"),
                5000, 5000, 1000
        );
        context.progress(0.08, "Parsing expression matrix");
        MatrixParser.MatrixResult parsed = new MatrixParser().parse(
                config, context.cancellationToken(), 1);
        HeatmapData original = parsed.data();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < original.columnLabels().size(); i++) {
            order.add(i);
        }
        order.sort(java.util.Comparator
                .comparing((Integer index) -> tissueBySample.getOrDefault(
                        original.columnLabels().get(index), "Unassigned"))
                .thenComparingInt(Integer::intValue));
        List<String> columns = order.stream().map(original.columnLabels()::get).toList();
        List<List<Double>> values = new ArrayList<>();
        for (List<Double> row : original.values()) {
            values.add(order.stream().map(row::get).toList());
        }
        HeatmapData data = new HeatmapData(
                original.rowLabels(), columns, values, "ORIGINAL",
                tissueBySample.isEmpty() ? "ORIGINAL" : "TISSUE",
                original.sourceRows(), original.sourceColumns(),
                original.missingValues(), original.invalidValues(), original.clippedValues(),
                1, 1, original.diagnostics()
        );
        double minimum = parsed.minimum();
        double maximum = parsed.maximum();
        if (maximum <= minimum) {
            maximum = minimum + 1;
        }
        PlotStyle baseStyle = PlotStyle.heatmap(PlotStyle.defaults().palette(), false);
        PlotStyle style = new PlotStyle(
                baseStyle.backgroundColor(), baseStyle.foregroundColor(), baseStyle.mutedColor(),
                baseStyle.accentColor(), baseStyle.missingColor(), baseStyle.palette(),
                baseStyle.fontFamily(), baseStyle.baseFontSize(), baseStyle.lineWidth(),
                true, false, minimum, maximum,
                request.string("normalization") == null ? "NONE" : request.string("normalization"),
                1, 1
        );
        Path base = PlotArtifactSupport.basePath(context, request.path("output"), "efp");
        List<String> formats = switch (request.string("exportFormats") == null ? "PNG_SVG_PDF"
                : request.string("exportFormats")) {
            case "PNG_SVG" -> List.of("PNG", "SVG");
            case "PNG" -> List.of("PNG");
            default -> List.of("PNG", "SVG", "PDF");
        };
        PlotExport export = new PlotExport(
                formats, base.getFileName().toString(),
                request.integer("width") <= 0 ? 1200 : request.integer("width"),
                request.integer("height") <= 0 ? 800 : request.integer("height"),
                request.integer("dpi") <= 0 ? 144 : request.integer("dpi"),
                false, request.bool("overwrite")
        );
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("rowOrder", "ORIGINAL");
        metadata.put("columnOrder", data.columnOrder());
        metadata.put("normalization", style.normalization());
        metadata.put("sampleMap", request.path("sampleMap") == null ? "" : request.path("sampleMap").toString());
        for (int i = 0; i < columns.size(); i++) {
            metadata.put("sampleGroup." + i, tissueBySample.getOrDefault(columns.get(i), "Unassigned"));
        }
        PlotSpec spec = new PlotSpec(
                "1.0", "efp", PlotType.HEATMAP,
                request.string("title") == null ? "Expression eFP" : request.string("title"),
                request.string("subtitle") == null ? "" : request.string("subtitle"),
                style, export, metadata, data
        );
        Path processed = Path.of(base + ".processed.tsv");
        if (request.bool("exportData")) {
            writeEfpMatrix(processed, data);
        }
        context.progress(0.55, "Rendering eFP map");
        PlotArtifactSupport.RenderedPlot rendered = PlotArtifactSupport.render(
                descriptor().id(), spec, context, base,
                PlotArtifactSupport.lineage(descriptor().id(), spec, request.path("input"))
        );
        context.progress(1.0, "eFP rendering completed");
        List<OutputArtifact> artifacts = new ArrayList<>(rendered.artifacts());
        if (request.bool("exportData")) {
            artifacts.add(new OutputArtifact(processed, "Processed eFP matrix", "text/tab-separated-values"));
        }
        long sampleGroups = tissueBySample.values().stream().distinct().count();
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("genes", data.rowLabels().size());
        metrics.put("samples", data.columnLabels().size());
        metrics.put("sampleGroups", (int) sampleGroups);
        metrics.put("normalization", style.normalization());
        metrics.put("missingValues", data.missingValues());
        return new ToolResult(
                "Rendered eFP expression map",
                List.of(data.rowLabels().size(), data.columnLabels().size()),
                data.rowLabels().size() + " genes x " + data.columnLabels().size()
                        + " samples; " + (tissueBySample.isEmpty() ? "no sample map" : "grouped by tissue") + ".",
                List.of(data.rowLabels().size()),
                new ResultTable(List.of("Metric", "Value"), List.of(
                        List.of("Genes", Integer.toString(data.rowLabels().size())),
                        List.of("Samples", Integer.toString(data.columnLabels().size())),
                        List.of("Sample groups", Integer.toString((int) sampleGroups)),
                        List.of("Normalization", style.normalization())
                )),
                artifacts, metrics
        );
    }

    private static Map<String, String> readSampleMap(Path path) throws Exception {
        if (path == null) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(
                Compression.open(path), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null) {
                throw new OpenToolException("SAMPLE_MAP_EMPTY", "Sample map is empty.",
                        "Provide a TSV with sample and tissue columns.");
            }
            String[] names = header.replace("\ufeff", "").split("\t", -1);
            int sample = -1;
            int tissue = -1;
            for (int i = 0; i < names.length; i++) {
                if (names[i].trim().equalsIgnoreCase("sample")) sample = i;
                if (names[i].trim().equalsIgnoreCase("tissue")) tissue = i;
            }
            if (sample < 0 || tissue < 0) {
                throw new OpenToolException("SAMPLE_MAP_HEADER",
                        "Sample map needs sample and tissue columns.",
                        "Use a tab-separated header such as sample<TAB>tissue.");
            }
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] fields = line.split("\t", -1);
                if (fields.length <= Math.max(sample, tissue)) continue;
                String sampleId = fields[sample].trim();
                String tissueName = fields[tissue].trim();
                if (!sampleId.isEmpty() && !tissueName.isEmpty()) {
                    result.put(sampleId, tissueName);
                }
            }
        }
        return Map.copyOf(result);
    }

    private static void writeEfpMatrix(Path path, HeatmapData data) throws Exception {
        Files.createDirectories(path.toAbsolutePath().getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write("gene");
            for (String column : data.columnLabels()) {
                writer.write('\t');
                writer.write(column);
            }
            writer.newLine();
            for (int row = 0; row < data.rowLabels().size(); row++) {
                writer.write(data.rowLabels().get(row));
                for (Double value : data.values().get(row)) {
                    writer.write('\t');
                    writer.write(value == null ? "" : Double.toString(value));
                }
                writer.newLine();
            }
        }
    }
}
