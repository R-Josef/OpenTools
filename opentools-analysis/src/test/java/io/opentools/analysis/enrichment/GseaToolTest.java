package io.opentools.analysis.enrichment;

import io.opentools.analysis.BuiltInAnalysis;
import io.opentools.api.tool.CancellationToken;
import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolRequest;
import io.opentools.core.tool.ToolRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GseaToolTest {
    @TempDir
    Path tempDir;

    @Test
    void scoresRankedGeneSetsWithDeterministicPermutations() throws Exception {
        Path ranking = tempDir.resolve("ranked.tsv");
        StringBuilder data = new StringBuilder("gene\tscore\n");
        for (int index = 1; index <= 20; index++) {
            data.append("g").append(index).append('\t').append(21 - index).append('\n');
        }
        Files.writeString(ranking, data.toString());
        Path sets = tempDir.resolve("sets.gmt");
        Files.writeString(sets, "S1\ttop\tg1\tg2\tg3\nS2\tmiddle\tg10\tg11\tg12\nS3\tmixed\tg1\tg20\n");
        Path output = tempDir.resolve("gsea.tsv");

        ToolRegistry registry = new ToolRegistry();
        BuiltInAnalysis.register(registry);
        Path out = Files.createDirectories(tempDir.resolve("out"));
        ToolExecutionContext context = new ToolExecutionContext("gsea", tempDir, out, out,
                ignored -> { }, ignored -> { }, new CancellationToken());
        var tool = registry.find("go.gsea").orElseThrow();
        ToolRequest request = new ToolRequest("go.gsea", Map.of(
                "ranking", ranking,
                "geneSets", sets,
                "minSetSize", 3,
                "maxSetSize", 5,
                "permutations", 20,
                "seed", 7,
                "output", output
        ));
        var validation = tool.validate(request);
        assertTrue(validation.valid(), validation.issues().toString());
        var result = tool.execute(request, context);

        assertEquals(2, result.metrics().get("sets"));
        String text = Files.readString(output);
        assertTrue(text.contains("set_id\tset_size\tenrichment_score\tnes\tp_value\tadjusted_p_value"));
        assertTrue(text.contains("S1\t3\t"));
        assertTrue(text.contains("S2\t3\t"));
    }
}
