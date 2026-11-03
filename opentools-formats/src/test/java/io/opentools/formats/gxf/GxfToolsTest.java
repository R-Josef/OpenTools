package io.opentools.formats.gxf;

import io.opentools.api.tool.CancellationToken;
import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GxfToolsTest {
    @TempDir
    Path tempDir;

    @Test
    void parsesGff3AndGtfAttributes() throws Exception {
        Path gff = tempDir.resolve("sample.gff3");
        Files.writeString(gff, """
                ##gff-version 3
                chr1\ttest\tCDS\t1\t3\t.\t+\t0\tID=cds1;Parent=tx1
                """);
        Path gtf = tempDir.resolve("sample.gtf");
        Files.writeString(gtf, """
                chr1\ttest\texon\t2\t4\t.\t-\t.\tgene_id "g1"; transcript_id "t1";
                """);

        try (GxfReader reader = new GxfReader(gff)) {
            GxfRecord record = reader.next();
            assertEquals("CDS", record.type());
            assertEquals("tx1", record.firstAttribute("Parent"));
        }
        try (GxfReader reader = new GxfReader(gtf)) {
            GxfRecord record = reader.next();
            assertEquals("t1", record.firstAttribute("transcript_id"));
            assertEquals('-', record.strand());
        }
    }

    @Test
    void parallelInspectionMatchesSequentialOutput() throws Exception {
        Path annotation = tempDir.resolve("parallel-inspect.gff3");
        StringBuilder content = new StringBuilder("##gff-version 3\n");
        for (int index = 0; index < 80; index++) {
            content.append("chr").append(index % 4 + 1)
                    .append("\ttest\t").append(index % 3 == 0 ? "exon" : "gene")
                    .append('\t').append(index + 1).append('\t')
                    .append(index + 10).append("\t.\t+\t.\tID=f")
                    .append(index).append('\n');
        }
        Files.writeString(annotation, content);
        Path sequential = tempDir.resolve("sequential-inspect.tsv");
        Path parallel = tempDir.resolve("parallel-inspect.tsv");

        new GxfInspectTool().execute(new ToolRequest("gxf.inspect", Map.of(
                "input", annotation.toString(),
                "output", sequential.toString(),
                "threads", 1
        )), context());
        var result = new GxfInspectTool().execute(new ToolRequest("gxf.inspect", Map.of(
                "input", annotation.toString(),
                "output", parallel.toString(),
                "threads", 3
        )), context());

        assertEquals(Files.readString(sequential), Files.readString(parallel));
        assertEquals(
                Files.readString(tempDir.resolve("sequential-inspect-features.tsv")),
                Files.readString(tempDir.resolve("parallel-inspect-features.tsv"))
        );
        assertEquals(3, result.metrics().get("threads"));
    }

    @Test
    void inspectsAndMatchesGenomeAnnotation() throws Exception {
        Path genome = writeGenome();
        Path annotation = writeAnnotation();
        ToolExecutionContext context = context();

        var inspection = new GxfInspectTool().execute(
                new ToolRequest("gxf.inspect", Map.of(
                        "input", annotation.toString(),
                        "output", tempDir.resolve("inspect.tsv").toString()
                )),
                context
        );
        var match = new GxfGenomeMatchTool().execute(
                new ToolRequest("gxf.genome.match", Map.of(
                        "genome", genome.toString(),
                        "annotation", annotation.toString(),
                        "report", tempDir.resolve("match.tsv").toString()
                )),
                context
        );

        assertEquals(7L, inspection.metrics().get("records"));
        assertEquals(2L, match.metrics().get("matchedSequences"));
        assertEquals(0L, match.metrics().get("outOfRangeRecords"));
    }

    @Test
    void extractsFeatureAndStrandAwarePromoters() throws Exception {
        Path genome = writeGenome();
        Path annotation = writeAnnotation();
        ToolExecutionContext context = context();

        Path plusFeature = tempDir.resolve("plus-cds.fa");
        var extraction = new GxfSequenceExtractTool().execute(
                new ToolRequest("gxf.sequence.extract", Map.of(
                        "genome", genome.toString(),
                        "annotation", annotation.toString(),
                        "featureType", "CDS",
                        "groupAttribute", "Parent",
                        "mode", "FEATURE",
                        "upstream", 0,
                        "downstream", 0,
                        "output", plusFeature.toString()
                )),
                context
        );
        assertTrue(Files.readString(plusFeature).contains("AAGG"));
        assertEquals("indexed", extraction.metrics().get("genomeStorage"));

        Path minusExon = tempDir.resolve("minus-exon.fa");
        new GxfSequenceExtractTool().execute(
                new ToolRequest("gxf.sequence.extract", Map.of(
                        "genome", genome.toString(),
                        "annotation", annotation.toString(),
                        "featureType", "exon",
                        "groupAttribute", "Parent",
                        "mode", "FEATURE",
                        "upstream", 0,
                        "downstream", 0,
                        "output", minusExon.toString()
                )),
                context
        );
        assertTrue(Files.readString(minusExon).contains("TT"));

        Path plusPromoter = tempDir.resolve("plus-promoter.fa");
        new GxfSequenceExtractTool().execute(
                new ToolRequest("gxf.sequence.extract", Map.of(
                        "genome", genome.toString(),
                        "annotation", annotation.toString(),
                        "featureType", "exon",
                        "groupAttribute", "Parent",
                        "mode", "UPSTREAM",
                        "upstream", 2,
                        "downstream", 0,
                        "output", plusPromoter.toString()
                )),
                context
        );
        assertTrue(Files.readString(plusPromoter).contains("AA"));

        Path minusPromoter = tempDir.resolve("minus-promoter.fa");
        new GxfSequenceExtractTool().execute(
                new ToolRequest("gxf.sequence.extract", Map.of(
                        "genome", genome.toString(),
                        "annotation", annotation.toString(),
                        "featureType", "gene",
                        "groupAttribute", "ID",
                        "mode", "UPSTREAM",
                        "upstream", 2,
                        "downstream", 0,
                        "output", minusPromoter.toString()
                )),
                context
        );
        assertTrue(Files.readString(minusPromoter).contains("GG"));
    }

    @Test
    void reportsMissingSequenceIdsAndOutOfRangeCoordinates() throws Exception {
        Path genome = writeGenome();
        Path annotation = tempDir.resolve("mismatch.gff3");
        Files.writeString(annotation, """
                ##gff-version 3
                chr1\ttest\tgene\t1\t4\t.\t+\t.\tID=ok
                chrX\ttest\tgene\t1\t2\t.\t+\t.\tID=missing
                chr2\ttest\tgene\t1\t99\t.\t+\t.\tID=too_long
                """);
        Path report = tempDir.resolve("mismatch.tsv");

        var result = new GxfGenomeMatchTool().execute(
                new ToolRequest("gxf.genome.match", Map.of(
                        "genome", genome.toString(),
                        "annotation", annotation.toString(),
                        "report", report.toString()
                )),
                context()
        );

        assertEquals(1, result.metrics().get("missingSequences"));
        assertEquals(1L, result.metrics().get("outOfRangeRecords"));
        assertTrue(Files.readString(report).contains("MISSING_SEQUENCE"));
        assertTrue(Files.readString(report).contains("COORDINATE_OUT_OF_RANGE"));
    }

    @Test
    void extractsUsingGtfTranscriptGrouping() throws Exception {
        Path genome = writeGenome();
        Path gtf = tempDir.resolve("sample.gtf");
        Files.writeString(gtf, "chr1\ttest\texon\t2\t4\t.\t+\t.\tgene_id \"g1\"; transcript_id \"t1\";\n");
        Path output = tempDir.resolve("gtf-extracted.fa");

        var result = new GxfSequenceExtractTool().execute(
                new ToolRequest("gxf.sequence.extract", Map.of(
                        "genome", genome.toString(),
                        "annotation", gtf.toString(),
                        "featureType", "exon",
                        "groupAttribute", "transcript_id",
                        "mode", "FEATURE",
                        "upstream", 0,
                        "downstream", 0,
                        "output", output.toString()
                )),
                context()
        );

        assertEquals(1L, result.metrics().get("extracted"));
        assertTrue(Files.readString(output).contains("ACC"));
    }

    @Test
    void decodesEscapedCommasAfterSplittingLists() throws Exception {
        Path gff = tempDir.resolve("escaped.gff3");
        Files.writeString(gff, """
                ##gff-version 3
                chr1	test	gene	1	3	.	+	.	ID=g1;Note=hello%2Cworld;Alias=a,b
                """);
        try (GxfReader reader = new GxfReader(gff)) {
            GxfRecord record = reader.next();
            assertEquals(List.of("hello,world"), record.attributes().get("Note"));
            assertEquals(List.of("a", "b"), record.attributes().get("Alias"));
        }
    }

    @Test
    void skipsMalformedLinesInLenientMode() throws Exception {
        Path gff = tempDir.resolve("broken.gff3");
        Files.writeString(gff, """
                ##gff-version 3
                chr1	test	gene	1	3	.	+	.	ID=g1
                chr1	test	gene	5
                chr1	test	gene	4	6	.	+	.	ID=g2
                """);
        try (GxfReader reader = new GxfReader(gff, true)) {
            assertEquals("g1", reader.next().firstAttribute("ID"));
            assertEquals("g2", reader.next().firstAttribute("ID"));
            assertEquals(null, reader.next());
            assertEquals(1, reader.warnings().size());
            assertTrue(reader.warnings().getFirst().contains("line 3"));
        }
    }

    @Test
    void appliesGffPhaseToPartialCds() throws Exception {
        Path genome = tempDir.resolve("phase-genome.fa");
        Files.writeString(genome, ">chr1\nAAACCCGGGTTT\n");
        Path annotation = tempDir.resolve("phase.gff3");
        Files.writeString(annotation, """
                ##gff-version 3
                chr1	test	gene	4	12	.	+	.	ID=g1
                chr1	test	mRNA	4	12	.	+	.	ID=tx1;Parent=g1
                chr1	test	CDS	4	6	.	+	1	ID=c1;Parent=tx1
                chr1	test	CDS	7	12	.	+	0	ID=c2;Parent=tx1
                """);
        Path output = tempDir.resolve("phase.fa");
        new GxfSequenceExtractTool().execute(
                new ToolRequest("gxf.sequence.extract", Map.of(
                        "genome", genome.toString(),
                        "annotation", annotation.toString(),
                        "featureType", "CDS",
                        "groupAttribute", "Parent",
                        "mode", "FEATURE",
                        "output", output.toString()
                )),
                context()
        );
        assertEquals("CCGGGTTT", Files.readAllLines(output).get(1));
    }

    private Path writeGenome() throws Exception {
        Path genome = tempDir.resolve("genome.fa");
        Files.writeString(genome, ">chr1\nAACCGGTT\n>chr2\nAAAACCCC\n");
        return genome;
    }

    private Path writeAnnotation() throws Exception {
        Path annotation = tempDir.resolve("annotation.gff3");
        Files.writeString(annotation, """
                ##gff-version 3
                chr1\ttest\tgene\t3\t4\t.\t+\t.\tID=gene1
                chr1\ttest\tmRNA\t3\t4\t.\t+\t.\tID=tx1;Parent=gene1
                chr1\ttest\texon\t3\t4\t.\t+\t.\tID=exon1;Parent=tx1
                chr1\ttest\tCDS\t1\t2\t.\t+\t0\tID=cds1;Parent=tx1
                chr1\ttest\tCDS\t5\t6\t.\t+\t0\tID=cds2;Parent=tx1
                chr2\ttest\tgene\t3\t4\t.\t-\t.\tID=gene2
                chr2\ttest\texon\t3\t4\t.\t-\t.\tID=exon2;Parent=gene2
                """);
        return annotation;
    }

    private ToolExecutionContext context() {
        Path run = tempDir.resolve("run-" + System.nanoTime());
        return new ToolExecutionContext("run", tempDir, run, run, ignored -> { }, ignored -> { }, new CancellationToken());
    }
}
