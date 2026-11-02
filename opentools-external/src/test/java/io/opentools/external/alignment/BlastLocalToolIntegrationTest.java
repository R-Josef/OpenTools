package io.opentools.external.alignment;

import io.opentools.api.tool.CancellationToken;
import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolRequest;
import io.opentools.external.ExternalRuntime;
import io.opentools.external.process.ExternalProcessException;
import io.opentools.external.tool.ExecutableLocator;
import io.opentools.external.tool.ExternalToolSpecs;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class BlastLocalToolIntegrationTest {
    @TempDir
    Path tempDir;

    @Test
    void runsSmallLocalBlastWhenAvailable() throws Exception {
        ExecutableLocator locator = new ExecutableLocator(ExternalRuntime.appHome());
        Assumptions.assumeTrue(locator.resolve(ExternalToolSpecs.blast("blastn"), null, null).available());
        Assumptions.assumeTrue(locator.resolve(ExternalToolSpecs.MAKEBLASTDB, null, null).available());

        Path query = tempDir.resolve("query.fa");
        Path subject = tempDir.resolve("subject.fa");
        Path output = tempDir.resolve("hits.tsv");
        Path cache = tempDir.resolve("blast-cache");
        Files.createDirectories(cache);
        String sequence = "GATTACAGTCGATCGTAGCTAGGCTAACGTTAGCGATCGTACGATCGATGCTAGCATCGATCGTAGCTAGCTACGATCGATCGA";
        Files.writeString(query, ">q1\n" + sequence + "\n");
        Files.writeString(subject, ">s1\n" + sequence + "\n");
        Path run = tempDir.resolve("run");
        List<String> logs = new ArrayList<>();
        ToolExecutionContext context = new ToolExecutionContext(
                "blast-test",
                tempDir,
                run,
                run.resolve("outputs"),
                logs::add,
                ignored -> { },
                new CancellationToken()
        );

        var result = new BlastLocalTool().execute(
                new ToolRequest("alignment.blast.local", parameters(
                        "query", query.toString(),
                        "subject", subject.toString(),
                        "program", "AUTO",
                        "evalue", 10.0,
                        "maxTargetSeqs", 5,
                        "threads", 1,
                        "timeoutMinutes", 2,
                        "cacheEnabled", true,
                        "refreshDatabase", true,
                        "cacheDirectory", cache.toString(),
                        "extraArgs", "-dust no",
                        "output", output.toString()
                )),
                context
        );

        assertTrue(Files.isRegularFile(output));
        assertTrue(
                ((Number) result.metrics().get("totalHits")).longValue() >= 1,
                String.join("\n", logs) + "\nOUTPUT:\n" + Files.readString(output)
        );
        assertFalse((Boolean) result.metrics().get("cacheHit"));
        assertFalse((Boolean) result.metrics().get("databaseReused"));

        Path secondOutput = tempDir.resolve("hits-cached.tsv");
        var cached = new BlastLocalTool().execute(
                new ToolRequest("alignment.blast.local", parameters(
                        "query", query.toString(),
                        "subject", subject.toString(),
                        "program", "AUTO",
                        "evalue", 10.0,
                        "maxTargetSeqs", 5,
                        "threads", 1,
                        "timeoutMinutes", 2,
                        "cacheEnabled", true,
                        "cacheDirectory", cache.toString(),
                        "extraArgs", "-dust no",
                        "output", secondOutput.toString()
                )),
                context
        );
        assertTrue((Boolean) cached.metrics().get("cacheHit"));
        assertTrue((Boolean) cached.metrics().get("databaseReused"));
        assertFalse((Boolean) cached.metrics().get("makeblastdbInvoked"));
    }

    @Test
    void failedBlastDoesNotPublishFormalOutput() throws Exception {
        ExecutableLocator locator = new ExecutableLocator(ExternalRuntime.appHome());
        Assumptions.assumeTrue(locator.resolve(ExternalToolSpecs.blast("blastn"), null, null).available());
        Assumptions.assumeTrue(locator.resolve(ExternalToolSpecs.MAKEBLASTDB, null, null).available());

        Path query = tempDir.resolve("fail-query.fa");
        Path subject = tempDir.resolve("fail-subject.fa");
        Path output = tempDir.resolve("must-not-exist.tsv");
        String sequence = "GATTACAGTCGATCGTAGCTAGGCTAACGTTAGCGATCGTACGATCGATGCTAGCATCGATCGTAGCTAGCTACGATCGATCGA";
        Files.writeString(query, ">q1\n" + sequence + "\n");
        Files.writeString(subject, ">s1\n" + sequence + "\n");
        Path run = tempDir.resolve("failed-run");
        ToolExecutionContext context = new ToolExecutionContext(
                "blast-failed-test",
                tempDir,
                run,
                run.resolve("outputs"),
                ignored -> { },
                ignored -> { },
                new CancellationToken()
        );

        assertThrows(ExternalProcessException.class, () -> new BlastLocalTool().execute(
                new ToolRequest("alignment.blast.local", Map.of(
                        "query", query.toString(),
                        "subject", subject.toString(),
                        "program", "BLASTN",
                        "evalue", 10.0,
                        "maxTargetSeqs", 5,
                        "threads", 1,
                        "timeoutMinutes", 2,
                        "cacheEnabled", false,
                        "extraArgs", "-definitely_invalid_blast_option",
                        "output", output.toString()
                )),
                context
        ));
        assertFalse(Files.exists(output));
    }

    private static Map<String, Object> parameters(Object... values) {
        if (values.length % 2 != 0) {
            throw new IllegalArgumentException("Parameter values must be key/value pairs");
        }
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }
}
