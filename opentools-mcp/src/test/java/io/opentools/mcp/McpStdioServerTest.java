package io.opentools.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class McpStdioServerTest {
    @TempDir
    Path tempDir;

    @Test
    void servesLineDelimitedJsonRpcAndNotifications() throws Exception {
        try (AutomationRuntime runtime = AutomationRuntime.open(
                tempDir.resolve("project"),
                tempDir.resolve("app"),
                List.of()
        )) {
            String input = """
                    {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"test","version":"1"}}}
                    {"jsonrpc":"2.0","method":"notifications/initialized"}
                    {"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}
                    """;
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            new McpStdioServer(new McpToolServer(runtime.toolAutomation())).run(
                    new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                    output
            );

            String[] lines = output.toString(StandardCharsets.UTF_8).strip().split("\\R");
            assertEquals(2, lines.length);
            JsonNode initialize = JsonSupport.MAPPER.readTree(lines[0]);
            JsonNode tools = JsonSupport.MAPPER.readTree(lines[1]);
            assertEquals("2025-06-18", initialize.path("result").path("protocolVersion").asText());
            assertTrue(tools.path("result").path("tools").isArray());
        }
    }
}
