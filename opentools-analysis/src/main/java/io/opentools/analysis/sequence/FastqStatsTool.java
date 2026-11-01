package io.opentools.analysis.sequence;

import io.opentools.api.tool.AbstractOpenTool;
import io.opentools.api.tool.OpenToolException;
import io.opentools.api.tool.OutputArtifact;
import io.opentools.api.tool.ResultTable;
import io.opentools.api.tool.TaskContext;
import io.opentools.api.tool.TaskResult;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ParameterType;
import io.opentools.api.tool.ToolParameter;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ValidationResult;
import io.opentools.core.util.OrderedParallelProcessor;
import io.opentools.formats.FormatTaskSupport;
import io.opentools.formats.fastq.FastqReader;
import io.opentools.formats.fastq.FastqRecord;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class FastqStatsTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "fastq.stats",
            "FASTQ Statistics",
            "Stream FASTQ length, quality, base composition, and Q20/Q30 statistics.",
            "Sequence",
            List.of("FASTQ stats", "quality statistics", "FASTQ 统计", "质量统计"),
            List.of("FASTQ", "FASTQ.gz"),
            List.of("TSV"),
            "1.2.0",
            List.of(
                    ToolParameter.requiredFile("input", "Input FASTQ", "FASTQ, optionally gzip-compressed."),
                    ToolParameter.integer("threads", "Threads",
                            "0 uses AUTO for large files; 1 disables parallel read processing.",
                            0, 0, 64),
                    new ToolParameter(
                            "qualityEncoding", "Quality encoding",
                            "AUTO detects Phred+33 or Phred+64 from observed ASCII values; choose explicitly for ambiguous data.",
                            ParameterType.ENUM, false, "AUTO",
                            List.of("AUTO", "PHRED_33", "PHRED_64", "SOLEXA_64"),
                            null, null, false, null),
                    ToolParameter.optionalOutputFile("output", "Output TSV", "Leave blank to save in the run directory."),
                    ToolParameter.bool("overwrite", "Overwrite output", "Allow replacing an existing output.", false)
            )
    );

    @Override public ToolDescriptor descriptor() { return DESCRIPTOR; }
    @Override public ValidationResult validate(ToolRequest request) {
        return validateParameters(request).merge(FormatTaskSupport.validateOutput(request, request.path("input")));
    }

    @Override public TaskResult execute(ToolRequest request, TaskContext context) throws Exception {
        FastqQualityEncoding selectedEncoding = FastqQualityEncoding.explicit(
                request.string("qualityEncoding"));
        int threads = OrderedParallelProcessor.resolveThreads(
                request.integer("threads"), Files.size(request.path("input")), 32L * 1024 * 1024);
        QualitySummary summary = new QualitySummary();
        try (FastqReader reader = new FastqReader(request.path("input"))) {
            OrderedParallelProcessor.processChunks(
                    threads,
                    256,
                    context.cancellationToken(),
                    reader::next,
                    chunk -> {
                        QualitySummary local = new QualitySummary();
                        for (FastqRecord record : chunk) {
                            local.add(record);
                        }
                        return local;
                    },
                    summary::merge
            );
        }
        if (summary.records == 0) {
            throw new OpenToolException("EMPTY_FASTQ", "No FASTQ records were found.", "Check the input.");
        }
        FastqQualityEncoding.Resolution encoding = selectedEncoding == null
                ? FastqQualityEncoding.detect(summary.minimumQualityAscii, summary.maximumQualityAscii)
                : new FastqQualityEncoding.Resolution(
                        selectedEncoding, false, false,
                        summary.minimumQualityAscii, summary.maximumQualityAscii);
        long[] counters = new long[11];
        counters[0] = summary.records;
        counters[1] = summary.bases;
        counters[5] = summary.a;
        counters[6] = summary.c;
        counters[7] = summary.g;
        counters[8] = summary.t;
        counters[9] = summary.n;
        long minLength = summary.minLength;
        long maxLength = summary.maxLength;
        double qualitySum = 0;
        double minQuality = Double.POSITIVE_INFINITY;
        double maxQuality = Double.NEGATIVE_INFINITY;
        for (int ascii = 33; ascii <= 126; ascii++) {
            long count = summary.qualityHistogram[ascii];
            if (count == 0) {
                continue;
            }
            double quality = encoding.encoding().score((char) ascii);
            qualitySum += quality * count;
            minQuality = Math.min(minQuality, quality);
            maxQuality = Math.max(maxQuality, quality);
            if (quality >= 20) {
                counters[3] += count;
            }
            if (quality >= 30) {
                counters[4] += count;
            }
        }
        Path output = FormatTaskSupport.outputPath(request, context, "fastq-stats.tsv");
        long records = counters[0];
        long bases = counters[1];
        long minimumLength = minLength;
        long maximumLength = maxLength;
        double meanLength = bases / (double) records;
        double meanQuality = qualitySum / (double) Math.max(1, bases);
        double q20 = counters[3] * 100.0 / Math.max(1, bases);
        double q30 = counters[4] * 100.0 / Math.max(1, bases);
        if (bases == 0) {
            minQuality = 0;
            maxQuality = 0;
        }
        long canonical = counters[5] + counters[6] + counters[7] + counters[8];
        FormatTaskSupport.writeAtomically(output, writer -> {
            writer.write("metric\tvalue\n");
            String[] labels = {"records", "bases", "min_length", "max_length", "mean_length",
                    "mean_quality", "q20_percent", "q30_percent", "a_percent", "c_percent",
                    "g_percent", "t_percent", "n_percent", "quality_encoding",
                    "quality_ascii_offset"};
            double[] values = {records, bases, minimumLength, maximumLength, meanLength, meanQuality,
                    q20, q30, counters[5] * 100.0 / Math.max(1, canonical),
                    counters[6] * 100.0 / Math.max(1, canonical),
                    counters[7] * 100.0 / Math.max(1, canonical),
                    counters[8] * 100.0 / Math.max(1, canonical),
                    counters[9] * 100.0 / Math.max(1, bases),
                    Double.NaN, Double.NaN};
            for (int index = 0; index < 13; index++) {
                writer.write(labels[index] + "\t" + format(values[index]) + "\n");
            }
            writer.write(labels[13] + "\t" + encoding.encoding().name() + "\n");
            writer.write(labels[14] + "\t" + encoding.encoding().asciiOffset() + "\n");
            writer.write("quality_autodetected\t" + encoding.autodetected() + "\n");
            writer.write("quality_detection_ambiguous\t" + encoding.ambiguous() + "\n");
        });
        context.progress(1.0, "FASTQ statistics completed");
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("records", records);
        metrics.put("bases", bases);
        metrics.put("meanLength", meanLength);
        metrics.put("meanQuality", meanQuality);
        metrics.put("q20Percent", q20);
        metrics.put("q30Percent", q30);
        metrics.put("minLength", minLength);
        metrics.put("maxLength", maxLength);
        metrics.put("minQuality", minQuality);
        metrics.put("maxQuality", maxQuality);
        metrics.put("qualityEncoding", encoding.encoding().name());
        metrics.put("qualityAsciiOffset", encoding.encoding().asciiOffset());
        metrics.put("qualityAutodetected", encoding.autodetected());
        metrics.put("qualityDetectionAmbiguous", encoding.ambiguous());
        metrics.put("threads", threads);
        metrics.put("parallelRecords", threads > 1);
        return new TaskResult(
                "Summarized " + records + " FASTQ records",
                "result.fastq.stats.summary", List.of(records),
                bases + " bases; Q30 " + format(q30) + "%.",
                "result.fastq.stats.detail", List.of(bases, q30),
                new ResultTable(List.of("Metric", "Value"), List.of(
                        List.of("Records", Long.toString(records)),
                        List.of("Bases", Long.toString(bases)),
                        List.of("Mean length", format(meanLength)),
                        List.of("Mean quality", format(meanQuality)),
                        List.of("Q20 percent", format(q20)),
                        List.of("Q30 percent", format(q30))
                )),
                List.of(new OutputArtifact(output, "FASTQ statistics", "text/tab-separated-values")),
                metrics
        );
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static final class QualitySummary {
        private long records;
        private long bases;
        private long minLength = Long.MAX_VALUE;
        private long maxLength;
        private long a;
        private long c;
        private long g;
        private long t;
        private long n;
        private int minimumQualityAscii = 127;
        private int maximumQualityAscii;
        private final long[] qualityHistogram = new long[128];

        private void add(FastqRecord record) {
            records++;
            int length = record.sequence().length();
            bases += length;
            minLength = Math.min(minLength, length);
            maxLength = Math.max(maxLength, length);
            for (int index = 0; index < length; index++) {
                char base = Character.toUpperCase(record.sequence().charAt(index));
                if (base == 'A') a++;
                else if (base == 'C') c++;
                else if (base == 'G') g++;
                else if (base == 'T' || base == 'U') t++;
                else if (base == 'N') n++;
                int qualityAscii = record.quality().charAt(index);
                if (qualityAscii < 33 || qualityAscii > 126) {
                    throw new OpenToolException(
                            "FASTQ_QUALITY_INVALID",
                            "FASTQ quality characters must use printable ASCII 33-126.",
                            "Check the FASTQ quality encoding."
                    );
                }
                qualityHistogram[qualityAscii]++;
                minimumQualityAscii = Math.min(minimumQualityAscii, qualityAscii);
                maximumQualityAscii = Math.max(maximumQualityAscii, qualityAscii);
            }
        }

        private void merge(QualitySummary other) {
            records += other.records;
            bases += other.bases;
            minLength = Math.min(minLength, other.minLength);
            maxLength = Math.max(maxLength, other.maxLength);
            a += other.a;
            c += other.c;
            g += other.g;
            t += other.t;
            n += other.n;
            minimumQualityAscii = Math.min(minimumQualityAscii, other.minimumQualityAscii);
            maximumQualityAscii = Math.max(maximumQualityAscii, other.maximumQualityAscii);
            for (int ascii = 0; ascii < qualityHistogram.length; ascii++) {
                qualityHistogram[ascii] += other.qualityHistogram[ascii];
            }
        }
    }
}
