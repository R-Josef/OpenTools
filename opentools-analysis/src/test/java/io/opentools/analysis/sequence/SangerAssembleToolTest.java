package io.opentools.analysis.sequence;

import io.opentools.api.tool.CancellationToken;
import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ToolResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SangerAssembleToolTest {
    @TempDir
    Path tempDir;

    @Test
    void assemblesOverlappingReadsAndAnchorsToReference() throws Exception {
        String reference = randomSequence(240);
        Path referencePath = tempDir.resolve("reference.fa");
        Files.writeString(referencePath, ">ref\n" + reference + "\n");

        String vector = "TCGATGTTACTCCTGGTCGAGTGTAAAATTCCAACCTGA";
        String forward = vector + reference.substring(0, 150);
        String reverse = vector + SangerAssembleTool.reverseComplement(
                reference.substring(90, reference.length()));
        Path reads = tempDir.resolve("reads.fastq");
        Files.writeString(reads, fastq("forward", forward) + fastq("reverse", reverse));

        Path output = Files.createDirectories(tempDir.resolve("out"));
        ToolResult result = execute(referencePath, reads, output);

        assertEquals(2, result.metrics().get("reads"));
        assertEquals(1, result.metrics().get("contigs"));
        assertTrue(Files.isRegularFile(output.resolve("assembly.fa")));
        assertTrue(Files.isRegularFile(output.resolve("corrected.fa")));
        String corrected = Files.readString(output.resolve("corrected.fa"));
        assertTrue(corrected.replace("\n", "").contains(reference));
        String variants = Files.readString(output.resolve("variants.tsv"));
        assertEquals(1, variants.lines().count());
        assertTrue(Files.readString(output.resolve("provenance.bed")).contains("READ_CONSENSUS"));
    }

    @Test
    void marksReferenceOnlyGapsInProvenance() throws Exception {
        String reference = randomSequence(200);
        Path referencePath = tempDir.resolve("gap-reference.fa");
        Files.writeString(referencePath, ">ref\n" + reference + "\n");
        String read = reference.substring(0, 100);
        Path reads = tempDir.resolve("gap.fastq");
        Files.writeString(reads, fastq("only-forward", read));
        Path output = Files.createDirectories(tempDir.resolve("gap-out"));

        execute(referencePath, reads, output);

        String provenance = Files.readString(output.resolve("provenance.bed"));
        assertTrue(provenance.contains("REFERENCE_FILL"));
        String corrected = Files.readString(output.resolve("corrected.fa"));
        assertTrue(corrected.replace("\n", "").contains(reference));
    }

    @Test
    void callsHeterozygousBasesAsIupac() throws Exception {
        String reference = randomSequence(160);
        Path referencePath = tempDir.resolve("hetero-reference.fa");
        Files.writeString(referencePath, ">ref\n" + reference + "\n");
        String read = reference.substring(0, 120);
        char replacement = read.charAt(60) == 'A' ? 'C' : 'A';
        String alternate = read.substring(0, 60) + replacement + read.substring(61);
        Path reads = tempDir.resolve("hetero.fastq");
        Files.writeString(reads, fastq("read1", read) + fastq("read2", alternate));
        Path output = Files.createDirectories(tempDir.resolve("hetero-out"));

        execute(referencePath, reads, output);

        String variants = Files.readString(output.resolve("variants.tsv"));
        assertTrue(variants.contains("HETEROZYGOUS"));
        String corrected = Files.readString(output.resolve("corrected.fa"));
        assertTrue(corrected.replace("\n", "").matches(".*[RYSWKM].*"));
    }

    @Test
    void reportsInsertionsFromReferenceAlignment() throws Exception {
        String reference = randomSequence(160);
        Path referencePath = tempDir.resolve("indel-reference.fa");
        Files.writeString(referencePath, ">ref\n" + reference + "\n");
        String read = reference.substring(0, 80) + "AAA" + reference.substring(80);
        Path reads = tempDir.resolve("indel.fastq");
        Files.writeString(reads, fastq("read", read));
        Path output = Files.createDirectories(tempDir.resolve("indel-out"));

        execute(referencePath, reads, output);

        String indels = Files.readString(output.resolve("indels.tsv"));
        assertTrue(indels.contains("INSERTION"));
        assertTrue(indels.contains("AAA"));
        assertEquals(reference.substring(0, 80) + "AAA" + reference.substring(80),
                fastaSequence(output.resolve("consensus_with_indels.fa")));
    }

    @Test
    void reportsDeletionsAndMarksDeletionProvenance() throws Exception {
        String reference = randomSequence(160);
        Path referencePath = tempDir.resolve("deletion-reference.fa");
        Files.writeString(referencePath, ">ref\n" + reference + "\n");
        String read = reference.substring(0, 80) + reference.substring(83);
        Path reads = tempDir.resolve("deletion.fastq");
        Files.writeString(reads, fastq("read", read));
        Path output = Files.createDirectories(tempDir.resolve("deletion-out"));

        execute(referencePath, reads, output);

        assertTrue(Files.readString(output.resolve("indels.tsv")).contains("DELETION"));
        assertTrue(Files.readString(output.resolve("provenance.bed")).contains("DELETION"));
        assertEquals(reference.substring(0, 80) + reference.substring(83),
                fastaSequence(output.resolve("consensus_with_indels.fa")));
    }

    private ToolResult execute(Path reference, Path reads, Path output) throws Exception {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("readsDirectory", reads.getParent().toString());
        parameters.put("reference", reference.toString());
        parameters.put("minOverlap", 30);
        parameters.put("minIdentity", 0.85);
        parameters.put("trimQuality", 20);
        parameters.put("trimWindow", 8);
        parameters.put("minReadLength", 50);
        parameters.put("consensusThreshold", 0.6);
        parameters.put("heterozygousThreshold", 0.25);
        parameters.put("minHeterozygousCoverage", 2);
        parameters.put("outputDirectory", output.toString());
        parameters.put("overwrite", true);
        ToolExecutionContext context = new ToolExecutionContext(
                "sanger-test", tempDir, output, output,
                ignored -> { }, ignored -> { }, new CancellationToken());
        return new SangerAssembleTool().execute(
                new ToolRequest("sanger.assemble", parameters), context);
    }

    private static String fastaSequence(Path path) throws Exception {
        return Files.readString(path).lines()
                .filter(line -> !line.startsWith(">"))
                .collect(java.util.stream.Collectors.joining());
    }

    private static String fastq(String name, String sequence) {
        return "@" + name + "\n" + sequence + "\n+\n" + "I".repeat(sequence.length()) + "\n";
    }

    private static String randomSequence(int length) {
        String bases = "ACGT";
        StringBuilder text = new StringBuilder(length);
        java.util.Random random = new java.util.Random(17);
        for (int index = 0; index < length; index++) {
            text.append(bases.charAt(random.nextInt(4)));
        }
        return text.toString();
    }
}
