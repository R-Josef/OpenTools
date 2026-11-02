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
import io.opentools.visualization.model.PlotSpec;

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
                "Computed sequence logo from " + pwm.positions() + " positions",
                "result.plot.seqlogo.summary", List.of(pwm.positions()),
                pwm.sourceSequences() == 0
                        ? "Input mode: POSITION_TABLE."
                        : pwm.sourceSequences() + " sequences; " + pwm.ambiguousBases()
                                + " IUPAC ambiguous bases distributed.",
                "result.plot.seqlogo.detail", List.of(),
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
                "gene-location", request.string("title"), "result.plot.gene_location.summary",
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
                "domain", request.string("title"), "result.plot.domain.summary", rows.size());
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
                "motif", request.string("title"), "result.plot.motif.summary", rows.size());
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
                "circos", request.string("title"), "result.plot.circos.summary", series.size());
    }
}

final class PlotEfpTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = PlotToolSupport.descriptor(
            "plot.efp", "eFP Expression", "Render tissue/sample expression as a reproducible heatmap.",
            List.of(ToolParameter.requiredFile("input", "Expression matrix", "Genes in rows and tissues/samples in header."),
                    ToolParameter.bool("exportData", "Export processed data", "Write processed matrix.", true)),
            "Expression eFP"
    );

    @Override public ToolDescriptor descriptor() { return DESCRIPTOR; }
    @Override public ValidationResult validate(ToolRequest request) { return PlotToolSupport.validate(request); }
    @Override public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        ToolResult delegated = new HeatmapTool().execute(
                new ToolRequest("visual.heatmap", PlotToolSupport.heatmapParameters(request)), context);
        return new ToolResult("Rendered expression eFP heatmap",
                "result.plot.efp.summary", List.of(), "Tissue/sample matrix rendered as a heatmap.",
                "result.plot.efp.detail", List.of(), delegated.table(),
                delegated.artifacts(), delegated.metrics());
    }
}
