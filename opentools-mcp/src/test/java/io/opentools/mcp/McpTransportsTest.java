package io.opentools.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class McpTransportsTest {
    @TempDir
    Path tempDir;

    @Test
    void servesNewlineDelimitedJsonOverStdio() throws Exception {
        try (AutomationRuntime runtime = runtime()) {
            McpToolServer protocol = new McpToolServer(runtime.toolAutomation());
            String input = """
                    {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18"}}
                    {"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}
                    """;
            ByteArrayOutputStream output = new ByteArrayOutputStream();

            new McpStdioServer(protocol).run(
                    new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                    output
            );

            String[] lines = output.toString(StandardCharsets.UTF_8).strip().split("\\R");
            assertEquals(2, lines.length);
            assertTrue(lines[0].contains("serverInfo"));
            assertTrue(lines[1].contains("opentools_search_tools"));
        }
    }

    @Test
    void httpRequiresTokenAndRestrictsOrigins() throws Exception {
        try (AutomationRuntime runtime = runtime();
             McpHttpServer server = new McpHttpServer(
                     new McpToolServer(runtime.toolAutomation()),
                     "127.0.0.1",
                     0,
                     "test-token-with-32-characters"
             )) {
            server.start();
            HttpClient client = HttpClient.newHttpClient();
            String body = """
                    {"jsonrpc":"2.0","id":1,"method":"ping","params":{}}
                    """;
            HttpRequest unauthorized = HttpRequest.newBuilder(URI.create(server.endpoint()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            assertEquals(403, client.send(unauthorized, HttpResponse.BodyHandlers.ofString()).statusCode());

            HttpRequest allowed = HttpRequest.newBuilder(URI.create(server.endpoint()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer test-token-with-32-characters")
                    .header("Origin", "http://localhost:3000")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = client.send(allowed, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"result\""));

            HttpRequest badOrigin = HttpRequest.newBuilder(URI.create(server.endpoint()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer test-token-with-32-characters")
                    .header("Origin", "https://example.com")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            assertEquals(403, client.send(badOrigin, HttpResponse.BodyHandlers.ofString()).statusCode());
        }
    }

    @Test
    void rejectsNonLoopbackBindAddress() throws Exception {
        try (AutomationRuntime runtime = runtime()) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new McpHttpServer(
                            new McpToolServer(runtime.toolAutomation()),
                            "0.0.0.0",
                            0,
                            "test-token-with-32-characters"
                    )
            );
        }
    }

    private AutomationRuntime runtime() throws Exception {
        return AutomationRuntime.open(
                tempDir.resolve("project-" + System.nanoTime()),
                tempDir.resolve("app"),
                List.of(tempDir)
        );
    }
}
