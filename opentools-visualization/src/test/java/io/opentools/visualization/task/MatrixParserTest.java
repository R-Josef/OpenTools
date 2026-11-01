package io.opentools.visualization.task;

import io.opentools.api.tool.CancellationToken;
import io.opentools.api.tool.OpenToolException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MatrixParserTest {
    @TempDir
    Path tempDir;

    @Test
    void recordsMissingAndInvalidDiagnosticsAndSortsDeterministically() throws Exception {
        Path input = tempDir.resolve("matrix.tsv");
        Files.writeString(input, """
                gene\tS2\tS1
                zeta\t3\tNA
                alpha\tbad\t1
                mid\t2\t2
                """);
        MatrixParser.Config config = new MatrixParser.Config(
                input,
                "AUTO",
                "GRAY",
                "AS_MISSING",
                "NAME_ASC",
                "NAME_ASC",
                "NONE",
                100,
                100
        );

        MatrixParser.MatrixResult result = new MatrixParser().parse(config, new CancellationToken());

        assertEquals(java.util.List.of("alpha", "mid", "zeta"), result.data().rowLabels());
        assertEquals(java.util.List.of("S1", "S2"), result.data().columnLabels());
        assertNull(result.data().values().getFirst().get(1));
        assertEquals(1, result.data().missingValues());
        assertEquals(1, result.data().invalidValues());
        assertEquals(2, result.data().diagnostics().size());
        assertTrue(result.data().diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.messageKey().equals("visual.diagnostic.invalid_as_missing")));
    }

    @Test
    void stopsOnInvalidValueInStrictMode() throws Exception {
        Path input = tempDir.resolve("strict.tsv");
        Files.writeString(input, "gene\tS1\na\tnot-a-number\n");
        MatrixParser.Config config = new MatrixParser.Config(
                input, "AUTO", "ERROR", "ERROR", "ORIGINAL", "ORIGINAL", "NONE", 10, 10
        );

        OpenToolException error = assertThrows(
                OpenToolException.class,
                () -> new MatrixParser().parse(config, new CancellationToken())
        );
        assertEquals("VIS_MATRIX_INVALID_VALUE", error.code());
        assertTrue(error.getMessage().contains("line 2"));
    }

    @Test
    void rowZScoreKeepsMissingCellsMissing() throws Exception {
        Path input = tempDir.resolve("zscore.tsv");
        Files.writeString(input, "gene\tS1\tS2\tS3\na\t1\t2\tNA\n");
        MatrixParser.Config config = new MatrixParser.Config(
                input, "AUTO", "GRAY", "AS_MISSING", "ORIGINAL", "ORIGINAL", "ROW_ZSCORE", 10, 10
        );

        MatrixParser.MatrixResult result = new MatrixParser().parse(config, new CancellationToken());

        assertNull(result.data().values().getFirst().get(2));
        assertTrue(result.data().values().getFirst().get(0) < 0);
    }
}
