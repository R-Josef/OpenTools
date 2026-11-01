package io.opentools.formats.fasta;

import io.opentools.api.tool.OpenToolException;
import io.opentools.formats.Compression;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class FastaSequenceStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void indexesUniformUncompressedFastaAndCrossesLineBoundaries() throws Exception {
        Path input = tempDir.resolve("genome.fa");
        Files.writeString(input, ">chr1 description\r\nACGT\r\nTGCA\r\nAAAA\r\nCC\r\n");

        FastaSequenceStore store = FastaSequenceStore.load(input);

        assertEquals("indexed", store.storageMode());
        assertEquals(14, store.length("chr1"));
        assertEquals("GTTGCAAAAACC", store.subsequence("chr1", 3, 14));
    }

    @Test
    void fallsBackToInMemoryForGzipAndVariableWrapping() throws Exception {
        Path gzip = tempDir.resolve("genome.fa.gz");
        try (var writer = Compression.createWriter(gzip)) {
            writer.write(">chr1\nACGT\nTGCA\n");
        }
        FastaSequenceStore compressed = FastaSequenceStore.load(gzip);
        assertEquals("in-memory", compressed.storageMode());
        assertEquals("CGTT", compressed.subsequence("chr1", 2, 5));

        Path variable = tempDir.resolve("variable.fa");
        Files.writeString(variable, ">chr1\nACGT\nTGC\nAAAA\n");
        FastaSequenceStore loaded = FastaSequenceStore.load(variable);
        assertEquals("in-memory", loaded.storageMode());
        assertEquals("CGTTGCAAAA", loaded.subsequence("chr1", 2, 11));
    }

    @Test
    void rejectsDuplicateSequenceIdsInIndexedInput() throws Exception {
        Path input = tempDir.resolve("duplicates.fa");
        Files.writeString(input, ">chr1\nACGT\n>chr1\nTGCA\n");

        OpenToolException exception = assertThrows(
                OpenToolException.class,
                () -> FastaSequenceStore.load(input)
        );

        assertEquals("DUPLICATE_SEQUENCE_ID", exception.code());
    }
}
