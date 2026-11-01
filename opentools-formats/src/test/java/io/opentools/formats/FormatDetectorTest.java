package io.opentools.formats;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class FormatDetectorTest {
    @TempDir
    Path tempDir;

    @Test
    void detectsFasta() throws Exception {
        Path file = tempDir.resolve("sample.fa");
        Files.writeString(file, ">seq1\nACGT\n");

        FormatDetection detection = new FormatDetector().detect(file);

        assertEquals(FormatType.FASTA, detection.type());
    }

    @Test
    void detectsFastqBeforeGffHeuristics() throws Exception {
        Path file = tempDir.resolve("reads.fq");
        Files.writeString(file, "@r1\nACGT\n+\nIIII\n");

        FormatDetection detection = new FormatDetector().detect(file);

        assertEquals(FormatType.FASTQ, detection.type());
    }

    @Test
    void detectsGtfAttributes() throws Exception {
        Path file = tempDir.resolve("genes.gtf");
        Files.writeString(file, """
                chr1\tsrc\tgene\t1\t100\t.\t+\t.\tgene_id "g1"; transcript_id "t1";
                """);

        FormatDetection detection = new FormatDetector().detect(file);

        assertEquals(FormatType.GTF, detection.type());
    }
}
