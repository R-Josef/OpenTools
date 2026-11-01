package io.opentools.formats.gxf;

import io.opentools.api.tool.CancellationToken;
import io.opentools.api.tool.OpenTool;
import io.opentools.api.tool.OpenToolException;
import io.opentools.api.tool.TaskContext;
import io.opentools.api.tool.TaskResult;
import io.opentools.api.tool.ToolParameter;
import io.opentools.core.tool.ToolRegistry;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ValidationResult;
import io.opentools.formats.BuiltInTools;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AnnotationBedToolsTest {
    @TempDir
    Path tempDir;

    @Test
    void filtersGxfAndRoundTripsBedCoordinates() throws Exception {
        Path gff = tempDir.resolve("features.gff3");
        Files.writeString(gff, """
                ##gff-version 3
                chr1\ttest\tgene\t1\t10\t.\t+\t.\tID=g1
                chr1\ttest\tCDS\t1\t1\t.\t+\t0\tID=cds1;Parent=g1
                chr1\ttest\tCDS\t3\t5\t.\t+\t0\tID=cds2;Parent=g1
                chr1\ttest\tCDS\t6\t8\t.\t-\t0\tID=cds3;Parent=g1
                """);
        Path filtered = tempDir.resolve("filtered.gff3");
        TaskResult filter = run("gxf.filter", Map.of(
                "input", gff,
                "types", "CDS",
                "strand", "+",
                "output", filtered
        ));
        assertEquals(2L, filter.metrics().get("retained"));

        Path bed = tempDir.resolve("features.bed");
        run("gxf.to-bed", Map.of("input", filtered, "output", bed));
        List<String> bedLines = Files.readAllLines(bed);
        assertTrue(bedLines.get(0).startsWith("chr1\t0\t1\t"));
        assertTrue(bedLines.get(1).startsWith("chr1\t2\t5\t"));

        Path stats = tempDir.resolve("bed-stats.tsv");
        TaskResult statistics = run("bed.stats", Map.of("input", bed, "output", stats));
        assertEquals(2L, statistics.metrics().get("records"));
        assertEquals(4L, statistics.metrics().get("totalBases"));

        Path roundTrip = tempDir.resolve("roundtrip.gff3");
        run("bed.to-gff", Map.of("input", bed, "output", roundTrip));
        String output = Files.readString(roundTrip);
        assertTrue(output.contains("\t1\t1\t"));
        assertTrue(output.contains("\t3\t5\t"));
    }

    @Test
    void findsOverlapsAndSelectsRepresentativeTranscript() throws Exception {
        Path annotation = writeTranscripts();
        Path overlaps = tempDir.resolve("overlaps.tsv");
        TaskResult overlap = run("gxf.overlap", Map.of("input", annotation, "output", overlaps));
        assertTrue((Long) overlap.metrics().get("overlapPairs") > 0);
        assertTrue(Files.readString(overlaps).contains("exon"));

        Path representative = tempDir.resolve("representative.gff3");
        TaskResult selected = run("gxf.representative", Map.of(
                "input", annotation,
                "transcriptTypes", "mRNA,transcript",
                "scoreMode", "LONGEST_TRANSCRIPT",
                "output", representative
        ));
        assertEquals(1L, selected.metrics().get("representatives"));
        String output = Files.readString(representative);
        assertTrue(output.contains("ID=tx_long"));
        assertFalse(output.contains("ID=tx_short"), output + "\nmetrics=" + selected.metrics());
        assertTrue(output.contains("Parent=tx_long"));
    }

    @Test
    void spoolsOverlapIndexAndHonorsPairAndTypeFilters() throws Exception {
        Path annotation = tempDir.resolve("spool-overlaps.gff3");
        Files.writeString(annotation, """
                ##gff-version 3
                chr1\ttest\tgene\t1\t2\t.\t+\t.\tID=g1
                chr1\ttest\tgene\t1\t2\t.\t+\t.\tID=g2
                chr1\ttest\tgene\t2\t3\t.\t+\t.\tID=g3
                chr1\ttest\texon\t1\t5\t.\t+\t.\tID=e1
                chr1\ttest\texon\t3\t5\t.\t+\t.\tID=e2
                """);

        Path diskOutput = tempDir.resolve("disk-overlaps.tsv");
        TaskResult disk = run("gxf.overlap", Map.of(
                "input", annotation,
                "overlapMode", "DISK",
                "sameTypeOnly", true,
                "minOverlap", 2,
                "maxPairs", 10,
                "output", diskOutput
        ));
        assertEquals("DISK", disk.metrics().get("overlapMode"));
        assertEquals(2L, disk.metrics().get("overlapPairs"), Files.readString(diskOutput));
        assertEquals(2L, Files.readAllLines(diskOutput).size() - 1);

        Path truncatedOutput = tempDir.resolve("truncated-overlaps.tsv");
        TaskResult truncated = run("gxf.overlap", Map.of(
                "input", annotation,
                "overlapMode", "DISK",
                "sameTypeOnly", true,
                "minOverlap", 2,
                "maxPairs", 1,
                "output", truncatedOutput
        ));
        assertEquals(1L, truncated.metrics().get("overlapPairs"));
        assertEquals(true, truncated.metrics().get("truncated"));

        Path autoOutput = tempDir.resolve("auto-overlaps.tsv");
        TaskResult automatic = run("gxf.overlap", Map.of(
                "input", annotation,
                "overlapMode", "AUTO",
                "maxRecords", 1,
                "maxPairs", 10,
                "output", autoOutput
        ));
        assertEquals("DISK", automatic.metrics().get("overlapMode"));

        Path singleBaseInput = tempDir.resolve("single-base-overlap.gff3");
        Files.writeString(singleBaseInput, """
                ##gff-version 3
                chr1\ttest\texon\t7\t7\t.\t+\t.\tID=s1
                chr1\ttest\texon\t7\t7\t.\t+\t.\tID=s2
                """);
        Path singleBaseOutput = tempDir.resolve("single-base-overlaps.tsv");
        TaskResult singleBase = run("gxf.overlap", Map.of(
                "input", singleBaseInput,
                "overlapMode", "DISK",
                "output", singleBaseOutput
        ));
        assertEquals(1L, singleBase.metrics().get("overlapPairs"));
        assertTrue(Files.readAllLines(singleBaseOutput).getLast().endsWith("\t1"));

        Path temp = tempDir.resolve("out/tmp");
        if (Files.isDirectory(temp)) {
            try (var files = Files.list(temp)) {
                assertFalse(files.anyMatch(path -> path.getFileName().toString().endsWith(".sqlite")));
            }
        }
    }

    @Test
    void diskOverlapEnforcesTemporaryDatabaseLimitWithoutPublishingOutput() throws Exception {
        Path annotation = tempDir.resolve("large-overlap.gff3");
        try (BufferedWriter writer = Files.newBufferedWriter(annotation)) {
            writer.write("##gff-version 3\n");
            String featureId = "feature_" + "x".repeat(120);
            for (int index = 0; index < 20_000; index++) {
                long start = index * 10L + 1;
                writer.write("chr1\ttest\texon\t" + start + "\t" + (start + 4)
                        + "\t.\t+\t.\tID=" + featureId + "_" + index + "\n");
            }
        }
        Path output = tempDir.resolve("bounded-overlaps.tsv");

        OpenToolException exception = assertThrows(OpenToolException.class, () -> run(
                "gxf.overlap",
                Map.of(
                        "input", annotation,
                        "overlapMode", "DISK",
                        "maxTempMegabytes", 1,
                        "output", output
                )
        ));

        assertEquals("SPILL_SPACE_LIMIT", exception.code());
        assertFalse(Files.exists(output));
    }

    @Test
    void diskOverlapEnforcesActiveRecordBoundWithoutPublishingOutput() throws Exception {
        Path annotation = tempDir.resolve("active-bound.gff3");
        Files.writeString(annotation, """
                ##gff-version 3
                chr1\ttest\texon\t1\t100\t.\t+\t.\tID=f1
                chr1\ttest\texon\t2\t99\t.\t+\t.\tID=f2
                chr1\ttest\texon\t3\t98\t.\t+\t.\tID=f3
                """);
        Path output = tempDir.resolve("active-bound-overlaps.tsv");

        OpenToolException exception = assertThrows(OpenToolException.class, () -> run(
                "gxf.overlap",
                Map.of(
                        "input", annotation,
                        "overlapMode", "DISK",
                        "maxActiveRecords", 2,
                        "output", output
                )
        ));

        assertEquals("ANNOTATION_ACTIVE_BOUND", exception.code());
        assertFalse(Files.exists(output));
    }

    @Test
    void diskOverlapAppliesPairLimitGloballyAcrossContigs() throws Exception {
        Path annotation = tempDir.resolve("global-pair-limit.gff3");
        Files.writeString(annotation, """
                ##gff-version 3
                chr2\ttest\texon\t1\t10\t.\t+\t.\tID=chr2-a
                chr2\ttest\texon\t2\t9\t.\t+\t.\tID=chr2-b
                chr1\ttest\texon\t1\t10\t.\t+\t.\tID=chr1-a
                chr1\ttest\texon\t2\t9\t.\t+\t.\tID=chr1-b
                """);
        Path output = tempDir.resolve("global-pair-limit.tsv");

        TaskResult result = run("gxf.overlap", Map.of(
                "input", annotation,
                "overlapMode", "DISK",
                "maxPairs", 1,
                "output", output
        ));

        List<String> lines = Files.readAllLines(output);
        assertEquals(2, lines.size());
        assertTrue(lines.get(1).startsWith("chr1\t"));
        assertEquals(1L, result.metrics().get("overlapPairs"));
        assertEquals(true, result.metrics().get("truncated"));
        Path temp = tempDir.resolve("out/tmp");
        if (Files.isDirectory(temp)) {
            try (var files = Files.list(temp)) {
                assertFalse(files.anyMatch(path -> path.getFileName().toString().endsWith(".sqlite")));
            }
        }
    }

    @Test
    void diskOverlapPreservesCoordinatesBeyondSinglePrecisionIntegerRange()
            throws Exception {
        Path annotation = tempDir.resolve("large-coordinate-overlap.gff3");
        Files.writeString(annotation, """
                ##gff-version 3
                chrBig\ttest\texon\t2000000001\t2000000100\t.\t+\t.\tID=left
                chrBig\ttest\texon\t2000000050\t2000000150\t.\t+\t.\tID=right
                """);
        Path output = tempDir.resolve("large-coordinate-overlaps.tsv");

        TaskResult result = run("gxf.overlap", Map.of(
                "input", annotation,
                "overlapMode", "DISK",
                "output", output
        ));

        assertEquals(1L, result.metrics().get("overlapPairs"));
        assertTrue(Files.readString(output).contains("\t51"));
    }

    @Test
    void parallelFilterAndBedStatsMatchSequentialOutput() throws Exception {
        Path annotation = tempDir.resolve("parallel-filter.gff3");
        Path bed = tempDir.resolve("parallel-filter.bed");
        StringBuilder gff = new StringBuilder("##gff-version 3\n");
        StringBuilder bedText = new StringBuilder();
        for (int index = 0; index < 75; index++) {
            long start = index * 5L + 1;
            gff.append("chr1\ttest\t").append(index % 2 == 0 ? "exon" : "gene")
                    .append('\t').append(start).append('\t').append(start + 3)
                    .append("\t.\t").append(index % 3 == 0 ? '-' : '+')
                    .append("\t.\tID=f").append(index).append('\n');
            bedText.append("chr1\t").append(start - 1).append('\t')
                    .append(start + 3).append("\t.\t.\t")
                    .append(index % 3 == 0 ? '-' : '+').append('\n');
        }
        Files.writeString(annotation, gff);
        Files.writeString(bed, bedText);
        Path sequentialGff = tempDir.resolve("sequential-filter.gff3");
        Path parallelGff = tempDir.resolve("parallel-filter-out.gff3");
        Path sequentialBed = tempDir.resolve("sequential-bed.tsv");
        Path parallelBed = tempDir.resolve("parallel-bed.tsv");

        run("gxf.filter", Map.of(
                "input", annotation,
                "types", "exon",
                "output", sequentialGff,
                "threads", 1
        ));
        var gxfResult = run("gxf.filter", Map.of(
                "input", annotation,
                "types", "exon",
                "output", parallelGff,
                "threads", 3
        ));
        run("bed.stats", Map.of(
                "input", bed,
                "output", sequentialBed,
                "threads", 1
        ));
        var bedResult = run("bed.stats", Map.of(
                "input", bed,
                "output", parallelBed,
                "threads", 3
        ));

        assertEquals(Files.readString(sequentialGff), Files.readString(parallelGff));
        assertEquals(Files.readString(sequentialBed), Files.readString(parallelBed));
        assertEquals(3, gxfResult.metrics().get("threads"));
        assertEquals(3, bedResult.metrics().get("threads"));
    }

    @Test
    void mapsIdsAndRepairsSafeAnnotationProblems() throws Exception {
        Path annotation = tempDir.resolve("broken.gff3");
        Files.writeString(annotation, """
                ##gff-version 3
                chr1\ttest\tgene\t1\t10\t.\t+\t.\tID=old_gene
                chr1\ttest\tmRNA\t1\t10\t.\t+\t.\tID=old_tx;Parent=old_gene
                chr1\ttest\tCDS\t1\t3\t.\t+\t9\tID=dup;Parent=old_tx
                chr1\ttest\tCDS\t5\t7\t.\t+\t0\tID=dup;Parent=missing
                chr1\ttest\texon\t8\t9\t.\t+\t.\tParent=old_tx
                """);
        Path mapped = tempDir.resolve("mapped.gff3");
        TaskResult mapping = run("gxf.id-map", Map.of(
                "input", annotation,
                "mapping", "old_gene\tgene.new\nold_tx\ttx.new",
                "output", mapped
        ));
        assertEquals(5L, mapping.metrics().get("replacements"));
        assertTrue(Files.readString(mapped).contains("ID=tx.new"));

        Path checked = tempDir.resolve("checked.gff3");
        TaskResult checkedResult = run("gxf.check-repair", Map.of(
                "input", annotation,
                "repair", true,
                "output", checked
        ));
        assertTrue((Long) checkedResult.metrics().get("errors") >= 2);
        assertTrue((Long) checkedResult.metrics().get("repairs") >= 2);
        String diagnostics = Files.readString(tempDir.resolve("out/annotation-diagnostics.tsv"));
        assertTrue(diagnostics.contains("DUPLICATE_ID"));
        assertTrue(diagnostics.contains("UNKNOWN_PARENT"));
        assertTrue(diagnostics.contains("INVALID_CDS_PHASE"));
        assertTrue(Files.readString(checked).contains("dup_duplicate2"));
    }

    @Test
    void computesDensityAndMergesOrSplitsAnnotation() throws Exception {
        Path first = tempDir.resolve("first.gff3");
        Path second = tempDir.resolve("second.gff3");
        Files.writeString(first, """
                ##gff-version 3
                chr1\ttest\tgene\t1\t5\t.\t+\t.\tID=g1
                chr1\ttest\texon\t1\t5\t.\t+\t.\tID=e1;Parent=g1
                """);
        Files.writeString(second, """
                ##gff-version 3
                chr1\ttest\tgene\t16\t20\t.\t-\t.\tID=g2
                chr2\ttest\tgene\t2\t4\t.\t+\t.\tID=g3
                """);
        Path density = tempDir.resolve("density.tsv");
        TaskResult densityResult = run("gxf.gene-density", Map.of(
                "input", first,
                "windowSize", 5,
                "stepSize", 5,
                "output", density
        ));
        assertTrue((Long) densityResult.metrics().get("windows") >= 1);
        assertTrue(Files.readString(density).contains("\t1\t5\t1\t"));

        Path merged = tempDir.resolve("merged.gff3");
        TaskResult merge = run("gxf.merge-split", Map.of(
                "mode", "MERGE",
                "inputs", first + "\n" + second,
                "output", merged
        ));
        assertEquals(2L, merge.metrics().get("files"));
        assertEquals(4L, merge.metrics().get("records"));

        Path split = tempDir.resolve("split");
        TaskResult splitResult = run("gxf.merge-split", Map.of(
                "mode", "SPLIT",
                "inputs", merged.toString(),
                "outputDirectory", split.toString(),
                "splitBy", "SEQ_ID"
        ));
        assertEquals(2, splitResult.metrics().get("parts"));
        assertTrue(Files.isRegularFile(split.resolve("chr1.gff3")));
        assertTrue(Files.isRegularFile(split.resolve("chr2.gff3")));
    }

    private Path writeTranscripts() throws Exception {
        Path annotation = tempDir.resolve("transcripts.gff3");
        Files.writeString(annotation, """
                ##gff-version 3
                chr1\ttest\tgene\t1\t20\t.\t+\t.\tID=g1
                chr1\ttest\tmRNA\t1\t10\t.\t+\t.\tID=tx_long;Parent=g1
                chr1\ttest\texon\t1\t5\t.\t+\t.\tID=e1;Parent=tx_long
                chr1\ttest\texon\t6\t10\t.\t+\t.\tID=e2;Parent=tx_long
                chr1\ttest\tmRNA\t1\t4\t.\t+\t.\tID=tx_short;Parent=g1
                chr1\ttest\texon\t1\t4\t.\t+\t.\tID=e3;Parent=tx_short
                """);
        return annotation;
    }

    private TaskResult run(String toolId, Map<String, Object> supplied) throws Exception {
        OpenTool tool = tool(toolId);
        Map<String, Object> parameters = new LinkedHashMap<>();
        for (ToolParameter parameter : tool.descriptor().parameters()) {
            if (parameter.defaultValue() != null) {
                parameters.put(parameter.id(), parameter.defaultValue());
            }
        }
        parameters.putAll(supplied);
        ToolRequest request = new ToolRequest(toolId, parameters);
        ValidationResult validation = tool.validate(request);
        assertTrue(validation.valid(), validation.issues().toString());
        Path outputDirectory = Files.createDirectories(tempDir.resolve("out"));
        return tool.execute(request, new TaskContext(
                "annotation-test",
                tempDir,
                outputDirectory,
                outputDirectory,
                ignored -> { },
                ignored -> { },
                new CancellationToken()
        ));
    }

    private OpenTool tool(String toolId) {
        ToolRegistry registry = new ToolRegistry();
        BuiltInTools.register(registry);
        return registry.find(toolId).orElseThrow();
    }
}
