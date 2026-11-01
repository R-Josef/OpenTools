package io.opentools.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TaskAutomationServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void validatesAndRunsFastaAndGffJobsThroughOneService() throws Exception {
        Path projectRoot = tempDir.resolve("project");
        try (AutomationRuntime runtime = AutomationRuntime.open(
                projectRoot,
                tempDir.resolve("app"),
                List.of()
        )) {
            Path fasta = projectRoot.resolve("sample.fa");
            Files.writeString(fasta, ">a\nACGT\n");
            Path gff = projectRoot.resolve("sample.gff3");
            Files.writeString(gff, """
                    ##gff-version 3
                    chr1\ttest\tgene\t1\t100\t.\t+\t.\tID=g1
                    """);

            var fastaValidation = runtime.tasks().validate(
                    "fasta.stats",
                    Map.of("input", fasta.toString())
            );
            var gffValidation = runtime.tasks().validate(
                    "gxf.inspect",
                    Map.of("input", gff.toString())
            );
            assertTrue(fastaValidation.valid());
            assertTrue(gffValidation.valid());

            var batch = runtime.tasks().runBatch(new TaskAutomationService.BatchSpec(
                    false,
                    List.of(
                            new TaskAutomationService.BatchItem(
                                    "fasta.stats",
                                    Map.of("input", fasta.toString()),
                                    false
                            ),
                            new TaskAutomationService.BatchItem(
                                    "gxf.inspect",
                                    Map.of("input", gff.toString()),
                                    false
                            )
                    )
            ));
            assertEquals(2, batch.get("completed"));
            assertFalse((Boolean) batch.get("stoppedOnError"));
            assertEquals(List.of("SUCCEEDED", "SUCCEEDED"),
                    ((List<?>) batch.get("jobs")).stream()
                            .map(item -> ((Map<?, ?>) item).get("status"))
                            .toList());
        }
    }

    @Test
    void everyRegisteredTaskExposesAParameterSchema() throws Exception {
        try (AutomationRuntime runtime = AutomationRuntime.open(
                tempDir.resolve("schema-project"),
                tempDir.resolve("schema-app"),
                List.of()
        )) {
            assertTrue(runtime.registry().list().size() >= 11);
            runtime.registry().list().forEach(tool -> {
                var schema = io.opentools.core.automation.ToolContracts.parameterSchema(tool);
                assertEquals("object", schema.get("type"));
                assertTrue(schema.containsKey("properties"));
            });
        }
    }
}
