package io.opentools.analysis.expression;

import io.opentools.analysis.BuiltInAnalysis;
import io.opentools.api.tool.CancellationToken;
import io.opentools.api.tool.OpenTool;
import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolResult;
import io.opentools.api.tool.ToolParameter;
import io.opentools.core.tool.ToolRegistry;
import io.opentools.api.tool.ToolRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ExpressionToolsTest {
    @TempDir
    Path tempDir;

    @Test
    void normalizesCountsToTpm() throws Exception {
        Path input = writeCounts();
        Path lengths = tempDir.resolve("lengths.tsv");
        Files.writeString(lengths, "gene\tlength\nA\t1000\nB\t2000\nC\t500\nD\t800\n");
        Path output = tempDir.resolve("tpm.tsv");
        ToolResult result = run("expression.normalize", Map.of(
                "input", input,
                "geneLengths", lengths,
                "method", "TPM",
                "output", output
        ));
        assertEquals("TPM", result.metrics().get("method"));
        List<String> rows = Files.readAllLines(output);
        double[] totals = new double[3];
        for (int row = 1; row < rows.size(); row++) {
            String[] fields = rows.get(row).split("\t");
            for (int sample = 0; sample < 3; sample++) {
                totals[sample] += Double.parseDouble(fields[sample + 1]);
            }
        }
        assertEquals(1_000_000, totals[0], 1.0);
        assertEquals(1_000_000, totals[1], 1.0);
        assertEquals(1_000_000, totals[2], 1.0);
    }

    @Test
    void calculatesCorrelationAndPcaCoordinates() throws Exception {
        Path input = writeCounts();
        Path correlation = tempDir.resolve("correlation.tsv");
        ToolResult correlationResult = run("expression.correlation", Map.of(
                "input", input,
                "method", "PEARSON",
                "output", correlation
        ));
        assertEquals(3, correlationResult.metrics().get("samples"));
        String matrix = Files.readString(correlation);
        assertTrue(matrix.contains("S1\t1.0"));

        Path groups = tempDir.resolve("groups.tsv");
        Files.writeString(groups, "sample\tgroup\nS1\tA\nS2\tA\nS3\tB\n");
        Path pca = tempDir.resolve("pca.tsv");
        ToolResult pcaResult = run("expression.pca", Map.of(
                "input", input,
                "groups", groups,
                "components", 2,
                "output", pca
        ));
        assertEquals(2, pcaResult.metrics().get("components"));
        String coordinates = Files.readString(pca);
        assertTrue(coordinates.contains("S1\tA\t"));
        assertTrue(coordinates.contains("PC1\tPC2"));
    }

    @Test
    void preparesDifferentialLongTable() throws Exception {
        Path input = writeCounts();
        Path groups = tempDir.resolve("groups.tsv");
        Files.writeString(groups, "sample\tgroup\nS1\tA\nS2\tA\nS3\tB\n");
        Path output = tempDir.resolve("differential.tsv");
        ToolResult result = run("expression.differential-input", Map.of(
                "input", input,
                "groups", groups,
                "output", output
        ));
        assertEquals(12L, result.metrics().get("observations"));
        assertEquals(13, Files.readAllLines(output).size());
        assertTrue(Files.readString(output).contains("A\tS1\tA\t"));
    }

    @Test
    void parallelCorrelationMatchesSequentialMatrix() throws Exception {
        Path input = writeCounts();
        Path sequential = tempDir.resolve("correlation-sequential.tsv");
        Path parallel = tempDir.resolve("correlation-parallel.tsv");
        run("expression.correlation", Map.of(
                "input", input, "threads", 1, "output", sequential));
        ToolResult result = run("expression.correlation", Map.of(
                "input", input, "threads", 3, "output", parallel));

        assertEquals(Files.readString(sequential), Files.readString(parallel));
        assertEquals(3, result.metrics().get("threads"));
    }

    @Test
    void runsWelchAndMannWhitneyDifferentialTests() throws Exception {
        Path input = tempDir.resolve("diff-matrix.tsv");
        Files.writeString(input, """
                gene	S1	S2	S3	S4
                A	10	12	30	32
                B	20	22	21	23
                C	5	7	15	17
                """);
        Path groups = tempDir.resolve("diff-groups.tsv");
        Files.writeString(groups, "sample\tgroup\nS1\tA\nS2\tA\nS3\tB\nS4\tB\n");

        Path welch = tempDir.resolve("welch.tsv");
        ToolResult welchResult = run("expression.differential", Map.of(
                "input", input,
                "groups", groups,
                "test", "WELCH_T",
                "output", welch
        ));
        assertEquals(3, welchResult.metrics().get("genes"));
        List<String> rows = Files.readAllLines(welch);
        assertEquals(4, rows.size());
        assertTrue(rows.get(0).contains("adjusted_p_value"));
        String geneA = rows.stream().filter(row -> row.startsWith("A\t")).findFirst().orElseThrow();
        String[] fields = geneA.split("\t");
        assertTrue(Double.parseDouble(fields[9]) <= 1.0);
        assertTrue(Double.parseDouble(fields[10]) >= Double.parseDouble(fields[9]));
        assertTrue(Double.parseDouble(fields[7]) < 0);

        Path mann = tempDir.resolve("mann.tsv");
        run("expression.differential", Map.of(
                "input", input,
                "groups", groups,
                "test", "MANN_WHITNEY",
                "output", mann
        ));
        String mannText = Files.readString(mann);
        assertTrue(mannText.contains("MANN_WHITNEY"));
    }

    private Path writeCounts() throws Exception {
        Path input = tempDir.resolve("counts.tsv");
        Files.writeString(input, """
                gene	S1	S2	S3
                A	10	20	5
                B	30	10	15
                C	0	5	25
                D	20	20	10
                """);
        return input;
    }

    private ToolResult run(String id, Map<String, Object> supplied) throws Exception {
        ToolRegistry registry = new ToolRegistry();
        BuiltInAnalysis.register(registry);
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
        return tool.execute(request, new ToolExecutionContext("expression", tempDir, output, output,
                ignored -> { }, ignored -> { }, new CancellationToken()));
    }
}
