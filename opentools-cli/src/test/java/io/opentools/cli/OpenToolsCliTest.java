package io.opentools.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OpenToolsCliTest {
    @TempDir
    Path tempDir;

    @Test
    void reportsCurrentProjectVersion() {
        Outcome outcome = execute("--version");

        assertEquals(0, outcome.code());
        assertTrue(outcome.out().contains("OpenTools 0.8.0-SNAPSHOT"));
    }

    @Test
    void listsBuiltInToolsAsJson() {
        Outcome outcome = execute("--project", tempDir.toString(), "tools", "list", "--json");

        assertEquals(0, outcome.code());
        var json = read(outcome);
        assertEquals(true, json.path("ok").asBoolean());
        assertEquals("1.0", json.path("apiVersion").asText());
        assertTrue(json.path("error").isNull());
        assertTrue(outcome.out().contains("fasta.stats"));
        assertTrue(outcome.out().contains("fasta.extract"));
        assertTrue(outcome.out().contains("fasta.filter"));
        assertTrue(outcome.out().contains("fasta.transform"));
        assertTrue(outcome.out().contains("fasta.subsequence"));
        assertTrue(outcome.out().contains("fasta.rename"));
        assertTrue(outcome.out().contains("fasta.convert"));
        assertTrue(outcome.out().contains("sequence.orf"));
        assertTrue(outcome.out().contains("fastq.stats"));
        assertTrue(outcome.out().contains("fastq.filter"));
        assertTrue(outcome.out().contains("gxf.filter"));
        assertTrue(outcome.out().contains("gxf.to-bed"));
        assertTrue(outcome.out().contains("bed.stats"));
        assertTrue(outcome.out().contains("alignment.blast.xml-to-table"));
        assertTrue(outcome.out().contains("alignment.hmmer.search"));
        assertTrue(outcome.out().contains("annotation.eggnog.import"));
        assertTrue(outcome.out().contains("alignment.muscle"));
        assertTrue(outcome.out().contains("alignment.trim"));
        assertTrue(outcome.out().contains("expression.normalize"));
        assertTrue(outcome.out().contains("expression.pca"));
        assertTrue(outcome.out().contains("plot.pca"));
        assertTrue(outcome.out().contains("plot.volcano"));
        assertTrue(outcome.out().contains("go.enrich"));
        assertTrue(outcome.out().contains("kegg.enrich"));
        assertTrue(outcome.out().contains("plot.enrichment-bar"));
        assertTrue(outcome.out().contains("tree.newick.summary"));
        assertTrue(outcome.out().contains("alignment.iqtree"));
        assertTrue(outcome.out().contains("mcscan.import"));
        assertTrue(outcome.out().contains("plot.collinearity"));
        assertTrue(outcome.out().contains("kaks.calculate"));
        assertTrue(outcome.out().contains("plot.seqlogo"));
        assertTrue(outcome.out().contains("plot.circos"));
        assertTrue(outcome.out().contains("plot.efp"));
        assertTrue(outcome.out().contains("hts.sam-stats"));
        assertTrue(outcome.out().contains("hts.bam.subset"));
        assertTrue(outcome.out().contains("hts.vcf.stats"));
        assertTrue(outcome.out().contains("bed.coverage"));
        assertTrue(outcome.out().contains("table.filter"));
        assertTrue(outcome.out().contains("table.join"));
        assertTrue(outcome.out().contains("table.pivot"));
        assertTrue(outcome.out().contains("visual.heatmap"));
        assertTrue(outcome.out().contains("visual.venn"));
        assertTrue(outcome.out().contains("visual.gene-structure"));
    }

    @Test
    void runsFastaAnalysisToolsThroughTheCliTaskContract() throws Exception {
        Path input = tempDir.resolve("analysis.fa");
        Files.writeString(input, ">alpha\nACGTACGT\n>beta\nTTTTAAAA\n");
        Path filtered = tempDir.resolve("filtered.fa");
        Path transformed = tempDir.resolve("transformed.fa");
        Path regions = tempDir.resolve("regions.fa");

        Outcome filter = execute(
                "--project", tempDir.toString(),
                "run", "fasta.filter",
                "--param", "input=" + input,
                "--param", "includeIds=alpha",
                "--param", "output=" + filtered,
                "--json"
        );
        Outcome transform = execute(
                "--project", tempDir.toString(),
                "run", "fasta.transform",
                "--param", "input=" + input,
                "--param", "direction=REVERSE_COMPLEMENT",
                "--param", "output=" + transformed,
                "--json"
        );
        Outcome subsequence = execute(
                "--project", tempDir.toString(),
                "run", "fasta.subsequence",
                "--param", "input=" + input,
                "--param", "regions=alpha\t2\t5\t-\talpha_region",
                "--param", "output=" + regions,
                "--json"
        );
        Outcome schema = execute(
                "--project", tempDir.toString(),
                "tools", "describe", "fasta.subsequence", "--schema"
        );

        assertEquals(0, filter.code(), filter.err());
        assertEquals(0, transform.code(), transform.err());
        assertEquals(0, subsequence.code(), subsequence.err());
        assertTrue(filter.out().contains("SUCCEEDED"));
        assertTrue(transform.out().contains("SUCCEEDED"));
        assertTrue(subsequence.out().contains("SUCCEEDED"));
        assertTrue(Files.readString(filtered).contains(">alpha"));
        assertTrue(Files.readString(transformed).contains(">alpha"));
        assertTrue(Files.readString(regions).contains(">alpha_region"));
        assertEquals(0, schema.code(), schema.err());
        assertTrue(schema.out().contains("\"regions\""));
    }

    @Test
    void runsFastaStatsAndPersistsHistory() throws Exception {
        Path input = tempDir.resolve("sample.fa");
        Files.writeString(input, ">a\nACGT\n>b\nGG\n");

        Outcome run = execute(
                "--project", tempDir.toString(),
                "run", "fasta.stats",
                "--param", "input=" + input,
                "--json"
        );
        Outcome jobs = execute("--project", tempDir.toString(), "jobs", "list", "--json");
        String sourceRunId = new ObjectMapper().readTree(jobs.out()).path("data").get(0).path("runId").asText();
        Outcome retry = execute("--project", tempDir.toString(), "jobs", "retry", sourceRunId, "--json");

        assertEquals(0, run.code(), run.err());
        assertTrue(run.out().contains("SUCCEEDED"));
        assertTrue(jobs.out().contains("fasta.stats"));
        assertEquals(0, retry.code(), retry.err());
    }

    @Test
    void invalidInputReturnsValidationExitCode() {
        Outcome outcome = execute(
                "--project", tempDir.toString(),
                "run", "fasta.stats",
                "--param", "input=" + tempDir.resolve("missing.fa")
        );

        assertEquals(2, outcome.code());
        assertTrue(outcome.err().contains("does not exist"));
    }

    @Test
    void supportsExplicitSimplifiedChineseLanguage() {
        String appHome = Path.of(System.getProperty("user.dir")).toAbsolutePath().getParent().toString();
        Outcome outcome = execute(
                "--app-home", appHome,
                "--language", "zh_CN",
                "--project", tempDir.toString(),
                "tools", "list"
        );

        assertEquals(0, outcome.code());
        assertTrue(outcome.out().contains("FASTA 统计"));
    }

    @Test
    void runsHeatmapThroughTheSameCliTaskContract() throws Exception {
        Path input = tempDir.resolve("heatmap.tsv");
        Files.writeString(input, "gene\tA\tB\na\t1\t2\nb\t3\t4\n");

        Outcome outcome = execute(
                "--project", tempDir.toString(),
                "run", "visual.heatmap",
                "--param", "input=" + input,
                "--param", "exportFormats=PNG_SVG",
                "--json"
        );

        assertEquals(0, outcome.code(), outcome.err());
        assertTrue(outcome.out().contains("SUCCEEDED"));
        assertTrue(outcome.out().contains("visual.heatmap"));
    }

    @Test
    void installsRunsAndPersistentlyDisablesPlugin() throws Exception {
        Path appHome = tempDir.resolve("app");
        Path plugins = Files.createDirectories(appHome.resolve("plugins"));
        String fixture = "io/opentools/cli/fixture/CliTestPlugin.class";
        byte[] classBytes;
        try (var input = getClass().getClassLoader().getResourceAsStream(fixture)) {
            classBytes = input == null ? new byte[0] : input.readAllBytes();
        }
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(plugins.resolve("cli-test.jar")))) {
            addZipEntry(zip, "META-INF/opentools-plugin.json", """
                    {
                      "id": "cli.test",
                      "name": "CLI Test Plugin",
                      "version": "1.0",
                      "apiVersion": "1.0",
                      "mainClass": "io.opentools.cli.fixture.CliTestPlugin",
                      "dependencies": [],
                      "requestedCapabilities": ["tool", "page", "data"]
                    }
                    """.getBytes(StandardCharsets.UTF_8));
            addZipEntry(zip, fixture, classBytes);
            addNestedClassEntries(zip, "io/opentools/cli/fixture/", "CliTestPlugin$");
        }

        Outcome listed = execute(
                "--app-home", appHome.toString(),
                "--project", tempDir.toString(),
                "plugins", "list", "--json"
        );
        Outcome toolsBeforeEnable = execute(
                "--app-home", appHome.toString(),
                "--project", tempDir.toString(),
                "tools", "list", "--json"
        );
        Outcome enabled = execute(
                "--app-home", appHome.toString(),
                "--project", tempDir.toString(),
                "plugins", "enable", "cli.test", "--json"
        );
        Outcome tools = execute(
                "--app-home", appHome.toString(),
                "--project", tempDir.toString(),
                "tools", "list", "--json"
        );
        Outcome run = execute(
                "--app-home", appHome.toString(),
                "--project", tempDir.toString(),
                "run", "plugin.cli.test", "--json"
        );
        Outcome disabled = execute(
                "--app-home", appHome.toString(),
                "--project", tempDir.toString(),
                "plugins", "disable", "cli.test", "--json"
        );
        Outcome toolsAfterDisable = execute(
                "--app-home", appHome.toString(),
                "--project", tempDir.toString(),
                "tools", "list", "--json"
        );

        assertEquals(0, listed.code(), listed.err());
        assertTrue(listed.out().contains("\"DISABLED\""));
        assertTrue(!toolsBeforeEnable.out().contains("plugin.cli.test"));
        assertEquals(0, enabled.code(), enabled.err());
        assertTrue(enabled.out().contains("\"ENABLED\""));
        assertTrue(tools.out().contains("plugin.cli.test"));
        assertEquals(0, run.code(), run.err());
        assertTrue(run.out().contains("SUCCEEDED"));
        assertEquals(0, disabled.code(), disabled.err());
        assertTrue(disabled.out().contains("\"DISABLED\""));
        assertTrue(!toolsAfterDisable.out().contains("plugin.cli.test"));
        assertTrue(Files.isRegularFile(plugins.resolve("plugin-state.json")));
    }

    @Test
    void emitsStableJsonErrorEnvelopeAndExitCodes() throws Exception {
        Outcome missingTool = execute(
                "--project", tempDir.toString(),
                "run", "missing.tool",
                "--json"
        );
        Path empty = tempDir.resolve("empty.fa");
        Files.writeString(empty, "");
        Outcome failedJob = execute(
                "--project", tempDir.toString(),
                "run", "fasta.stats",
                "--param", "input=" + empty,
                "--json"
        );

        var missingJson = new ObjectMapper().readTree(missingTool.out());
        var failedJson = new ObjectMapper().readTree(failedJob.out());
        String failedRunId = failedJson.path("error").path("details").path("job").path("runId").asText();
        Outcome failedStatus = execute(
                "--project", tempDir.toString(),
                "jobs", "status", failedRunId,
                "--json"
        );
        var failedStatusJson = new ObjectMapper().readTree(failedStatus.out());
        assertEquals(ExitCodes.INPUT, missingTool.code());
        assertEquals(false, missingJson.path("ok").asBoolean());
        assertEquals("TOOL_NOT_FOUND", missingJson.path("error").path("code").asText());
        assertEquals(ExitCodes.EXECUTION, failedJob.code());
        assertEquals(false, failedJson.path("ok").asBoolean());
        assertEquals("EMPTY_FASTA", failedJson.path("error").path("code").asText());
        assertEquals("FAILED", failedJson.path("error").path("details").path("job").path("status").asText());
        assertEquals(ExitCodes.EXECUTION, failedStatus.code());
        assertEquals(false, failedStatusJson.path("ok").asBoolean());
        assertEquals("EMPTY_FASTA", failedStatusJson.path("error").path("code").asText());
        assertTrue(missingTool.err().isBlank());
        assertTrue(failedJob.err().isBlank());
    }

    @Test
    void emitsValidationAndMalformedParameterErrorsAsJson() throws Exception {
        Outcome validation = execute(
                "--project", tempDir.toString(),
                "run", "fasta.stats",
                "--param", "input=" + tempDir.resolve("missing.fa"),
                "--json"
        );
        Outcome malformedParameter = execute(
                "--project", tempDir.toString(),
                "run", "fasta.stats",
                "--param", "not-a-pair",
                "--json"
        );

        var validationJson = read(validation);
        var parameterJson = read(malformedParameter);
        assertEquals(ExitCodes.INPUT, validation.code());
        assertEquals("VALIDATION_FAILED", validationJson.path("error").path("code").asText());
        assertTrue(validationJson.path("error").path("issues").size() > 0);
        assertTrue(validation.err().isBlank());
        assertEquals(ExitCodes.INPUT, malformedParameter.code());
        assertEquals("INVALID_ARGUMENTS", parameterJson.path("error").path("code").asText());
        assertTrue(malformedParameter.err().isBlank());
    }

    @Test
    void emitsToolSchemaAndRunsJsonBatch() throws Exception {
        Path fasta = tempDir.resolve("batch.fa");
        Files.writeString(fasta, ">a\nACGT\n");
        Path batch = tempDir.resolve("batch.json");
        Files.writeString(batch, """
                {
                  "continueOnError": false,
                  "jobs": [
                    {
                      "toolId": "fasta.stats",
                      "parameters": {"input": "%s"},
                      "dryRun": false
                    }
                  ]
                }
                """.formatted(fasta.toString().replace("\\", "\\\\")));

        Outcome schema = execute(
                "--project", tempDir.toString(),
                "tools", "describe", "fasta.stats", "--schema"
        );
        Outcome batchOutcome = execute(
                "--project", tempDir.toString(),
                "batch", "run", batch.toString(),
                "--json"
        );

        var schemaJson = new ObjectMapper().readTree(schema.out());
        var batchJson = new ObjectMapper().readTree(batchOutcome.out());
        assertEquals(0, schema.code(), schema.err());
        assertTrue(schemaJson.path("data").path("parameters").path("properties").has("input"));
        assertEquals(0, batchOutcome.code(), batchOutcome.err());
        assertEquals(1, batchJson.path("data").path("completed").asInt());
        assertEquals("SUCCEEDED", batchJson.path("data").path("jobs").get(0)
                .path("status").asText());
    }

    private Outcome execute(String... args) {
        StringWriter stdout = new StringWriter();
        StringWriter stderr = new StringWriter();
        int code = OpenToolsCli.execute(
                args,
                new PrintWriter(stdout, true),
                new PrintWriter(stderr, true)
        );
        return new Outcome(code, stdout.toString(), stderr.toString());
    }

    private static com.fasterxml.jackson.databind.JsonNode read(Outcome outcome) {
        try {
            return new ObjectMapper().readTree(outcome.out());
        } catch (Exception ex) {
            throw new AssertionError("Expected JSON output: " + outcome.out(), ex);
        }
    }

    private void addNestedClassEntries(ZipOutputStream zip, String prefix, String classPrefix) throws Exception {
        Path classesDirectory = Path.of(
                getClass().getProtectionDomain().getCodeSource().getLocation().toURI()
        );
        try (var files = Files.list(classesDirectory.resolve(prefix))) {
            for (Path classFile : files.filter(path -> path.getFileName().toString().startsWith(classPrefix)).toList()) {
                addZipEntry(zip, prefix + classFile.getFileName(), Files.readAllBytes(classFile));
            }
        }
    }

    private static void addZipEntry(ZipOutputStream zip, String name, byte[] bytes) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }

    private record Outcome(int code, String out, String err) {
    }
}
