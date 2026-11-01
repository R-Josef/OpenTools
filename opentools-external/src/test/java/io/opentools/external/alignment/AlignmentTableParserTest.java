package io.opentools.external.alignment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class AlignmentTableParserTest {
    @TempDir
    Path tempDir;

    @Test
    void selectsBestHitForEachQuery() throws Exception {
        Path table = tempDir.resolve("hits.tsv");
        Files.writeString(table, """
                q1\ts1\t90\t100\t10\t0\t1\t100\t1\t100\t1e-20\t200\t100\t100
                q1\ts2\t99\t100\t1\t0\t1\t100\t1\t100\t1e-30\t250\t100\t100
                q2\ts3\t80\t50\t10\t0\t1\t50\t1\t50\t1e-5\t80\t50\t50
                """);

        AlignmentTableSummary summary = new AlignmentTableParser().parse(table);

        assertEquals(3, summary.totalHits());
        assertEquals(2, summary.queryCount());
        assertEquals("s2", summary.bestHits().stream()
                .filter(hit -> hit.query().equals("q1"))
                .findFirst().orElseThrow().subject());
    }
}
