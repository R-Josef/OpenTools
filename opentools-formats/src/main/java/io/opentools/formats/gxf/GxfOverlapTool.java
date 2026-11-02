package io.opentools.formats.gxf;

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
import io.opentools.core.util.SqliteSpillSupport;
import io.opentools.formats.FormatToolSupport;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.TreeSet;

public final class GxfOverlapTool extends AbstractOpenTool {
    private static final String REPORT_HEADER =
            "seq_id\ttype_a\tid_a\tstart_a\tend_a\ttype_b\tid_b\tstart_b\tend_b\toverlap_bases\n";
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "gxf.overlap",
            "Find GFF3/GTF Overlaps",
            "Report overlapping annotation intervals with a bounded in-memory sweep or disk-backed interval index.",
            "Genome annotation",
            List.of("gff overlap", "gtf overlap", "interval overlap", "注释重叠"),
            List.of("GFF3", "GTF", "GFF3.gz", "GTF.gz"),
            List.of("TSV"),
            "1.4.0",
            List.of(
                    ToolParameter.requiredFile("input", "Input annotation", "GFF3 or GTF."),
                    ToolParameter.integer("minOverlap", "Minimum overlap",
                            "Minimum overlapping bases.", 1, 1, Integer.MAX_VALUE),
                    ToolParameter.bool("sameTypeOnly", "Same type only",
                            "Only compare records with the same feature type.", false),
                    new ToolParameter(
                            "overlapMode",
                            "Overlap mode",
                            "AUTO spills large inputs or when maxRecords is exceeded; DISK always uses a temporary interval table.",
                            ParameterType.ENUM,
                            false,
                            "AUTO",
                            List.of("AUTO", "IN_MEMORY", "DISK"),
                            null,
                            null,
                            false,
                            null
                    ),
                    ToolParameter.integer("maxRecords", "Maximum in-memory records",
                            "Safety bound for annotation records held by the in-memory overlap sweep.",
                            2_000_000, 1, 100_000_000),
                    ToolParameter.integer("maxActiveRecords", "Maximum active disk records",
                            "Safety bound for concurrently overlapping records retained by the disk sweep.",
                            2_000_000, 1, 100_000_000),
                    ToolParameter.integer("maxTempMegabytes", "Maximum temporary SQLite size",
                            "Disk safety limit for DISK/AUTO overlap mode; 0 means no explicit limit.",
                            8192, 0, 1_048_576),
                    ToolParameter.integer("maxPairs", "Maximum pairs",
                            "Cap reported overlap pairs.", 100_000, 1, 1_000_000),
                    ToolParameter.optionalOutputFile("output", "Overlap report",
                            "Leave blank to save in the run directory."),
                    ToolParameter.bool("overwrite", "Overwrite output",
                            "Allow replacing an existing report.", false)
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
        Path output = FormatToolSupport.outputPath(request, context, "overlaps.tsv");
        if (shouldUseDisk(request)) {
            return diskResult(request, output, context);
        }
        try {
            return memoryResult(request, output, context);
        } catch (OpenToolException ex) {
            if (!"AUTO".equals(request.string("overlapMode"))
                    || !"ANNOTATION_MEMORY_BOUND".equals(ex.code())) {
                throw ex;
            }
            context.log("In-memory overlap bound reached; retrying with disk interval index");
            return diskResult(request, output, context);
        }
    }

    private static boolean shouldUseDisk(ToolRequest request) throws Exception {
        String mode = request.string("overlapMode");
        if ("DISK".equals(mode)) {
            return true;
        }
        if ("IN_MEMORY".equals(mode)) {
            return false;
        }
        return Files.size(request.path("input")) >= 8L * 1024 * 1024;
    }

    private static ToolResult memoryResult(
            ToolRequest request,
            Path output,
            ToolExecutionContext context
    ) throws Exception {
        Map<String, List<GxfRecord>> bySequence = new LinkedHashMap<>();
        long records = 0;
        try (GxfReader reader = new GxfReader(request.path("input"))) {
            GxfRecord record;
            while ((record = reader.next()) != null) {
                context.cancellationToken().throwIfCancelled();
                if (records >= request.integer("maxRecords")) {
                    throw new OpenToolException(
                            "ANNOTATION_MEMORY_BOUND",
                            "Annotation records exceed maxRecords.",
                            "Raise the bound, use overlapMode=DISK, or partition the annotation."
                    );
                }
                bySequence.computeIfAbsent(record.seqId(), ignored -> new ArrayList<>()).add(record);
                records++;
            }
        }
        requireRecords(records);
        long[] totals = new long[2];
        FormatToolSupport.writeAtomically(output, writer -> {
            writer.write(REPORT_HEADER);
            long pairs = 0;
            int maxPairs = request.integer("maxPairs");
            for (List<GxfRecord> recordsForSequence : bySequence.values()) {
                recordsForSequence.sort(Comparator.comparingLong(GxfRecord::start)
                        .thenComparingLong(GxfRecord::end));
                List<GxfRecord> active = new ArrayList<>();
                for (GxfRecord current : recordsForSequence) {
                    active.removeIf(previous -> previous.end() < current.start());
                    for (GxfRecord previous : active) {
                        if (request.bool("sameTypeOnly")
                                && !previous.type().equals(current.type())) {
                            continue;
                        }
                        long overlap = overlap(previous, current);
                        if (overlap < request.integer("minOverlap")) {
                            continue;
                        }
                        if (pairs >= maxPairs) {
                            totals[0] = pairs;
                            totals[1] = 1;
                            return;
                        }
                        writePair(
                                writer,
                                current.seqId(),
                                previous.type(),
                                AnnotationTaskSupport.firstId(previous),
                                previous.start(),
                                previous.end(),
                                current.type(),
                                AnnotationTaskSupport.firstId(current),
                                current.start(),
                                current.end(),
                                overlap
                        );
                        pairs++;
                    }
                    active.add(current);
                }
            }
            totals[0] = pairs;
        });
        return result(request, output, context, new OverlapStats(
                records, totals[0], totals[1] != 0, "IN_MEMORY"));
    }

