package io.opentools.external.comparative;

import io.opentools.api.tool.CancellationToken;
import io.opentools.api.tool.OpenTool;
import io.opentools.api.tool.TaskContext;
import io.opentools.api.tool.TaskResult;
import io.opentools.api.tool.ToolParameter;
import io.opentools.core.tool.ToolRegistry;
import io.opentools.api.tool.ToolRequest;
import io.opentools.external.ExternalTools;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ComparativeToolsTest {
    @TempDir
    Path tempDir;

    @Test
    void importsCollinearityAndCalculatesKaks() throws Exception {
        Path collinearity = tempDir.resolve("input.collinearity");
        Files.writeString(collinearity, """
                ## Alignment 1: score=100.0 e_value=1e-20 N=2 chr1&chr2 plus
                  0-  0: geneA geneX
                  1-  1: geneB geneY
                """);
        Path imported = tempDir.resolve("collinearity.tsv");
        TaskResult importResult = run("mcscan.import", Map.of(
                "input", collinearity, "output", imported));
        assertEquals(2L, importResult.metrics().get("pairs"));
        assertTrue(Files.readString(imported).contains("geneA\tgeneX"));

        Path cds = tempDir.resolve("pairs.fa");
        Files.writeString(cds, ">a\nATGGCCTAA\n>b\nATGGCTTAA\n");
        Path kaks = tempDir.resolve("kaks.tsv");
        TaskResult kaksResult = run("kaks.calculate", Map.of(
                "input", cds, "output", kaks));
        assertEquals(1L, kaksResult.metrics().get("pairs"));
        assertTrue(Files.readString(kaks).contains("a\tb"));
    }

    @Test
    void preparesMcscanxGffWithFeatureIdentifiers() throws Exception {
        Path annotation = tempDir.resolve("annotation.gff3");
        Files.writeString(annotation, """
                ##gff-version 3
                chr1	test	gene	1	100	.	+	.	ID=g1
                chr1	test	gene	201	300	.	+	.	ID=g2
                chr2	test	gene	401	500	.	+	.	ID=g3
                """);
        Path blast = tempDir.resolve("hits.blast");
        Files.writeString(blast, "g1\tg3\t90\t100\t1e-20\t80\n");
        String prefix = tempDir.resolve("mcscan").toString();

        run("mcscan.input", Map.of(
                "annotation", annotation,
                "blast", blast,
                "outputPrefix", prefix
        ));

        List<String> lines = Files.readAllLines(Path.of(prefix + ".gff"));
        assertEquals(List.of(
                "chr1\tg1\t1\t100",
                "chr1\tg2\t201\t300",
                "chr2\tg3\t401\t500"
        ), lines);
    }

    @Test
    void registersF6ComparativeTasks() {
        ToolRegistry registry = new ToolRegistry();
        ExternalTools.register(registry);
        for (String id : List.of("alignment.iqtree", "mcscan.input", "mcscan.run",
                "mcscan.import", "kaks.calculate")) {
            assertTrue(registry.find(id).isPresent(), id);
        }
    }

    @Test
    void parallelKaksMatchesSequentialOutput() throws Exception {
        Path cds = tempDir.resolve("parallel-pairs.fa");
        StringBuilder content = new StringBuilder();
        for (int index = 0; index < 24; index++) {
            content.append(">a_").append(index).append('\n')
                    .append(index % 2 == 0 ? "ATGGCCTAA" : "ATGAAATGA").append('\n')
                    .append(">b_").append(index).append('\n')
                    .append(index % 2 == 0 ? "ATGGCTTAA" : "ATGGAGTGA").append('\n');
        }
        Files.writeString(cds, content);
        Path sequential = tempDir.resolve("kaks-sequential.tsv");
        Path parallel = tempDir.resolve("kaks-parallel.tsv");

        run("kaks.calculate", Map.of(
                "input", cds, "threads", 1, "output", sequential));
        TaskResult result = run("kaks.calculate", Map.of(
                "input", cds, "threads", 3, "output", parallel));

        assertEquals(Files.readString(sequential), Files.readString(parallel));
        assertEquals(3, result.metrics().get("threads"));
    }

    private TaskResult run(String id, Map<String, Object> supplied) throws Exception {
        ToolRegistry registry = new ToolRegistry();
        ExternalTools.register(registry);
        OpenTool tool = registry.find(id).orElseThrow();
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
        return tool.execute(request, new TaskContext("comparative", tempDir, output, output,
                ignored -> { }, ignored -> { }, new CancellationToken()));
    }
}
