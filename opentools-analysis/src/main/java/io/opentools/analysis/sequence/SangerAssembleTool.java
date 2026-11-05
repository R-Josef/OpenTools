package io.opentools.analysis.sequence;

import io.opentools.api.tool.AbstractOpenTool;
import io.opentools.api.tool.OpenToolException;
import io.opentools.api.tool.OutputArtifact;
import io.opentools.api.tool.ParameterType;
import io.opentools.api.tool.ResultTable;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolParameter;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ToolResult;
import io.opentools.api.tool.ValidationIssue;
import io.opentools.api.tool.ValidationResult;
import io.opentools.formats.fasta.FastaReader;
import io.opentools.formats.fasta.FastaRecord;
import io.opentools.formats.fastq.FastqReader;
import io.opentools.formats.fastq.FastqRecord;
import io.opentools.formats.sanger.Ab1Reader;

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

/** Overlap-first Sanger assembly with optional reference anchoring. */
public final class SangerAssembleTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "sanger.assemble",
            "Sanger Assembly",
            "Assemble AB1, FASTQ, or FASTA Sanger reads by overlap, optionally anchor them to a reference, and report consensus provenance.",
            "Sequence",
            List.of("Sanger", "AB1", "assembly", "consensus", "测序拼接"),
            List.of("AB1", "FASTQ", "FASTA"),
            List.of("FASTA", "TSV", "BED"),
            "1.0.0",
            List.of(
                    new ToolParameter("readsDirectory", "Reads directory",
                            "Directory containing AB1, FASTQ, or FASTA reads. AB1 is preferred when present.",
                            ParameterType.DIRECTORY, true, null, List.of(), null, null, false, null),
                    ToolParameter.optionalFile("reference", "Reference FASTA",
                            "Optional reference sequence used to anchor reads and fill uncovered positions."),
                    ToolParameter.integer("minOverlap", "Minimum overlap",
                            "Minimum overlap length for joining reads.", 30, 10, 10_000),
                    ToolParameter.decimal("minIdentity", "Minimum overlap identity",
                            "Minimum fraction of matching bases in an overlap.", 0.85, 0.5, 1.0),
                    ToolParameter.integer("trimQuality", "End-trim quality",
                            "Mean Phred quality required in the end-trimming window.", 20, 0, 60),
                    ToolParameter.integer("trimWindow", "End-trim window",
                            "Sliding window used to trim low-quality read ends.", 8, 1, 100),
                    ToolParameter.integer("minReadLength", "Minimum read length",
                            "Reads shorter than this after trimming are rejected.", 50, 1, 100_000),
                    ToolParameter.decimal("consensusThreshold", "Consensus threshold",
                            "Minimum quality-weighted base fraction for a confident call.", 0.60, 0.5, 1.0),
                    ToolParameter.optionalDirectory("outputDirectory", "Output directory",
                            "Leave blank to save in the run directory."),
                    ToolParameter.bool("overwrite", "Overwrite outputs",
                            "Allow replacing existing output files.", false)
            )
    );

    @Override public ToolDescriptor descriptor() { return DESCRIPTOR; }

    @Override
    public ValidationResult validate(ToolRequest request) {
        ValidationResult result = validateParameters(request);
        Path reads = request.path("readsDirectory");
        if (reads != null && !Files.isDirectory(reads)) {
            result = result.merge(ValidationResult.of(ValidationIssue.error(
                    "readsDirectory", "Reads directory does not exist: " + reads,
                    "Choose a directory containing AB1, FASTQ, or FASTA reads.")));
        }
        Path reference = request.path("reference");
        if (reference != null && !Files.isRegularFile(reference)) {
            result = result.merge(ValidationResult.of(ValidationIssue.error(
                    "reference", "Reference FASTA does not exist: " + reference,
                    "Choose a reference FASTA or leave the parameter blank.")));
        }
        return result;
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        Path readsDirectory = request.path("readsDirectory");
        Path referencePath = request.path("reference");
        Path outputDirectory = request.path("outputDirectory");
        if (outputDirectory == null) outputDirectory = context.outputDirectory();
        Files.createDirectories(outputDirectory);
        Map<String, Path> outputs = outputNames(outputDirectory);
        if (!request.bool("overwrite")) {
            for (Path path : outputs.values()) {
                if (Files.exists(path)) {
                    throw new OpenToolException(
                            "SANGER_OUTPUT_EXISTS",
                            "Output already exists: " + path,
                            "Enable overwrite or choose another output directory."
                    );
                }
            }
        }

        List<Path> files = readFiles(readsDirectory, referencePath);
        if (files.isEmpty()) {
            throw new OpenToolException(
                    "SANGER_NO_READS",
                    "No AB1, FASTQ, or FASTA reads were found in " + readsDirectory,
                    "Add AB1/FASTQ/FASTA reads to the directory."
            );
        }
        int trimQuality = request.integer("trimQuality");
        int trimWindow = request.integer("trimWindow");
        int minReadLength = request.integer("minReadLength");
        List<ReadRecord> reads = new ArrayList<>();
        for (Path file : files) {
            context.cancellationToken().throwIfCancelled();
            reads.addAll(loadReads(file, trimQuality, trimWindow, minReadLength));
        }
        if (reads.isEmpty()) {
            throw new OpenToolException(
                    "SANGER_READS_EMPTY",
                    "All reads were empty or shorter than the minimum length after trimming.",
                    "Lower minReadLength or trimQuality."
            );
        }
        int minOverlap = request.integer("minOverlap");
        double minIdentity = decimalValue(request, "minIdentity");
        Assembly assembly = assemble(reads, minOverlap, minIdentity,
                decimalValue(request, "consensusThreshold"));
        if (assembly.contigs().isEmpty()) {
            throw new OpenToolException(
                    "SANGER_ASSEMBLY_EMPTY",
                    "The reads did not form any contig.",
                    "Lower minOverlap/minIdentity or inspect the read quality."
            );
        }

        ReferenceAnchor anchor = null;
        FastaRecord reference = null;
        if (referencePath != null) {
            reference = readReference(referencePath);
            anchor = ReferenceAnchor.place(reference.sequence(), reads, minOverlap);
        }

        writeFasta(outputs.get("assembly"), assembly.contigs());
        writeReadTable(outputs.get("reads"), reads, anchor);
        if (reference != null && anchor != null) {
            Consensus consensus = anchor.consensus(decimalValue(request, "consensusThreshold"));
            writeConsensus(outputs.get("corrected"), reference, consensus);
            writeVariants(outputs.get("variants"), reference, consensus);
            writeProvenance(outputs.get("provenance"), reference, consensus);
        }

        List<OutputArtifact> artifacts = new ArrayList<>();
        artifacts.add(new OutputArtifact(outputs.get("assembly"), "Assembled contigs", "text/x-fasta"));
        artifacts.add(new OutputArtifact(outputs.get("reads"), "Read and placement summary",
                "text/tab-separated-values"));
        if (reference != null && anchor != null) {
            artifacts.add(new OutputArtifact(outputs.get("corrected"), "Reference-anchored consensus",
                    "text/x-fasta"));
            artifacts.add(new OutputArtifact(outputs.get("variants"), "Consensus variants",
                    "text/tab-separated-values"));
            artifacts.add(new OutputArtifact(outputs.get("provenance"), "Consensus provenance",
                    "text/plain"));
        }
        long totalLength = assembly.contigs().stream().mapToLong(contig -> contig.sequence().length()).sum();
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("reads", reads.size());
        metrics.put("contigs", assembly.contigs().size());
        metrics.put("assembledBases", totalLength);
        metrics.put("referenceUsed", reference != null);
        metrics.put("placements", anchor == null ? 0 : anchor.placements().size());
        if (reference != null && anchor != null) {
            metrics.put("referenceLength", reference.sequence().length());
            metrics.put("coveredReferenceBases", anchor.coveredBases());
        }
        context.progress(1.0, "Sanger assembly completed");
        return new ToolResult(
                "Assembled " + reads.size() + " reads into " + assembly.contigs().size()
                        + " contig(s)" + (reference == null ? "" : " with reference anchoring"),
                List.of(reads.size(), assembly.contigs().size()),
                totalLength + " assembled bases" + (reference == null ? "" : "; reference consensus written"),
                List.of(totalLength),
                new ResultTable(List.of("Metric", "Value"), List.of(
                        List.of("Reads", Integer.toString(reads.size())),
                        List.of("Contigs", Integer.toString(assembly.contigs().size())),
                        List.of("Assembled bases", Long.toString(totalLength)),
                        List.of("Reference", reference == null ? "not used" : reference.id())
                )),
                artifacts,
                metrics
        );
    }

    private static Map<String, Path> outputNames(Path directory) {
        Map<String, Path> names = new LinkedHashMap<>();
        names.put("assembly", directory.resolve("assembly.fa"));
        names.put("reads", directory.resolve("reads.tsv"));
        names.put("corrected", directory.resolve("corrected.fa"));
        names.put("variants", directory.resolve("variants.tsv"));
        names.put("provenance", directory.resolve("provenance.bed"));
        return names;
    }

    private static List<Path> readFiles(Path directory, Path reference) throws Exception {
        Set<Path> files = new LinkedHashSet<>();
        try (var stream = Files.list(directory)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> reference == null
                            || !path.toAbsolutePath().normalize().equals(reference.toAbsolutePath().normalize()))
                    .forEach(files::add);
        }
        List<Path> ab1 = filter(files, ".ab1");
        if (!ab1.isEmpty()) return ab1;
        List<Path> fastq = filter(files, ".fastq", ".fq");
        if (!fastq.isEmpty()) return fastq;
        return filter(files, ".fa", ".fasta", ".seq");
    }

    private static List<Path> filter(Set<Path> files, String... extensions) {
        List<Path> result = new ArrayList<>();
        for (Path path : files) {
            String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
            for (String extension : extensions) {
                if (name.endsWith(extension)) {
                    result.add(path);
                    break;
                }
            }
        }
        result.sort(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)));
        return result;
    }

    private static List<ReadRecord> loadReads(
            Path file,
            int trimQuality,
            int trimWindow,
            int minReadLength
    ) throws Exception {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        List<ReadRecord> result = new ArrayList<>();
        if (name.endsWith(".ab1")) {
            Ab1Reader.Trace trace = Ab1Reader.read(file);
            ReadRecord read = trim(trace.name(), trace.sequence(), trace.qualities(),
                    trimQuality, trimWindow, minReadLength);
            if (read != null) result.add(read);
        } else if (name.endsWith(".fastq") || name.endsWith(".fq")) {
            try (FastqReader reader = new FastqReader(file)) {
                FastqRecord record;
                while ((record = reader.next()) != null) {
                    ReadRecord read = trim(record.id(), record.sequence(), phred(record.quality()),
                            trimQuality, trimWindow, minReadLength);
                    if (read != null) result.add(read);
                }
            }
        } else if (name.endsWith(".seq")) {
            String sequence = Files.readString(file, StandardCharsets.UTF_8)
                    .replaceAll("\\s+", "");
            ReadRecord read = trim(name.substring(0, name.length() - 4), sequence, null,
                    trimQuality, trimWindow, minReadLength);
            if (read != null) result.add(read);
        } else {
            try (FastaReader reader = new FastaReader(file)) {
                FastaRecord record;
                while ((record = reader.next()) != null) {
                    ReadRecord read = trim(record.id(), record.sequence(), null,
                            trimQuality, trimWindow, minReadLength);
                    if (read != null) result.add(read);
                }
            }
        }
        return result;
    }

    private static ReadRecord trim(
            String name,
            String sequence,
            int[] qualities,
            int trimQuality,
            int window,
            int minReadLength
    ) {
        if (sequence == null) return null;
        String upper = sequence.toUpperCase(Locale.ROOT).replaceAll("[^ACGTN]", "N");
        int[] scores = qualities == null ? null : qualities;
        if (scores != null && scores.length != upper.length()) {
            scores = null;
        }
        int start = 0;
        int end = upper.length();
        if (scores != null) {
            while (start + window <= end && mean(scores, start, start + window) < trimQuality) start++;
            while (end - window >= start && mean(scores, end - window, end) < trimQuality) end--;
        }
        if (end - start < minReadLength) return null;
        String trimmed = upper.substring(start, end);
        int[] trimmedQualities = scores == null ? null : java.util.Arrays.copyOfRange(scores, start, end);
        return new ReadRecord(name, trimmed, trimmedQualities, start, end, upper.length());
    }

    private static int[] phred(String quality) {
        if (quality == null) return null;
        int[] values = new int[quality.length()];
        for (int index = 0; index < quality.length(); index++) {
            values[index] = Math.max(0, quality.charAt(index) - 33);
        }
        return values;
    }

    private static double decimalValue(ToolRequest request, String id) {
        Object value = request.value(id);
        if (value == null) return 0;
        return value instanceof Number number
                ? number.doubleValue() : Double.parseDouble(String.valueOf(value).trim());
    }

    private static double[] toDouble(int[] values) {
        if (values == null) return null;
        double[] result = new double[values.length];
        for (int index = 0; index < values.length; index++) result[index] = values[index];
        return result;
    }

    private static double mean(int[] values, int start, int end) {
        double sum = 0;
        for (int index = start; index < end; index++) sum += values[index];
        return (end - start) == 0 ? 0 : sum / (end - start);
    }

    private static FastaRecord readReference(Path path) throws Exception {
        try (FastaReader reader = new FastaReader(path)) {
            FastaRecord record = reader.next();
            if (record == null || record.sequence().isBlank()) {
                throw new OpenToolException("SANGER_REFERENCE_EMPTY", "Reference FASTA is empty.",
                        "Provide a non-empty reference FASTA.");
            }
            return new FastaRecord(record.id(), record.header(),
                    record.sequence().toUpperCase(Locale.ROOT));
        }
    }

    private static Assembly assemble(
            List<ReadRecord> reads,
            int minOverlap,
            double minIdentity,
            double threshold
    ) {
        List<ReadRecord> ordered = new ArrayList<>(reads);
        ordered.sort(Comparator.comparingInt((ReadRecord read) -> read.sequence().length()).reversed());
        List<Contig> contigs = new ArrayList<>();
        for (ReadRecord read : ordered) {
            Contig bestContig = null;
            Overlap best = null;
            for (Contig contig : contigs) {
                Overlap overlap = bestOverlap(contig.sequence(), read.sequence(), minOverlap, minIdentity);
                if (overlap != null && (best == null || overlap.betterThan(best))) {
                    best = overlap;
                    bestContig = contig;
                }
            }
            if (bestContig == null) {
                contigs.add(Contig.from(read, threshold));
            } else {
                merge(bestContig, read, best, threshold);
            }
        }
        return new Assembly(contigs);
    }

    private static Overlap bestOverlap(
            String a,
            String b,
            int minOverlap,
            double minIdentity
    ) {
        Overlap best = null;
        for (int orientation = 0; orientation < 2; orientation++) {
            String query = orientation == 0 ? b : reverseComplement(b);
            int startOffset = -query.length() + minOverlap;
            int endOffset = a.length() - minOverlap;
            for (int offset = startOffset; offset <= endOffset; offset++) {
                int start = Math.max(0, offset);
                int end = Math.min(a.length(), offset + query.length());
                int length = end - start;
                if (length < minOverlap) continue;
                int matches = 0;
                for (int index = start; index < end; index++) {
                    if (a.charAt(index) == query.charAt(index - offset)) matches++;
                }
                double identity = matches / (double) length;
                if (identity < minIdentity) continue;
                Overlap candidate = new Overlap(offset, length, identity, orientation == 1);
                if (best == null || candidate.betterThan(best)) best = candidate;
            }
        }
        return best;
    }

    private static void merge(Contig contig, ReadRecord read, Overlap overlap, double threshold) {
        String query = overlap.reverse() ? reverseComplement(read.sequence()) : read.sequence();
        int[] queryQualities = overlap.reverse()
                ? reversed(read.qualities()) : read.qualities();
        double[] queryQuality = toDouble(queryQualities);
        int origin = Math.min(0, overlap.offset());
        int length = Math.max(contig.sequence().length(), overlap.offset() + query.length()) - origin;
        double[][] counts = new double[length][5];
        int[] coverage = new int[length];
        double[] quality = new double[length];
        addCounts(counts, coverage, quality, contig.sequence(), contig.qualities(), -origin);
        addCounts(counts, coverage, quality, query, queryQuality, overlap.offset() - origin);
        StringBuilder sequence = new StringBuilder(length);
        boolean[] low = new boolean[length];
        for (int index = 0; index < length; index++) {
            int bestBase = bestBase(counts[index]);
            double total = 0;
            for (double value : counts[index]) total += value;
            sequence.append("ACGTN".charAt(bestBase));
            low[index] = coverage[index] > 1 && total > 0
                    && counts[index][bestBase] / total < threshold;
        }
        contig.replace(sequence.toString(), coverage, quality, low, -origin);
    }

    private static void addCounts(
            double[][] counts,
            int[] coverage,
            double[] quality,
            String sequence,
            double[] qualities,
            int offset
    ) {
        for (int index = 0; index < sequence.length(); index++) {
            int target = index + offset;
            if (target < 0 || target >= counts.length) continue;
            int base = "ACGTN".indexOf(sequence.charAt(index));
            if (base < 0) base = 4;
            double q = qualities == null ? 20 : qualities[index];
            double weight = Math.max(1, q);
            counts[target][base] += weight;
            coverage[target]++;
            quality[target] += q;
        }
    }

    private static int bestBase(double[] counts) {
        int best = 0;
        for (int index = 1; index < counts.length; index++) {
            if (counts[index] > counts[best]) best = index;
        }
        return best;
    }

    private static int[] reversed(int[] values) {
        if (values == null) return null;
        int[] result = new int[values.length];
        for (int index = 0; index < values.length; index++) {
            result[index] = values[values.length - 1 - index];
        }
        return result;
    }

    static String reverseComplement(String sequence) {
        StringBuilder result = new StringBuilder(sequence.length());
        for (int index = sequence.length() - 1; index >= 0; index--) {
            result.append(switch (sequence.charAt(index)) {
                case 'A' -> 'T';
                case 'C' -> 'G';
                case 'G' -> 'C';
                case 'T' -> 'A';
                default -> 'N';
            });
        }
        return result.toString();
    }

    private static void writeFasta(Path path, List<Contig> contigs) throws Exception {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < contigs.size(); index++) {
            Contig contig = contigs.get(index);
            text.append(">contig_").append(index + 1)
                    .append(" length=").append(contig.sequence().length())
                    .append(" reads=").append(contig.reads()).append('\n');
            for (int offset = 0; offset < contig.sequence().length(); offset += 80) {
                text.append(contig.sequence(), offset,
                        Math.min(contig.sequence().length(), offset + 80)).append('\n');
            }
        }
        Files.writeString(path, text.toString(), StandardCharsets.UTF_8);
    }

    private static void writeReadTable(Path path, List<ReadRecord> reads, ReferenceAnchor anchor) throws Exception {
        StringBuilder text = new StringBuilder();
        text.append("read\toriginal_length\ttrim_start\ttrim_end\ttrimmed_length"
                + "\tmean_quality\treference_start\torientation\tidentity\n");
        for (ReadRecord read : reads) {
            Placement placement = anchor == null ? null : anchor.byName().get(read.name());
            text.append(read.name()).append('\t')
                    .append(read.originalLength()).append('\t')
                    .append(read.trimStart()).append('\t')
                    .append(read.trimEnd()).append('\t')
                    .append(read.sequence().length()).append('\t')
                    .append(format(meanQuality(read.qualities()))).append('\t')
                    .append(placement == null ? "" : Integer.toString(placement.start())).append('\t')
                    .append(placement == null ? "" : (placement.reverse() ? "reverse" : "forward")).append('\t')
                    .append(placement == null ? "" : format(placement.identity())).append('\n');
        }
        Files.writeString(path, text.toString(), StandardCharsets.UTF_8);
    }

    private static void writeConsensus(Path path, FastaRecord reference, Consensus consensus) throws Exception {
        StringBuilder text = new StringBuilder();
        text.append('>').append(reference.id()).append("_consensus\n");
        for (int offset = 0; offset < consensus.sequence().length(); offset += 80) {
            text.append(consensus.sequence(), offset,
                    Math.min(consensus.sequence().length(), offset + 80)).append('\n');
        }
        Files.writeString(path, text.toString(), StandardCharsets.UTF_8);
    }

    private static void writeVariants(Path path, FastaRecord reference, Consensus consensus) throws Exception {
        StringBuilder text = new StringBuilder();
        text.append("position\treference\tconsensus\tcoverage\tmean_quality\tstate\n");
        for (int index = 0; index < consensus.sequence().length(); index++) {
            if (!consensus.covered()[index]) continue;
            char base = consensus.sequence().charAt(index);
            char referenceBase = reference.sequence().toUpperCase(Locale.ROOT).charAt(index);
            if (base == referenceBase) continue;
            text.append(index + 1).append('\t').append(referenceBase).append('\t').append(base)
                    .append('\t').append(consensus.coverage()[index]).append('\t')
                    .append(format(consensus.quality()[index])).append('\t')
                    .append(consensus.low()[index] ? "LOW_CONFIDENCE" : "READ_CONSENSUS")
                    .append('\n');
        }
        Files.writeString(path, text.toString(), StandardCharsets.UTF_8);
    }

    private static void writeProvenance(Path path, FastaRecord reference, Consensus consensus) throws Exception {
        StringBuilder text = new StringBuilder();
        String name = reference.id();
        int start = 0;
        String state = state(consensus, 0);
        for (int index = 1; index <= consensus.sequence().length(); index++) {
            String next = index == consensus.sequence().length() ? null : state(consensus, index);
            if (next == null || !next.equals(state)) {
                text.append(name).append('\t').append(start).append('\t').append(index)
                        .append('\t').append(state).append('\n');
                start = index;
                state = next;
            }
        }
        Files.writeString(path, text.toString(), StandardCharsets.UTF_8);
    }

    private static String state(Consensus consensus, int index) {
        if (!consensus.covered()[index]) return "REFERENCE_FILL";
        return consensus.low()[index] ? "LOW_CONFIDENCE" : "READ_CONSENSUS";
    }

    private static String format(double value) {
        return Double.isNaN(value) ? "" : "%.2f".formatted(value);
    }

    private static double meanQuality(int[] quality) {
        if (quality == null || quality.length == 0) return Double.NaN;
        double sum = 0;
        for (int value : quality) sum += value;
        return sum / quality.length;
    }

    private record Assembly(List<Contig> contigs) {
    }

    private record ReadRecord(
            String name,
            String sequence,
            int[] qualities,
            int trimStart,
            int trimEnd,
            int originalLength
    ) {
    }

    private record Overlap(int offset, int length, double identity, boolean reverse) {
        boolean betterThan(Overlap other) {
            if (Double.compare(identity, other.identity) != 0) return identity > other.identity;
            return length > other.length;
        }
    }

    private static final class Contig {
        private String sequence;
        private int[] coverage;
        private double[] qualities;
        private boolean[] low;
        private int reads;
        private final Map<String, Integer> origins = new LinkedHashMap<>();

        static Contig from(ReadRecord read, double threshold) {
            Contig contig = new Contig();
            contig.sequence = read.sequence();
            contig.coverage = new int[read.sequence().length()];
            java.util.Arrays.fill(contig.coverage, 1);
            contig.qualities = read.qualities() == null
                    ? new double[read.sequence().length()] : toDouble(read.qualities());
            contig.low = new boolean[read.sequence().length()];
            contig.reads = 1;
            contig.origins.put(read.name(), 0);
            return contig;
        }

        private static double[] toDouble(double[] values) {
        return values == null ? null : values.clone();
    }

    private static double[] toDouble(int[] values) {
            double[] result = new double[values.length];
            for (int index = 0; index < values.length; index++) result[index] = values[index];
            return result;
        }

        String sequence() { return sequence; }
        int[] coverage() { return coverage; }
        double[] qualities() { return qualities; }
        int reads() { return reads; }

        void replace(String sequence, int[] coverage, double[] qualities, boolean[] low, int origin) {
            this.sequence = sequence;
            this.coverage = coverage;
            this.qualities = qualities;
            this.low = low;
            this.reads++;
            if (origin != 0) {
                origins.replaceAll((name, value) -> value + origin);
            }
        }
    }

    private record Placement(String name, int start, boolean reverse, double identity) {
    }

    private static final class ReferenceAnchor {
        private final String reference;
        private final List<Placement> placements;
        private final Map<String, Placement> byName;
        private final Map<String, ReadRecord> reads;

        private ReferenceAnchor(
                String reference,
                List<Placement> placements,
                Map<String, ReadRecord> reads
        ) {
            this.reference = reference;
            this.placements = placements;
            this.byName = new LinkedHashMap<>();
            for (Placement placement : placements) byName.put(placement.name(), placement);
            this.reads = reads;
        }

        static ReferenceAnchor place(String reference, List<ReadRecord> reads, int minOverlap) {
            Map<String, List<Integer>> index = new LinkedHashMap<>();
            int k = 15;
            for (int position = 0; position + k <= reference.length(); position++) {
                String kmer = reference.substring(position, position + k);
                if (kmer.indexOf('N') >= 0) continue;
                index.computeIfAbsent(kmer, ignored -> new ArrayList<>()).add(position);
            }
            List<Placement> placements = new ArrayList<>();
            Map<String, ReadRecord> byName = new LinkedHashMap<>();
            for (ReadRecord read : reads) {
                Placement best = bestPlacement(reference, index, read, minOverlap);
                if (best != null) {
                    placements.add(best);
                    byName.put(read.name(), read);
                }
            }
            return new ReferenceAnchor(reference, placements, byName);
        }

        private static Placement bestPlacement(
                String reference,
                Map<String, List<Integer>> index,
                ReadRecord read,
                int minOverlap
        ) {
            Placement best = null;
            for (int orientation = 0; orientation < 2; orientation++) {
                String sequence = orientation == 0 ? read.sequence() : reverseComplement(read.sequence());
                for (int position = 0; position + 15 <= sequence.length(); position += 5) {
                    List<Integer> hits = index.get(sequence.substring(position, position + 15));
                    if (hits == null) continue;
                    for (int hit : hits) {
                        int start = hit - position;
                        int overlapStart = Math.max(0, start);
                        int overlapEnd = Math.min(reference.length(), start + sequence.length());
                        int length = overlapEnd - overlapStart;
                        if (length < minOverlap) continue;
                        int matches = 0;
                        for (int ref = overlapStart; ref < overlapEnd; ref++) {
                            if (reference.charAt(ref) == sequence.charAt(ref - start)) matches++;
                        }
                        double identity = matches / (double) length;
                        if (identity < 0.7) continue;
                        Placement candidate = new Placement(read.name(), start,
                                orientation == 1, identity);
                        if (best == null || candidate.identity() > best.identity()) best = candidate;
                    }
                }
            }
            return best;
        }

        List<Placement> placements() { return placements; }
        Map<String, Placement> byName() { return byName; }

        int coveredBases() {
            return consensus(reference, 0.6).coveredCount();
        }

        Consensus consensus(double threshold) {
            return consensus(reference, threshold);
        }

        private Consensus consensus(String reference, double threshold) {
            int length = reference.length();
            double[][] counts = new double[length][5];
            int[] coverage = new int[length];
            double[] quality = new double[length];
            for (Placement placement : placements) {
                ReadRecord read = reads.get(placement.name());
                if (read == null) continue;
                String sequence = placement.reverse()
                        ? reverseComplement(read.sequence()) : read.sequence();
                int[] baseQuality = placement.reverse()
                        ? reversed(read.qualities()) : read.qualities();
                for (int index = 0; index < sequence.length(); index++) {
                    int target = placement.start() + index;
                    if (target < 0 || target >= length) continue;
                    int base = "ACGTN".indexOf(sequence.charAt(index));
                    if (base < 0) base = 4;
                    int q = baseQuality == null ? 20 : baseQuality[index];
                    counts[target][base] += Math.max(1, q);
                    coverage[target]++;
                    quality[target] += q;
                }
            }
            StringBuilder sequence = new StringBuilder(length);
            boolean[] covered = new boolean[length];
            boolean[] low = new boolean[length];
            for (int index = 0; index < length; index++) {
                int best = 0;
                double total = 0;
                for (int base = 0; base < 5; base++) {
                    total += counts[index][base];
                    if (counts[index][base] > counts[index][best]) best = base;
                }
                covered[index] = coverage[index] > 0;
                if (covered[index]) {
                    char base = "ACGTN".charAt(best);
                    sequence.append(base);
                    low[index] = counts[index][best] / Math.max(1, total) < threshold
                            || quality[index] / Math.max(1, coverage[index]) < 20;
                } else {
                    sequence.append(reference.charAt(index));
                }
                if (covered[index] && coverage[index] > 1) {
                    quality[index] /= coverage[index];
                }
            }
            return new Consensus(sequence.toString(), covered, coverage, quality, low);
        }
    }

    private record Consensus(
            String sequence,
            boolean[] covered,
            int[] coverage,
            double[] quality,
            boolean[] low
    ) {
        int coveredCount() {
            int count = 0;
            for (boolean value : covered) if (value) count++;
            return count;
        }
    }
}
