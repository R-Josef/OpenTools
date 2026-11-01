package io.opentools.analysis.hts;

import htsjdk.samtools.SAMFileWriter;
import htsjdk.samtools.SAMFileWriterFactory;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.reference.FastaSequenceIndexCreator;
import htsjdk.variant.vcf.VCFFileReader;
import io.opentools.api.tool.CancellationToken;
import io.opentools.api.tool.OpenTool;
import io.opentools.api.tool.OpenToolException;
import io.opentools.api.tool.TaskContext;
import io.opentools.api.tool.TaskResult;
import io.opentools.api.tool.ToolParameter;
import io.opentools.api.tool.ToolRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class HtsToolsTest {
    @TempDir
    Path tempDir;

    @Test
    void summarizesSubsetsAndComputesCoverageWithHtsjdk() throws Exception {
        Path sam = tempDir.resolve("reads.sam");
        Files.writeString(sam, """
                @HD\tVN:1.6\tSO:coordinate
                @SQ\tSN:chr1\tLN:100
                r1\t0\tchr1\t1\t60\t4M\t*\t0\t0\tACGT\tIIII\tNM:i:0
                r2\t4\t*\t0\t0\t*\t*\t0\t0\tNNNN\tIIII
                """);
        Path stats = tempDir.resolve("stats.tsv");
        TaskResult statsResult = run("hts.sam-stats", Map.of("input", sam, "output", stats));
        assertEquals(2L, statsResult.metrics().get("records"));
        assertEquals(1L, statsResult.metrics().get("mapped"));

        Path subset = tempDir.resolve("subset.sam");
        run("hts.bam.subset", Map.of(
                "input", sam, "regions", "chr1:1-4", "output", subset));
        assertTrue(Files.readAllLines(subset).stream().anyMatch(line -> line.startsWith("r1\t")));

        Path bed = tempDir.resolve("regions.bed");
        Files.writeString(bed, "chr1\t0\t4\n");
        Path coverage = tempDir.resolve("coverage.bedgraph");
        run("hts.bam.coverage", Map.of("input", sam, "regions", bed, "output", coverage));
        assertTrue(Files.readString(coverage).contains("chr1\t0\t4\t1"));

        Path bedCoverage = tempDir.resolve("bed-coverage.tsv");
        run("bed.coverage", Map.of("alignment", sam, "regions", bed, "output", bedCoverage));
        assertTrue(Files.readString(bedCoverage).contains("\t1.000000\t4"));
    }

    @Test
    void summarizesAndFiltersVcfWithHtsjdk() throws Exception {
        Path vcf = tempDir.resolve("variants.vcf");
        Files.writeString(vcf, """
                ##fileformat=VCFv4.2
                ##contig=<ID=chr1,length=100>
                ##FORMAT=<ID=GT,Number=1,Type=String,Description="Genotype">
                #CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\tS1
                chr1\t10\t.\tA\tG\t50\tPASS\t.\tGT\t0/1
                chr1\t20\t.\tAT\tA\t10\tPASS\t.\tGT\t./.
                """);
        Path stats = tempDir.resolve("vcf-stats.tsv");
        TaskResult statsResult = run("hts.vcf.stats", Map.of("input", vcf, "output", stats));
        assertEquals(2L, statsResult.metrics().get("variants"));
        assertEquals(1L, statsResult.metrics().get("snps"));
        assertEquals(1L, statsResult.metrics().get("indels"));

        Path filtered = tempDir.resolve("filtered.vcf");
        TaskResult filterResult = run("hts.vcf.filter", Map.of(
                "input", vcf, "minQual", 20.0, "maxMissing", 0.5,
                "biallelicOnly", true, "output", filtered));
        assertEquals(1L, filterResult.metrics().get("retained"));
        assertEquals("NONE", filterResult.metrics().get("indexType"));
        assertEquals(false, filterResult.metrics().get("indexCreated"));
        assertTrue(Files.readString(filtered).contains("chr1\t10"));
        assertTrue(!Files.readString(filtered).contains("chr1\t20"));
    }

    @Test
    void parallelVcfStatsMatchSequentialOutput() throws Exception {
        Path vcf = tempDir.resolve("parallel-stats.vcf");
        StringBuilder content = new StringBuilder("""
                ##fileformat=VCFv4.2
                ##contig=<ID=chr1,length=100000>
                #CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO
                """);
        for (int index = 0; index < 300; index++) {
            content.append("chr1\t").append(index + 1).append("\t.\t")
                    .append(index % 3 == 0 ? "AT" : "A").append('\t')
                    .append(index % 3 == 0 ? "A" : "G")
                    .append("\t50\tPASS\t.\n");
        }
        Files.writeString(vcf, content);
        Path sequential = tempDir.resolve("vcf-stats-sequential.tsv");
        Path parallel = tempDir.resolve("vcf-stats-parallel.tsv");

        run("hts.vcf.stats", Map.of(
                "input", vcf, "threads", 1, "output", sequential));
        TaskResult result = run("hts.vcf.stats", Map.of(
                "input", vcf, "threads", 3, "output", parallel));

        assertEquals(Files.readString(sequential), Files.readString(parallel));
        assertEquals(3, result.metrics().get("threads"));
    }

    @Test
    void usesBamIndexForSubsetAndCoverageAndCreatesOutputIndex() throws Exception {
        Path bam = tempDir.resolve("indexed.bam");
        createIndexedBam(bam);

        Path subset = tempDir.resolve("indexed-subset.bam");
        TaskResult subsetResult = run("hts.bam.subset", Map.of(
                "input", bam,
                "regions", "chr1:1-5\nchr1:3-8",
                "output", subset,
                "createIndex", true
        ));
        assertEquals(1L, subsetResult.metrics().get("records"));
        assertEquals(true, subsetResult.metrics().get("inputIndexUsed"));
        assertEquals(true, subsetResult.metrics().get("outputIndexCreated"));

        Path bed = tempDir.resolve("indexed-regions.bed");
        Files.writeString(bed, "chr1\t0\t10\n");
        Path coverage = tempDir.resolve("indexed-coverage.bedgraph");
        TaskResult coverageResult = run("hts.bam.coverage", Map.of(
                "input", bam,
                "regions", bed,
                "output", coverage
        ));
        assertEquals(true, coverageResult.metrics().get("inputIndexUsed"));
        assertEquals("interval-events", coverageResult.metrics().get("coverageMemory"));
        assertTrue(Files.readString(coverage).contains("chr1\t0\t4\t1"));

        Path parallelCoverage = tempDir.resolve("parallel-coverage.bedgraph");
        TaskResult parallelCoverageResult = run("hts.bam.coverage", Map.of(
                "input", bam,
                "regions", bed,
                "threads", 3,
                "output", parallelCoverage
        ));
        assertEquals(3, parallelCoverageResult.metrics().get("threads"));
        assertEquals(true, parallelCoverageResult.metrics().get("parallelRegions"));
        assertEquals(Files.readString(coverage), Files.readString(parallelCoverage));

        Path bedCoverage = tempDir.resolve("indexed-bed-coverage.tsv");
        TaskResult bedResult = run("bed.coverage", Map.of(
                "alignment", bam,
                "regions", bed,
                "output", bedCoverage
        ));
        assertEquals(true, bedResult.metrics().get("inputIndexUsed"));
        assertTrue(Files.readString(bedCoverage).contains("\t0.400000\t4"));

        Path parallelBedCoverage = tempDir.resolve("parallel-bed-coverage.tsv");
        run("bed.coverage", Map.of(
                "alignment", bam,
                "regions", bed,
                "threads", 3,
                "output", parallelBedCoverage
        ));
        assertEquals(Files.readString(bedCoverage), Files.readString(parallelBedCoverage));
    }

    @Test
    void subsetReadsReferenceDependentCramAndRequiresExplicitReference() throws Exception {
        Path reference = tempDir.resolve("reference.fa");
        Files.writeString(reference, ">chr1\n" + "A".repeat(200) + "\n");
        FastaSequenceIndexCreator.create(reference, true);
        Files.writeString(
                tempDir.resolve("reference.dict"),
                "@HD\tVN:1.6\tSO:unsorted\n@SQ\tSN:chr1\tLN:200\n"
        );
        Path bam = tempDir.resolve("cram-source.bam");
        createIndexedBam(bam);
        Path cram = tempDir.resolve("reference-dependent.cram");
        try (SamReader reader = SamReaderFactory.makeDefault().open(bam);
             SAMFileWriter writer = new SAMFileWriterFactory()
                     .makeCRAMWriter(reader.getFileHeader(), true, cram, reference)) {
            for (SAMRecord record : reader) {
                writer.addAlignment(record);
            }
        }

        Path subset = tempDir.resolve("cram-subset.bam");
        TaskResult result = run("hts.bam.subset", Map.of(
                "input", cram,
                "reference", reference,
                "regions", "chr1:1-10",
                "threads", 1,
                "createIndex", true,
                "output", subset
        ));
        assertEquals(1L, result.metrics().get("records"));
        try (SamReader reader = SamReaderFactory.makeDefault().open(subset)) {
            assertTrue(reader.hasIndex());
            int records = 0;
            try (var iterator = reader.iterator()) {
                while (iterator.hasNext()) {
                    iterator.next();
                    records++;
                }
            }
            assertEquals(1, records);
        }

        Path missingReference = tempDir.resolve("cram-missing-reference.bam");
        OpenToolException failure = assertThrows(OpenToolException.class, () -> run(
                "hts.bam.subset",
                Map.of(
                        "input", cram,
                        "regions", "chr1:1-10",
                        "threads", 1,
                        "createIndex", true,
                        "output", missingReference
                )
        ));
        assertEquals("CRAM_REFERENCE_REQUIRED", failure.code());
        assertFalse(Files.exists(missingReference));
        assertFalse(Files.exists(missingReference.resolveSibling("cram-missing-reference.bam.bai")));
    }

    @Test
    void parallelBamSubsetDeduplicatesMergedRegionsAndKeepsCoordinateOrder() throws Exception {
        Path bam = tempDir.resolve("parallel-indexed.bam");
        createIndexedBam(bam, """
                @HD\tVN:1.6\tSO:coordinate
                @SQ\tSN:chr1\tLN:100
                r1\t0\tchr1\t1\t60\t4M\t*\t0\t0\tACGT\tIIII
                span\t0\tchr1\t7\t60\t20M\t*\t0\t0\tACGTACGTACGTACGTACGT\tIIIIIIIIIIIIIIIIIIII
                r2\t0\tchr1\t20\t60\t3M\t*\t0\t0\tACG\tIII
                r3\t0\tchr1\t40\t60\t2M\t*\t0\t0\tAC\tII
                """);
        Path sequential = tempDir.resolve("parallel-sequential.bam");
        Path parallel = tempDir.resolve("parallel-workers.bam");

        run("hts.bam.subset", Map.of(
                "input", bam,
                "regions", "chr1:20-30\nchr1:1-10\nchr1:20-30",
                "threads", 1,
                "output", sequential,
                "createIndex", false
        ));
        TaskResult result = run("hts.bam.subset", Map.of(
                "input", bam,
                "regions", "chr1:20-30\nchr1:1-10\nchr1:20-30",
                "threads", 3,
                "output", parallel,
                "createIndex", true
        ));

        List<String> sequentialRecords = records(sequential);
        assertEquals(sequentialRecords, records(parallel));
        assertEquals(-1L, Files.mismatch(sequential, parallel));
        assertEquals(List.of("r1", "span", "r2"), readNames(parallel));
        assertEquals(3L, result.metrics().get("records"));
        assertEquals(3, result.metrics().get("threads"));
        assertEquals(true, result.metrics().get("parallelRegions"));
        assertEquals(true, result.metrics().get("outputIndexCreated"));
        try (SamReader reader = SamReaderFactory.makeDefault().open(parallel)) {
            assertEquals(htsjdk.samtools.SAMFileHeader.SortOrder.coordinate,
                    reader.getFileHeader().getSortOrder());
            assertTrue(reader.hasIndex());
            try (var query = reader.queryOverlapping("chr1", 1, 10)) {
                assertTrue(query.hasNext());
            }
        }
        assertFalse(hasTemporaryBamFiles(tempDir.resolve("tmp")));
    }

    @Test
    void cancelledOrFailedBamSubsetDoesNotPublishPartialOutput() throws Exception {
        Path bam = tempDir.resolve("cancel-indexed.bam");
        createIndexedBam(bam, """
                @HD\tVN:1.6\tSO:coordinate
                @SQ\tSN:chr1\tLN:100
                r1\t0\tchr1\t1\t60\t4M\t*\t0\t0\tACGT\tIIII
                r2\t0\tchr1\t20\t60\t4M\t*\t0\t0\tACGT\tIIII
                """);
        Path cancelled = tempDir.resolve("cancelled-subset.bam");
        CancellationToken cancellation = new CancellationToken();
        cancellation.cancel();

        assertThrows(java.util.concurrent.CancellationException.class, () -> run(
                "hts.bam.subset",
                Map.of(
                        "input", bam,
                        "regions", "chr1:1-10\nchr1:20-30",
                        "threads", 2,
                        "output", cancelled,
                        "createIndex", true
                ),
                cancellation
        ));
        assertFalse(Files.exists(cancelled));
        assertFalse(Files.exists(cancelled.resolveSibling("cancelled-subset.bam.bai")));
        assertFalse(Files.exists(cancelled.resolveSibling("cancelled-subset.bai")));

        Path failed = tempDir.resolve("failed-subset.bam");
        OpenToolException exception = assertThrows(OpenToolException.class, () -> run(
                "hts.bam.subset",
                Map.of(
                        "input", bam,
                        "regions", "missing:1-10\nchr1:20-30",
                        "threads", 2,
                        "output", failed,
                        "createIndex", true
                )
        ));
        assertEquals("ALIGNMENT_CONTIG_MISSING", exception.code());
        assertFalse(Files.exists(failed));
        assertFalse(hasTemporaryBamFiles(tempDir.resolve("tmp")));
        assertNoAtomicSidecars();
    }

    @Test
    void indexTargetConflictsDoNotPublishPrimaryOutputs() throws Exception {
        Path bam = tempDir.resolve("conflict-indexed.bam");
        createIndexedBam(bam);
        Path bamOutput = tempDir.resolve("conflict-subset.bam");
        Files.createDirectories(bamOutput.resolveSibling("conflict-subset.bam.bai"));

        OpenToolException bamFailure = assertThrows(OpenToolException.class, () -> run(
                "hts.bam.subset",
                Map.of(
                        "input", bam,
                        "regions", "chr1:1-10\nchr1:20-30",
                        "threads", 2,
                        "output", bamOutput,
                        "createIndex", true
                )
        ));
        assertEquals("ALIGNMENT_INDEX_TARGET_CONFLICT", bamFailure.code());
        assertFalse(Files.exists(bamOutput));

        Path vcf = writeIndexableVcf();
        Path vcfOutput = tempDir.resolve("conflict-filtered.vcf.gz");
        Files.createDirectories(vcfOutput.resolveSibling("conflict-filtered.vcf.gz.tbi"));
        OpenToolException vcfFailure = assertThrows(OpenToolException.class, () -> run(
                "hts.vcf.filter",
                Map.of(
                        "input", vcf,
                        "output", vcfOutput,
                        "threads", 2,
                        "indexType", "AUTO"
                )
        ));
        assertEquals("VCF_INDEX_TARGET_CONFLICT", vcfFailure.code());
        assertFalse(Files.exists(vcfOutput));

        Path directoryOutput = tempDir.resolve("directory-output.vcf.gz");
        Files.createDirectories(directoryOutput);
        OpenToolException outputConflict = assertThrows(OpenToolException.class, () -> run(
                "hts.vcf.filter",
                Map.of(
                        "input", vcf,
                        "output", directoryOutput,
                        "overwrite", true,
                        "indexType", "NONE"
                )
        ));
        assertEquals("VCF_OUTPUT_TARGET_CONFLICT", outputConflict.code());
        assertTrue(Files.isDirectory(directoryOutput));
        assertNoAtomicSidecars();
    }

    @Test
    void htsParallelTasksExposeUnifiedThreadContract() {
        for (String id : List.of("hts.bam.subset", "hts.vcf.filter")) {
            OpenTool tool = HtsTools.tools().stream()
                    .filter(candidate -> candidate.descriptor().id().equals(id))
                    .findFirst()
                    .orElseThrow();
            ToolParameter threads = tool.descriptor().parameters().stream()
                    .filter(parameter -> parameter.id().equals("threads"))
                    .findFirst()
                    .orElseThrow();
            assertEquals(0, threads.defaultValue());
            assertEquals(0, threads.min());
            assertEquals(64, threads.max());
        }
    }

    @Test
    void coverageCanCountOverlappingReadPairsOnce() throws Exception {
        Path sam = tempDir.resolve("overlapping-pairs.sam");
        Files.writeString(sam, """
                @HD\tVN:1.6\tSO:coordinate
                @SQ\tSN:chr1\tLN:100
                q1\t67\tchr1\t1\t60\t10M\t=\t6\t15\tACGTACGTAC\tIIIIIIIIII
                q1\t131\tchr1\t6\t60\t10M\t=\t1\t-15\tACGTACGTAC\tIIIIIIIIII
                """);
        Path bed = tempDir.resolve("pair-region.bed");
        Files.writeString(bed, "chr1\t0\t15\n");

        Path rawOutput = tempDir.resolve("raw-pair-coverage.tsv");
        run("bed.coverage", Map.of(
                "alignment", sam,
                "regions", bed,
                "output", rawOutput
        ));
        assertTrue(Files.readString(rawOutput).contains("\t1.333333\t15"));

        Path deduplicatedOutput = tempDir.resolve("deduplicated-pair-coverage.tsv");
        TaskResult result = run("bed.coverage", Map.of(
                "alignment", sam,
                "regions", bed,
                "countOverlappingPairsOnce", true,
                "output", deduplicatedOutput
        ));
        assertEquals(true, result.metrics().get("overlappingPairsCountedOnce"));
        assertTrue(Files.readString(deduplicatedOutput).contains("\t1.000000\t15"));

        Path deduplicatedBedGraph = tempDir.resolve("deduplicated-pairs.bedgraph");
        run("hts.bam.coverage", Map.of(
                "input", sam,
                "regions", bed,
                "countOverlappingPairsOnce", true,
                "output", deduplicatedBedGraph
        ));
        assertTrue(Files.readString(deduplicatedBedGraph).contains("chr1\t0\t15\t1"));
        assertTrue(!Files.readString(deduplicatedBedGraph).contains("\t2\n"));

        Path splicedSam = tempDir.resolve("spliced.sam");
        Files.writeString(splicedSam, """
                @HD\tVN:1.6\tSO:coordinate
                @SQ\tSN:chr1\tLN:100
                spliced\t0\tchr1\t1\t60\t3M5N2M\t*\t0\t0\tACGTA\tIIIII
                """);
        Path splicedBed = tempDir.resolve("spliced-region.bed");
        Files.writeString(splicedBed, "chr1\t0\t10\n");
        Path splicedOutput = tempDir.resolve("spliced-coverage.tsv");
        run("bed.coverage", Map.of(
                "alignment", splicedSam,
                "regions", splicedBed,
                "countOverlappingPairsOnce", true,
                "output", splicedOutput
        ));
        assertTrue(Files.readString(splicedOutput).contains("\t0.500000\t5"));
    }

    @Test
    void mateDeduplicationSeparatesReadGroupsAndPreservesSplicedPairs() throws Exception {
        Path sam = tempDir.resolve("paired-read-groups.sam");
        Files.writeString(sam, """
                @HD\tVN:1.6\tSO:coordinate
                @SQ\tSN:chr1\tLN:100
                @RG\tID:rg1\tSM:sample
                @RG\tID:rg2\tSM:sample
                q1\t67\tchr1\t1\t60\t10M\t=\t6\t15\tACGTACGTAC\tIIIIIIIIII\tRG:Z:rg1
                q1\t67\tchr1\t1\t60\t10M\t=\t6\t15\tACGTACGTAC\tIIIIIIIIII\tRG:Z:rg2
                q1\t131\tchr1\t6\t60\t10M\t=\t1\t-15\tACGTACGTAC\tIIIIIIIIII\tRG:Z:rg1
                q1\t131\tchr1\t6\t60\t10M\t=\t1\t-15\tACGTACGTAC\tIIIIIIIIII\tRG:Z:rg2
                """);
        Path bed = tempDir.resolve("read-group-region.bed");
        Files.writeString(bed, "chr1\t0\t15\n");
        Path output = tempDir.resolve("read-group-coverage.tsv");

        run("bed.coverage", Map.of(
                "alignment", sam,
                "regions", bed,
                "countOverlappingPairsOnce", true,
                "output", output
        ));

        assertTrue(Files.readString(output).contains("\t2.000000\t15"));

        Path spliced = tempDir.resolve("spliced-pair.sam");
        Files.writeString(spliced, """
                @HD\tVN:1.6\tSO:coordinate
                @SQ\tSN:chr1\tLN:100
                pair\t67\tchr1\t1\t60\t3M5N2M\t=\t8\t12\tACGTA\tIIIII
                pair\t147\tchr1\t8\t60\t3M5N2M\t=\t1\t-12\tACGTA\tIIIII
                """);
        Path splicedBed = tempDir.resolve("spliced-pair-region.bed");
        Files.writeString(splicedBed, "chr1\t0\t10\n");
        Path splicedOutput = tempDir.resolve("spliced-pair-coverage.bedgraph");
        run("hts.bam.coverage", Map.of(
                "input", spliced,
                "regions", splicedBed,
                "countOverlappingPairsOnce", true,
                "output", splicedOutput
        ));
        String coverage = Files.readString(splicedOutput);
        assertTrue(coverage.contains("chr1\t0\t3\t1"), coverage);
        assertTrue(coverage.contains("chr1\t7\t10\t1"), coverage);
        assertFalse(coverage.contains("\t2\n"), coverage);
    }

    @Test
    void vcfFilterCreatesTbiAndCsiIndexesOnRequest() throws Exception {
        Path vcf = writeIndexableVcf();
        Path tabixOutput = tempDir.resolve("indexed.vcf.gz");
        TaskResult tabix = run("hts.vcf.filter", Map.of(
                "input", vcf,
                "output", tabixOutput,
                "indexType", "AUTO"
        ));
        assertEquals("TBI", tabix.metrics().get("indexType"));
        assertEquals(true, tabix.metrics().get("indexCreated"));
        try (VCFFileReader reader = new VCFFileReader(tabixOutput, false)) {
            assertTrue(reader.isQueryable());
            try (var variants = reader.query("chr1", 1, 15)) {
                assertTrue(variants.hasNext());
            }
        }

        Path bcfOutput = tempDir.resolve("indexed.bcf");
        TaskResult csi = run("hts.vcf.filter", Map.of(
                "input", vcf,
                "output", bcfOutput,
                "indexType", "AUTO"
        ));
        assertEquals("CSI", csi.metrics().get("indexType"));
        assertEquals(true, csi.metrics().get("indexCreated"));
        try (VCFFileReader reader = new VCFFileReader(bcfOutput, false)) {
            assertTrue(reader.isQueryable());
            try (var variants = reader.query("chr1", 1, 15)) {
                assertTrue(variants.hasNext());
            }
        }
    }

    @Test
    void parallelCompressedVcfFilterIsByteIdenticalAndDictionaryOrdered() throws Exception {
        Path vcf = tempDir.resolve("dictionary-order.vcf");
        Files.writeString(vcf, """
                ##fileformat=VCFv4.2
                ##contig=<ID=chr1,length=100>
                ##contig=<ID=chr2,length=100>
                #CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO
                chr1\t5\t.\tA\tG\t50\tPASS\t.
                chr1\t10\t.\tA\tT\t50\tPASS\t.
                chr2\t3\t.\tC\tG\t50\tPASS\t.
                """);
        Path sequential = tempDir.resolve("ordered-sequential.vcf.gz");
        Path parallel = tempDir.resolve("ordered-parallel.vcf.gz");
        run("hts.vcf.filter", Map.of(
                "input", vcf,
                "threads", 1,
                "output", sequential,
                "indexType", "NONE"
        ));
        run("hts.vcf.filter", Map.of(
                "input", vcf,
                "threads", 3,
                "output", parallel,
                "indexType", "NONE"
        ));
        assertEquals(
                java.util.Arrays.toString(Files.readAllBytes(sequential)),
                java.util.Arrays.toString(Files.readAllBytes(parallel))
        );

        Path indexed = tempDir.resolve("ordered-indexed.vcf.gz");
        run("hts.vcf.filter", Map.of(
                "input", vcf,
                "threads", 3,
                "output", indexed,
                "indexType", "AUTO"
        ));
        try (VCFFileReader reader = new VCFFileReader(indexed, false)) {
            assertTrue(reader.isQueryable());
            try (var variants = reader.query("chr2", 1, 5)) {
                assertTrue(variants.hasNext());
                assertEquals("chr2", variants.next().getContig());
            }
        }
    }

    @Test
    void readsAndFiltersBcfInput() throws Exception {
        Path sourceVcf = writeIndexableVcf();
        Path sourceBcf = tempDir.resolve("source.bcf");
        TaskResult created = run("hts.vcf.filter", Map.of(
                "input", sourceVcf,
                "output", sourceBcf,
                "indexType", "NONE"
        ));
        assertEquals(2L, created.metrics().get("inputVariants"));

        Path bcfStats = tempDir.resolve("bcf-stats.tsv");
        TaskResult stats = run("hts.vcf.stats", Map.of(
                "input", sourceBcf,
                "output", bcfStats
        ));
        assertEquals(2L, stats.metrics().get("variants"));
        assertEquals(1L, stats.metrics().get("snps"));
        assertEquals(1L, stats.metrics().get("indels"));

        Path indexedVcf = tempDir.resolve("bcf-filtered.vcf.gz");
        TaskResult filtered = run("hts.vcf.filter", Map.of(
                "input", sourceBcf,
                "output", indexedVcf,
                "minQual", 20.0,
                "indexType", "AUTO"
        ));
        assertEquals(1L, filtered.metrics().get("retained"));
        assertEquals("TBI", filtered.metrics().get("indexType"));
        try (VCFFileReader reader = new VCFFileReader(indexedVcf, false)) {
            assertTrue(reader.isQueryable());
        }
    }

    @Test
    void parallelVcfFilterMatchesSequentialOutputAcrossChunks() throws Exception {
        Path vcf = tempDir.resolve("parallel-filter.vcf");
        StringBuilder content = new StringBuilder("""
                ##fileformat=VCFv4.2
                ##contig=<ID=chr1,length=100000>
                ##FORMAT=<ID=GT,Number=1,Type=String,Description="Genotype">
                #CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\tS1\tS2
                """);
        for (int index = 0; index < 1_000; index++) {
            content.append("chr1\t").append(index + 1).append("\t.\t")
                    .append(index % 4 == 0 ? "AT" : "A").append('\t')
                    .append(index % 4 == 0 ? "A" : "G").append('\t')
                    .append(index % 5 == 0 ? 10 : 50).append("\tPASS\t.\tGT\t")
                    .append(index % 7 == 0 ? "./." : "0/1").append('\t')
                    .append(index % 11 == 0 ? "./." : "0/0").append('\n');
        }
        Files.writeString(vcf, content);
        Path sequential = tempDir.resolve("parallel-filter-sequential.vcf");
        Path parallel = tempDir.resolve("parallel-filter-workers.vcf");

        run("hts.vcf.filter", Map.of(
                "input", vcf,
                "threads", 1,
                "minQual", 20.0,
                "maxMissing", 0.5,
                "output", sequential,
                "indexType", "NONE"
        ));
        TaskResult result = run("hts.vcf.filter", Map.of(
                "input", vcf,
                "threads", 3,
                "minQual", 20.0,
                "maxMissing", 0.5,
                "output", parallel,
                "indexType", "NONE"
        ));

        assertEquals(Files.readString(sequential), Files.readString(parallel));
        assertEquals(1_000L, result.metrics().get("inputVariants"));
        assertTrue((long) result.metrics().get("retained") < 1_000L);
        assertEquals(3, result.metrics().get("threads"));
        assertEquals(true, result.metrics().get("parallelRecords"));
    }

    @Test
    void indexedVcfOutputRequiresACompleteSequenceDictionary() throws Exception {
        Path vcf = tempDir.resolve("no-dictionary.vcf");
        Files.writeString(vcf, """
                ##fileformat=VCFv4.2
                #CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO
                chr1\t10\t.\tA\tG\t50\tPASS\t.
                """);
        Path output = tempDir.resolve("no-dictionary.vcf.gz");
        OpenToolException exception = assertThrows(OpenToolException.class, () -> run(
                "hts.vcf.filter",
                Map.of(
                        "input", vcf,
                        "output", output,
                        "indexType", "AUTO"
                )
        ));
        assertEquals("VCF_INDEX_DICTIONARY_REQUIRED", exception.code());
        assertFalse(Files.exists(output));
        assertFalse(Files.exists(output.resolveSibling("no-dictionary.vcf.gz.tbi")));

        Path mismatched = tempDir.resolve("mismatched-contig.vcf");
        Files.writeString(mismatched, """
                ##fileformat=VCFv4.2
                ##contig=<ID=chr1,length=100>
                #CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO
                chr2\t10\t.\tA\tG\t50\tPASS\t.
                """);
        Path mismatchedOutput = tempDir.resolve("mismatched.vcf.gz");
        OpenToolException mismatch = assertThrows(OpenToolException.class, () -> run(
                "hts.vcf.filter",
                Map.of(
                        "input", mismatched,
                        "output", mismatchedOutput,
                        "indexType", "AUTO"
                )
        ));
        assertEquals("VCF_INDEX_CONTIG_MISSING", mismatch.code());
        assertFalse(Files.exists(mismatchedOutput));
    }

    @Test
    void indexedVcfOutputRejectsUnsortedVariants() throws Exception {
        Path vcf = tempDir.resolve("unsorted.vcf");
        Files.writeString(vcf, """
                ##fileformat=VCFv4.2
                ##contig=<ID=chr1,length=100>
                #CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO
                chr1\t20\t.\tA\tG\t50\tPASS\t.
                chr1\t10\t.\tC\tT\t50\tPASS\t.
                """);
        OpenToolException exception = assertThrows(OpenToolException.class, () -> run(
                "hts.vcf.filter",
                Map.of(
                        "input", vcf,
                        "output", tempDir.resolve("unsorted.vcf.gz"),
                        "indexType", "AUTO"
                )
        ));
        assertEquals("VCF_NOT_SORTED", exception.code());
    }

    private void createIndexedBam(Path output) throws Exception {
        createIndexedBam(output, """
                @HD\tVN:1.6\tSO:coordinate
                @SQ\tSN:chr1\tLN:100
                r1\t0\tchr1\t1\t60\t4M\t*\t0\t0\tACGT\tIIII
                r2\t0\tchr1\t20\t60\t3M\t*\t0\t0\tACG\tIII
                """);
    }

    private void createIndexedBam(Path output, String samText) throws Exception {
        Path sam = output.resolveSibling(output.getFileName() + "-source.sam");
        Files.writeString(sam, samText);
        try (SamReader reader = SamReaderFactory.makeDefault().open(sam);
             SAMFileWriter writer = new SAMFileWriterFactory().setCreateIndex(true)
                     .makeWriter(reader.getFileHeader(), true, output, null)) {
            for (SAMRecord record : reader) {
                writer.addAlignment(record);
            }
        }
    }

    private List<String> records(Path bam) throws Exception {
        List<String> records = new ArrayList<>();
        try (SamReader reader = SamReaderFactory.makeDefault().open(bam)) {
            for (SAMRecord record : reader) {
                records.add(record.getSAMString());
            }
        }
        return records;
    }

    private List<String> readNames(Path bam) throws Exception {
        List<String> names = new ArrayList<>();
        try (SamReader reader = SamReaderFactory.makeDefault().open(bam)) {
            for (SAMRecord record : reader) {
                names.add(record.getReadName());
            }
        }
        return names;
    }

    private boolean hasTemporaryBamFiles(Path root) throws Exception {
        if (!Files.isDirectory(root)) {
            return false;
        }
        try (var files = Files.walk(root)) {
            return files.anyMatch(path -> path.getFileName().toString().endsWith(".bam"));
        }
    }

    private Path writeIndexableVcf() throws Exception {
        Path vcf = tempDir.resolve("indexable.vcf");
        Files.writeString(vcf, """
                ##fileformat=VCFv4.2
                ##contig=<ID=chr1,length=100>
                #CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO
                chr1\t10\t.\tA\tG\t50\tPASS\t.
                chr1\t20\t.\tAT\tA\t10\tPASS\t.
                """);
        return vcf;
    }

    private void assertNoAtomicSidecars() throws Exception {
        try (var files = Files.walk(tempDir)) {
            assertTrue(files.noneMatch(path ->
                    path.getFileName().toString().startsWith(".opentools-")));
        }
    }

    private TaskResult run(String id, Map<String, Object> supplied) throws Exception {
        return run(id, supplied, new CancellationToken());
    }

    private TaskResult run(
            String id,
            Map<String, Object> supplied,
            CancellationToken cancellationToken
    ) throws Exception {
        OpenTool tool = HtsTools.tools().stream()
                .filter(candidate -> candidate.descriptor().id().equals(id))
                .findFirst()
                .orElseThrow();
        Map<String, Object> parameters = new LinkedHashMap<>();
        for (ToolParameter parameter : tool.descriptor().parameters()) {
            if (parameter.defaultValue() != null) {
                parameters.put(parameter.id(), parameter.defaultValue());
            }
        }
        parameters.putAll(supplied);
        ToolRequest request = new ToolRequest(id, parameters);
        var validation = tool.validate(request);
        assertTrue(validation.valid(), validation.issues().toString());
        Path output = Files.createDirectories(tempDir.resolve("out"));
        return tool.execute(request, new TaskContext("hts", tempDir, output, output,
                ignored -> { }, ignored -> { }, cancellationToken));
    }
}
