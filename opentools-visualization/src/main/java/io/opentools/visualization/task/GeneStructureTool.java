package io.opentools.visualization.task;

import io.opentools.api.tool.AbstractOpenTool;
import io.opentools.api.tool.OpenToolException;
import io.opentools.api.tool.OutputArtifact;
import io.opentools.api.tool.ParameterType;
import io.opentools.api.tool.ResultTable;
import io.opentools.api.tool.TaskContext;
import io.opentools.api.tool.TaskResult;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolParameter;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ValidationIssue;
import io.opentools.api.tool.ValidationResult;
import io.opentools.formats.gxf.GxfReader;
import io.opentools.formats.gxf.GxfRecord;
import io.opentools.visualization.model.GeneStructureData;
import io.opentools.visualization.model.PlotExport;
import io.opentools.visualization.model.PlotSpec;
import io.opentools.visualization.model.PlotStyle;
import io.opentools.visualization.model.PlotType;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class GeneStructureTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "visual.gene-structure",
            "Gene Structure",
            "Render selected GFF3/GTF genes as a compact transcript and feature diagram.",
            "Visualization",
            List.of("gene structure", "gene model", "transcript diagram", "基因结构", "转录本"),
            List.of("GFF3", "GTF", "GFF3.gz", "GTF.gz"),
            List.of("SVG", "PNG", "PDF", "JSON"),
            "1.0.0",
            List.of(
                    ToolParameter.requiredFile("input", "Input annotation", "A GFF3 or GTF annotation file, optionally gzip-compressed."),
                    ToolParameter.optionalText("geneIds", "Gene IDs", "Optional gene IDs, separated by new lines, commas, or semicolons. Leave blank for the first genes."),
                    ToolParameter.string("featureTypes", "Feature types", "Comma-separated feature types included in transcript tracks.", "exon,CDS"),
                    integerParameter("maxGenes", "Maximum genes", "Maximum gene loci included in the plot.", 20, 1, 200, false),
                    ToolParameter.string("title", "Plot title", "Optional title written into the plot model.", "Gene structures"),
                    ToolParameter.string("subtitle", "Plot subtitle", "Optional subtitle written into the plot model.", ""),
                    integerParameter("width", "Image width", "Rendered width in pixels.", 1200, 320, 10000, true),
                    integerParameter("height", "Image height", "Rendered height in pixels.", 800, 240, 10000, true),
                    integerParameter("dpi", "PDF DPI", "Resolution metadata and physical PDF page size.", 144, 72, 600, true),
                    enumParameter("exportFormats", "Export formats", "Choose the headless artifact formats.", "PNG_SVG_PDF",
                            List.of("PNG_SVG_PDF", "PNG_SVG", "PNG"), true),
                    ToolParameter.bool("exportData", "Export feature coordinates", "Write rendered feature coordinates as TSV.", true),
                    ToolParameter.optionalOutputFile("output", "Output prefix", "Optional base path for plot and image outputs."),
                    ToolParameter.bool("overwrite", "Overwrite outputs", "Allow replacing existing custom output files.", false)
            )
    );

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ValidationResult validate(ToolRequest request) {
        ValidationResult result = validateParameters(request);
        Path requested = request.path("output");
        if (requested != null && !request.bool("overwrite")) {
            Path base = PlotTaskSupport.stripKnownExtensions(requested);
            List<Path> expected = new ArrayList<>();
            expected.add(PlotTaskSupport.plotDocumentPath(base));
            exportFormats(request).forEach(format -> expected.add(Path.of(base + "." + format.toLowerCase(Locale.ROOT))));
            if (request.bool("exportData")) {
                expected.add(Path.of(base + ".features.tsv"));
            }
            if (PlotTaskSupport.anyExists(expected)) {
                result = result.merge(ValidationResult.of(ValidationIssue.error(
                        "output",
                        "One or more gene-structure outputs already exist.",
                        "Choose another output prefix or enable overwrite."
                )));
            }
        }
        return result;
    }

    @Override
    public TaskResult execute(ToolRequest request, TaskContext context) throws Exception {
        Set<String> selectedGenes = parseSelectors(request.string("geneIds"));
        Set<String> featureTypes = parseFeatureTypes(request.string("featureTypes"));
        int maximumGenes = request.integer("maxGenes");
        Map<String, MutableGene> genes = new LinkedHashMap<>();
        Map<String, String> recordToGene = new LinkedHashMap<>();
        Map<String, String> transcriptToGene = new LinkedHashMap<>();
        Set<String> resolvedSelectedGenes = new LinkedHashSet<>();
        long annotationRecords = 0;

        context.progress(0.08, "Reading gene annotation");
        try (GxfReader reader = new GxfReader(request.path("input"))) {
            GxfRecord record;
            while ((record = reader.next()) != null) {
                context.cancellationToken().throwIfCancelled();
                annotationRecords++;
                String type = record.type().toLowerCase(Locale.ROOT);
                String recordSequenceId = record.seqId();
                long recordStart = record.start();
                long recordEnd = record.end();
                char recordStrand = record.strand();
                String geneId = record.firstAttribute("gene_id", "geneId");
                if ((geneId == null || geneId.isBlank()) && type.equals("gene")) {
                    geneId = record.firstAttribute("ID", "Name", "gene_name");
                }
                String parent = record.firstAttribute("Parent");
                if (geneId == null || geneId.isBlank()) {
                    if (parent != null) {
                        geneId = transcriptToGene.getOrDefault(parent, recordToGene.get(parent));
                    }
                }
                if ((geneId == null || geneId.isBlank())
                        && (type.equals("mrna") || type.equals("transcript"))) {
                    geneId = record.firstAttribute("ID");
                }
                if (geneId == null || geneId.isBlank()) {
                    geneId = "unknown_" + record.seqId();
                }
                String recordId = record.firstAttribute("ID");
                if (recordId != null) {
                    recordToGene.put(recordId, geneId);
                }
                if (parent != null) {
                    for (String parentId : parent.split(",")) {
                        recordToGene.put(parentId.trim(), geneId);
                    }
                }
                String transcriptId = record.firstAttribute("transcript_id");
                if (transcriptId == null && (type.equals("mrna") || type.equals("transcript"))) {
                    transcriptId = recordId;
                }
                if (transcriptId == null && parent != null) {
                    transcriptId = parent.split(",")[0].trim();
                }
                if (transcriptId == null || transcriptId.isBlank()) {
                    transcriptId = geneId + "_transcript";
                }
                transcriptToGene.put(transcriptId, geneId);
                String resolvedGeneId = geneId;
                String resolvedTranscriptId = transcriptId;

                boolean matchesGene;
                if (selectedGenes.isEmpty()) {
                    matchesGene = true;
                } else if (selectedGenes.contains(resolvedGeneId)
                        || selectedGenes.contains(record.firstAttribute("Name", "gene_name"))) {
                    matchesGene = true;
                    resolvedSelectedGenes.add(resolvedGeneId);
                } else {
                    matchesGene = resolvedSelectedGenes.contains(resolvedGeneId);
                }
                if (!matchesGene) {
                    continue;
                }
                MutableGene gene = genes.computeIfAbsent(
                        resolvedGeneId,
                        ignored -> new MutableGene(resolvedGeneId, recordSequenceId, recordStrand)
                );
                gene.recordsRead++;
                if (record.start() < gene.start) {
                    gene.start = record.start();
                }
                if (record.end() > gene.end) {
                    gene.end = record.end();
                }
                if (record.strand() != '.') {
                    gene.strand = record.strand();
                }
                if (featureTypes.contains(type) && !type.equals("gene")) {
                    MutableTranscript transcript = gene.transcripts.computeIfAbsent(
                            resolvedTranscriptId,
                            id -> new MutableTranscript(id, recordStart, recordEnd, recordStrand)
                    );
                    transcript.start = Math.min(transcript.start, record.start());
                    transcript.end = Math.max(transcript.end, record.end());
                    if (record.strand() != '.') {
                        transcript.strand = record.strand();
                    }
                    transcript.features.add(new GeneStructureData.Feature(
                            record.type(),
                            record.start(),
                            record.end(),
                            record.strand()
                    ));
                }
            }
        }
        if (genes.isEmpty()) {
            throw new OpenToolException(
                    "VIS_GENE_NOT_FOUND",
                    selectedGenes.isEmpty() ? "No gene structures were found." : "None of the requested genes were found.",
                    "Check the annotation feature types and gene identifiers."
            );
        }
        List<GeneStructureData.GeneLocus> loci = genes.values().stream()
                .sorted(Comparator.comparing((MutableGene gene) -> gene.sequenceId)
                        .thenComparingLong(gene -> gene.start)
                        .thenComparing(gene -> gene.id))
                .limit(maximumGenes)
                .map(MutableGene::materialize)
                .toList();
        long renderedFeatures = loci.stream()
                .flatMap(locus -> locus.transcripts().stream())
                .mapToLong(transcript -> transcript.features().size())
                .sum();
        PlotType plotType = PlotType.GENE_STRUCTURE;
        Path base = PlotTaskSupport.basePath(context, request.path("output"), "gene-structures");
        PlotSpec spec = new PlotSpec(
                "1.0",
                "gene-structures",
                plotType,
                request.string("title"),
                request.string("subtitle"),
                PlotStyle.defaults(),
                new PlotExport(
                        exportFormats(request),
                        base.getFileName().toString(),
                        request.integer("width"),
                        request.integer("height"),
                        request.integer("dpi"),
                        false,
                        request.bool("overwrite")
                ),
                Map.of(
                        "annotationRecords", Long.toString(annotationRecords),
                        "geneCount", Integer.toString(genes.size()),
                        "renderedGeneCount", Integer.toString(loci.size()),
                        "featureTypes", String.join(",", featureTypes)
                ),
                new GeneStructureData(loci)
        );
        Path featureTable = Path.of(base + ".features.tsv");
        if (request.bool("exportData")) {
            writeFeatures(featureTable, loci);
        }
        context.progress(0.58, "Rendering gene structures");
        PlotTaskSupport.RenderedPlot rendered = PlotTaskSupport.render(
                descriptor().id(),
                spec,
                context,
                base,
                PlotTaskSupport.lineage(descriptor().id(), spec, request.path("input"))
        );
        List<OutputArtifact> artifacts = new ArrayList<>(rendered.artifacts());
        if (request.bool("exportData")) {
            artifacts.add(new OutputArtifact(featureTable, "Rendered feature coordinates", "text/tab-separated-values"));
        }
        ResultTable table = new ResultTable(
                List.of("Gene", "Sequence", "Start", "End", "Strand", "Transcripts", "Features"),
                loci.stream().limit(50).map(locus -> List.of(
                        locus.id(),
                        locus.sequenceId(),
                        Long.toString(locus.start()),
                        Long.toString(locus.end()),
                        Character.toString(locus.strand()),
                        Integer.toString(locus.transcripts().size()),
                        Long.toString(locus.transcripts().stream().mapToLong(transcript -> transcript.features().size()).sum())
                )).toList()
        );
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("annotationRecords", annotationRecords);
        metrics.put("genesFound", genes.size());
        metrics.put("genesRendered", loci.size());
        metrics.put("featuresRendered", renderedFeatures);
        metrics.put("exportFormats", exportFormats(request));
        context.progress(1.0, "Gene-structure plot completed");
        return new TaskResult(
                "Rendered " + loci.size() + " gene structures",
                "result.visual.gene.summary",
                List.of(loci.size()),
                renderedFeatures + " transcript features across " + loci.size() + " genes",
                "result.visual.gene.detail",
                List.of(renderedFeatures, loci.size()),
                table,
                artifacts,
                metrics
        );
    }

    private static void writeFeatures(Path output, List<GeneStructureData.GeneLocus> loci) throws Exception {
        Files.createDirectories(output.toAbsolutePath().getParent());
        try (var writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            writer.write("gene\tsequence\tstrand\ttranscript\tfeature\tstart\tend");
            writer.newLine();
            for (GeneStructureData.GeneLocus locus : loci) {
                for (GeneStructureData.Transcript transcript : locus.transcripts()) {
                    for (GeneStructureData.Feature feature : transcript.features()) {
                        writer.write(locus.id());
                        writer.write('\t');
                        writer.write(locus.sequenceId());
                        writer.write('\t');
                        writer.write(Character.toString(locus.strand()));
                        writer.write('\t');
                        writer.write(transcript.id());
                        writer.write('\t');
                        writer.write(feature.type());
                        writer.write('\t');
                        writer.write(Long.toString(feature.start()));
                        writer.write('\t');
                        writer.write(Long.toString(feature.end()));
                        writer.newLine();
                    }
                }
            }
        }
    }

    private static Set<String> parseSelectors(String value) {
        Set<String> result = new LinkedHashSet<>();
        if (value == null || value.isBlank()) {
            return result;
        }
        for (String token : value.split("[,\\n;]+")) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private static Set<String> parseFeatureTypes(String value) {
        Set<String> result = new LinkedHashSet<>();
        for (String token : value.split(",")) {
            String trimmed = token.trim().toLowerCase(Locale.ROOT);
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        if (result.isEmpty()) {
            result.add("exon");
            result.add("cds");
        }
        return result;
    }

    private static List<String> exportFormats(ToolRequest request) {
        return switch (request.string("exportFormats")) {
            case "PNG_SVG" -> List.of("PNG", "SVG");
            case "PNG" -> List.of("PNG");
            default -> List.of("PNG", "SVG", "PDF");
        };
    }

    private static ToolParameter enumParameter(
            String id,
            String title,
            String description,
            String defaultValue,
            List<String> options,
            boolean advanced
    ) {
        return new ToolParameter(id, title, description, ParameterType.ENUM, false, defaultValue,
                options, null, null, advanced, null);
    }

    private static ToolParameter integerParameter(
            String id,
            String title,
            String description,
            int defaultValue,
            int minimum,
            int maximum,
            boolean advanced
    ) {
        return new ToolParameter(id, title, description, ParameterType.INTEGER, false, defaultValue,
                List.of(), (double) minimum, (double) maximum, advanced, null);
    }

    private static final class MutableGene {
        private final String id;
        private final String sequenceId;
        private final Map<String, MutableTranscript> transcripts = new LinkedHashMap<>();
        private long start = Long.MAX_VALUE;
        private long end = Long.MIN_VALUE;
        private char strand;
        private int recordsRead;

        private MutableGene(String id, String sequenceId, char strand) {
            this.id = id;
            this.sequenceId = sequenceId;
            this.strand = strand;
        }

        private GeneStructureData.GeneLocus materialize() {
            List<GeneStructureData.Transcript> materialized = transcripts.values().stream()
                    .sorted(Comparator.comparingLong(transcript -> transcript.start))
                    .map(MutableTranscript::materialize)
                    .toList();
            long geneStart = start == Long.MAX_VALUE ? 1 : start;
            long geneEnd = end == Long.MIN_VALUE ? geneStart : end;
            return new GeneStructureData.GeneLocus(id, sequenceId, geneStart, geneEnd, strand, materialized);
        }
    }

    private static final class MutableTranscript {
        private final String id;
        private long start;
        private long end;
        private char strand;
        private final List<GeneStructureData.Feature> features = new ArrayList<>();

        private MutableTranscript(String id, long start, long end, char strand) {
            this.id = id;
            this.start = start;
            this.end = end;
            this.strand = strand;
        }

        private GeneStructureData.Transcript materialize() {
            return new GeneStructureData.Transcript(
                    id,
                    start,
                    end,
                    strand,
                    features.stream().sorted(Comparator.comparingLong(GeneStructureData.Feature::start)).toList()
            );
        }
    }
}
