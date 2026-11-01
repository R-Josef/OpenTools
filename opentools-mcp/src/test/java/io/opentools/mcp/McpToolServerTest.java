package io.opentools.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class McpToolServerTest {
    @TempDir
    Path tempDir;

    private AutomationRuntime runtime;
    private McpToolServer server;

    @BeforeEach
    void setUp() throws Exception {
        runtime = AutomationRuntime.open(
                tempDir.resolve("project"),
                tempDir.resolve("app"),
                List.of(tempDir)
        );
        server = new McpToolServer(runtime.tasks());
    }

    @AfterEach
    void tearDown() throws Exception {
        runtime.close();
    }

    @Test
    void exposesInitializationAndToolCatalog() {
        ObjectNode initialize = request("initialize", Map.of(
                "protocolVersion", "2025-06-18",
                "clientInfo", Map.of("name", "test", "version", "1")
        ));
        JsonNode initializeResponse = server.handle(initialize);

        assertEquals("2.0", initializeResponse.path("jsonrpc").asText());
        assertEquals("2025-06-18", initializeResponse.path("result").path("protocolVersion").asText());
        assertTrue(initializeResponse.path("result").path("capabilities").path("tools").has("listChanged"));

        JsonNode tools = server.handle(request("tools/list", Map.of()));
        assertTrue(tools.path("result").path("tools").toString().contains("opentools_submit_job"));
        assertTrue(tools.path("result").path("tools").toString().contains("opentools_read_result"));
        String described = callTool("opentools_describe_tool", Map.of("toolId", "fasta.filter")).toString();
        assertTrue(described.contains("fasta.filter"));
        assertTrue(described.contains("includeRegex"));
    }

    @Test
    void runsFastaFilterAndSubsequenceThroughMcp() throws Exception {
        Path fasta = runtime.project().root().resolve("analysis.fa");
        Files.writeString(fasta, ">alpha\nACGTACGT\n>beta\nTTTTAAAA\n");

        String filterRun = submitAndWait("fasta.filter", Map.of(
                "input", "analysis.fa",
                "includeIds", "alpha",
                "output", "filtered.fa"
        ));
        String regionRun = submitAndWait("fasta.subsequence", Map.of(
                "input", "analysis.fa",
                "regions", "alpha\t2\t5\t-\talpha_region",
                "output", "region.fa"
        ));
        JsonNode filterResult = callTool("opentools_read_result", Map.of("runId", filterRun));
        JsonNode regionResult = callTool("opentools_read_result", Map.of("runId", regionRun));

        assertFalse(filterResult.path("isError").asBoolean(), filterResult.toString());
        assertFalse(regionResult.path("isError").asBoolean(), regionResult.toString());
        assertTrue(Files.isRegularFile(runtime.project().root().resolve("filtered.fa")));
        assertTrue(Files.isRegularFile(runtime.project().root().resolve("region.fa")));
        assertTrue(filterResult.path("structuredContent").path("data").path("result").toString()
                .contains("fasta.filter"));
        assertTrue(regionResult.path("structuredContent").path("data").path("result").toString()
                .contains("fasta.subsequence"));
    }

    @Test
    void runsAnnotationFilterThroughMcp() throws Exception {
        Path annotation = runtime.project().root().resolve("annotation.gff3");
        Files.writeString(annotation, """
                ##gff-version 3
                chr1\ttest\tgene\t1\t10\t.\t+\t.\tID=g1
                chr1\ttest\texon\t1\t5\t.\t+\t.\tID=e1;Parent=g1
                """);
        String runId = submitAndWait("gxf.filter", Map.of(
                "input", "annotation.gff3",
                "types", "exon",
                "output", "exons.gff3"
        ));
        JsonNode result = callTool("opentools_read_result", Map.of("runId", runId));
        assertFalse(result.path("isError").asBoolean(), result.toString());
        assertTrue(Files.readString(runtime.project().root().resolve("exons.gff3")).contains("ID=e1"));
        assertTrue(result.path("structuredContent").path("data").path("result").toString()
                .contains("gxf.filter"));
    }

    @Test
    void validatesRejectsOverwriteAndCompletesFastaAndGffJobs() throws Exception {
        Path fasta = runtime.project().root().resolve("sample.fa");
        Files.writeString(fasta, ">a\nACGT\n>b\nGG\n");
        Path gff = runtime.project().root().resolve("sample.gff3");
        Files.writeString(gff, """
                ##gff-version 3
                chr1\ttest\tgene\t1\t100\t.\t+\t.\tID=g1
                """);

        JsonNode invalid = callTool("opentools_validate_inputs", Map.of(
                "toolId", "fasta.stats",
                "parameters", Map.of("input", "missing.fa")
        ));
        assertFalse(invalid.path("isError").asBoolean());
        assertFalse(invalid.path("structuredContent").path("data").path("valid").asBoolean());

        JsonNode overwrite = callTool("opentools_validate_inputs", Map.of(
                "toolId", "fasta.stats",
                "parameters", Map.of("input", fasta.toString(), "overwrite", true)
        ));
        assertTrue(overwrite.path("structuredContent").path("data").path("issues").toString()
                .contains("OVERWRITE_NOT_ALLOWED"));

        String fastaRun = submitAndWait("fasta.stats", Map.of("input", "sample.fa"));
        String gffRun = submitAndWait("gxf.inspect", Map.of("input", "sample.gff3"));
        JsonNode fastaResult = callTool("opentools_read_result", Map.of("runId", fastaRun));
        JsonNode gffResult = callTool("opentools_read_result", Map.of("runId", gffRun));

        assertFalse(fastaResult.path("isError").asBoolean());
        assertTrue(fastaResult.path("structuredContent").path("data").path("result").toString()
                .contains("fasta.stats"));
        assertFalse(gffResult.path("isError").asBoolean());
        assertTrue(gffResult.path("structuredContent").path("data").path("result").toString()
                .contains("gxf.inspect"));
    }

    @Test
    void returnsStructuredToolErrors() {
        JsonNode response = callTool("opentools_describe_tool", Map.of("toolId", "missing.tool"));

        assertTrue(response.path("isError").asBoolean());
        assertEquals("TOOL_NOT_FOUND", response.path("structuredContent").path("error").path("code").asText());
    }

    private String submitAndWait(String toolId, Map<String, Object> parameters) {
        JsonNode submitted = callTool("opentools_submit_job", Map.of(
                "toolId", toolId,
                "parameters", parameters
        ));
        assertFalse(submitted.path("isError").asBoolean(), submitted.toString());
        String runId = submitted.path("structuredContent").path("data").path("runId").asText();
        JsonNode status = callTool("opentools_job_status", Map.of(
                "runId", runId,
                "waitSeconds", 10
        ));
        assertEquals("SUCCEEDED", status.path("structuredContent").path("data").path("status").asText());
        return runId;
    }

    private JsonNode callTool(String name, Map<String, Object> arguments) {
        JsonNode response = server.handle(request("tools/call", Map.of(
                "name", name,
                "arguments", arguments
        )));
        return response.path("result");
    }

    private ObjectNode request(String method, Map<String, Object> params) {
        ObjectNode request = JsonSupport.MAPPER.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", method);
        request.put("method", method);
        request.set("params", JsonSupport.MAPPER.valueToTree(params));
        return request;
    }
}