    private static ToolResult diskResult(
            ToolRequest request,
            Path output,
            ToolExecutionContext context
    ) throws Exception {
        Path tempDirectory = context.runDirectory().resolve("tmp");
        Path database = SqliteSpillSupport.createTemporaryDatabase(
                tempDirectory, "gxf-overlap-");
        long maxTempBytes = SqliteSpillSupport.megabytes(
                request.integer("maxTempMegabytes"));
        try {
            try {
                long records = populateIntervalDatabase(
                        request.path("input"), database, maxTempBytes, context);
                requireRecords(records);
                OverlapStats stats = queryIntervalDatabase(
                        database, request, output, records, context);
                return result(request, output, context, stats);
            } catch (SQLException ex) {
                throw SqliteSpillSupport.translateSqlException(
                        ex, "Annotation overlap");
            }
        } finally {
            SqliteSpillSupport.deleteDatabaseQuietly(database);
        }
    }

    private static long populateIntervalDatabase(
            Path input,
            Path database,
            long maxTempBytes,
            ToolExecutionContext context
    ) throws Exception {
        long records = 0;
        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + database.toAbsolutePath());
             Statement setup = connection.createStatement()) {
            setup.execute("PRAGMA journal_mode=OFF");
            setup.execute("PRAGMA synchronous=OFF");
            setup.execute("PRAGMA temp_store=FILE");
            setup.execute("""
                    CREATE TABLE features (
                      seq_id TEXT NOT NULL,
                      record_order INTEGER NOT NULL,
                      type TEXT NOT NULL,
                      feature_id TEXT NOT NULL,
                      start INTEGER NOT NULL,
                      end INTEGER NOT NULL,
                      PRIMARY KEY(seq_id,record_order)
                    ) WITHOUT ROWID
                    """);
            setup.execute("CREATE INDEX features_interval "
                    + "ON features(seq_id,start,end,record_order)");
            connection.setAutoCommit(false);
            try (GxfReader reader = new GxfReader(input);
                 PreparedStatement insert = connection.prepareStatement(
                         "INSERT INTO features(seq_id,record_order,type,feature_id,start,end) "
                                 + "VALUES(?,?,?,?,?,?)")) {
                GxfRecord record;
                while ((record = reader.next()) != null) {
                    context.cancellationToken().throwIfCancelled();
                    insert.setString(1, record.seqId());
                    insert.setLong(2, records);
                    insert.setString(3, record.type());
                    insert.setString(4, nullToEmpty(AnnotationTaskSupport.firstId(record)));
                    insert.setLong(5, record.start());
                    insert.setLong(6, record.end());
                    insert.addBatch();
                    records++;
                    if (records % 10_000 == 0) {
                        insert.executeBatch();
                        connection.commit();
                        SqliteSpillSupport.enforceLimit(database, maxTempBytes);
                    }
                }
                insert.executeBatch();
                connection.commit();
                SqliteSpillSupport.enforceLimit(database, maxTempBytes);
            }
        }
        return records;
    }

    private static OverlapStats queryIntervalDatabase(
            Path database,
            ToolRequest request,
            Path output,
            long records,
            ToolExecutionContext context
    ) throws Exception {
        long[] totals = new long[2];
        Comparator<DiskRecord> byEnd = Comparator.comparingLong(DiskRecord::end)
                .thenComparingLong(DiskRecord::recordOrder);
        Comparator<DiskRecord> byInterval = Comparator.comparingLong(DiskRecord::start)
                .thenComparingLong(DiskRecord::end)
                .thenComparingLong(DiskRecord::recordOrder);
        PriorityQueue<DiskRecord> expiring = new PriorityQueue<>(byEnd);
        TreeSet<DiskRecord> active = new TreeSet<>(byInterval);
        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + database.toAbsolutePath());
             PreparedStatement current = connection.prepareStatement("""
                     SELECT seq_id,record_order,type,feature_id,start,end
                     FROM features
                     ORDER BY seq_id,start,end,record_order
                     """)) {
            FormatToolSupport.writeAtomically(output, writer -> {
                writer.write(REPORT_HEADER);
                long pairs = 0;
                String activeSequence = null;
                try (ResultSet currentRows = current.executeQuery()) {
                    while (currentRows.next()) {
                        context.cancellationToken().throwIfCancelled();
                        String seqId = currentRows.getString(1);
                        if (!seqId.equals(activeSequence)) {
                            activeSequence = seqId;
                            active.clear();
                            expiring.clear();
                        }
                        DiskRecord record = new DiskRecord(
                                seqId,
                                currentRows.getLong(2),
                                currentRows.getString(3),
                                currentRows.getString(4),
                                currentRows.getLong(5),
                                currentRows.getLong(6)
                        );
                        while (!expiring.isEmpty()
                                && expiring.peek().end() < record.start()) {
                            DiskRecord expired = expiring.remove();
                            active.remove(expired);
                        }
                        for (DiskRecord previous : active) {
                            if (request.bool("sameTypeOnly")
                                    && !previous.type().equals(record.type())) {
                                continue;
                            }
                            long overlap = Math.min(previous.end(), record.end())
                                    - Math.max(previous.start(), record.start()) + 1;
                            if (overlap < request.integer("minOverlap")) {
                                continue;
                            }
                            if (pairs >= request.integer("maxPairs")) {
                                totals[0] = pairs;
                                totals[1] = 1;
                                return;
                            }
                            writePair(
                                    writer,
                                    seqId,
                                    previous.type(),
                                    previous.featureId(),
                                    previous.start(),
                                    previous.end(),
                                    record.type(),
                                    record.featureId(),
                                    record.start(),
                                    record.end(),
                                    overlap
                            );
                            pairs++;
                        }
                        if (active.size() >= request.integer("maxActiveRecords")) {
                            throw new OpenToolException(
                                    "ANNOTATION_ACTIVE_BOUND",
                                    "The disk overlap sweep reached maxActiveRecords.",
                                    "Partition the annotation or raise maxActiveRecords."
                            );
                        }
                        active.add(record);
                        expiring.add(record);
                    }
                }
                totals[0] = pairs;
            });
        }
        return new OverlapStats(records, totals[0], totals[1] != 0, "DISK");
    }

    private static ToolResult result(
            ToolRequest request,
            Path output,
            ToolExecutionContext context,
            OverlapStats stats
    ) {
        context.progress(1.0, "Annotation overlap scan completed");
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("records", stats.records());
        metrics.put("overlapPairs", stats.pairs());
        metrics.put("truncated", stats.truncated());
        metrics.put("overlapMode", stats.mode());
        metrics.put("memoryBound", request.integer("maxRecords"));
        metrics.put("activeBound", request.integer("maxActiveRecords"));
        metrics.put("spillLimitBytes", SqliteSpillSupport.megabytes(
                request.integer("maxTempMegabytes")));
        metrics.put("annotationMemory",
                "IN_MEMORY".equals(stats.mode()) ? "bounded-records" : "disk-interval-index");
        return new ToolResult(
                "Found " + stats.pairs() + " overlapping record pairs", List.of(stats.pairs()),
                stats.truncated() ? "Pair output reached maxPairs." : "All pairs were considered.",
                List.of(),
                new ResultTable(List.of("Metric", "Value"), List.of(
                        List.of("Annotation records", Long.toString(stats.records())),
                        List.of("Overlap pairs", Long.toString(stats.pairs())),
                        List.of("Mode", stats.mode()),
                        List.of("Truncated", Boolean.toString(stats.truncated()))
                )),
                List.of(new OutputArtifact(output, "Overlap report", "text/tab-separated-values")),
                metrics
        );
    }

    private static void writePair(
            BufferedWriter writer,
            String seqId,
            String typeA,
            String idA,
            long startA,
            long endA,
            String typeB,
            String idB,
            long startB,
            long endB,
            long overlap
    ) throws Exception {
        writer.write(seqId);
        writer.write('\t');
        writer.write(nullToEmpty(typeA));
        writer.write('\t');
        writer.write(nullToEmpty(idA));
        writer.write('\t');
        writer.write(Long.toString(startA));
        writer.write('\t');
        writer.write(Long.toString(endA));
        writer.write('\t');
        writer.write(nullToEmpty(typeB));
        writer.write('\t');
        writer.write(nullToEmpty(idB));
        writer.write('\t');
        writer.write(Long.toString(startB));
        writer.write('\t');
        writer.write(Long.toString(endB));
        writer.write('\t');
        writer.write(Long.toString(overlap));
        writer.newLine();
    }

    private static long overlap(GxfRecord previous, GxfRecord current) {
        return Math.min(previous.end(), current.end())
                - Math.max(previous.start(), current.start()) + 1;
    }

    private static void requireRecords(long records) {
        if (records == 0) {
            throw new OpenToolException(
                    "EMPTY_ANNOTATION", "No annotation records were found.", "Check the input."
            );
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record OverlapStats(
            long records,
            long pairs,
            boolean truncated,
            String mode
    ) {
    }

    private record DiskRecord(
            String sequenceId,
            long recordOrder,
            String type,
            String featureId,
            long start,
            long end
    ) {
    }
}
