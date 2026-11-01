package io.opentools.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class McpHttpServerTest {
    @TempDir
    Path tempDir;

    @Test
    void requiresBearerTokenAndRejectsForeignOrigins() throws Exception {
        try (AutomationRuntime runtime = AutomationRuntime.open(
                tempDir.resolve("project"),
                tempDir.resolve("app"),
                List.of()
        );
             McpHttpServer server = new McpHttpServer(
                     new McpToolServer(runtime.tasks()),
                     "127.0.0.1",
                     0,
                     "test-token-with-32-characters"
             )) {
            server.start();
            String body = """
                    {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"test","version":"1"}}}
                    """;
            HttpClient client = HttpClient.newHttpClient();
            URI endpoint = URI.create(server.endpoint());

            HttpResponse<String> unauthorized = client.send(
                    HttpRequest.newBuilder(endpoint)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertEquals(403, unauthorized.statusCode());

            HttpResponse<String> authorized = client.send(
                    HttpRequest.newBuilder(endpoint)
                            .header("Authorization", "Bearer test-token-with-32-characters")
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertEquals(200, authorized.statusCode());
            assertEquals("no-store", authorized.headers().firstValue("Cache-Control").orElse(""));
            assertEquals("nosniff", authorized.headers().firstValue("X-Content-Type-Options").orElse(""));

            HttpResponse<String> foreignOrigin = client.send(
                    HttpRequest.newBuilder(endpoint)
                            .header("Authorization", "Bearer test-token-with-32-characters")
                            .header("Origin", "https://example.org")
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertEquals(403, foreignOrigin.statusCode());
            assertNull(foreignOrigin.headers().firstValue("Access-Control-Allow-Origin").orElse(null));

            HttpResponse<String> wrongContentType = client.send(
                    HttpRequest.newBuilder(endpoint)
                            .header("Authorization", "Bearer test-token-with-32-characters")
                            .header("Content-Type", "text/plain")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertEquals(415, wrongContentType.statusCode());
        }
    }

    @Test
    void rejectsExplicitLowEntropyBearerTokens() throws Exception {
        try (AutomationRuntime runtime = AutomationRuntime.open(
                tempDir.resolve("short-token-project"),
                tempDir.resolve("short-token-app"),
                List.of()
        )) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new McpHttpServer(
                            new McpToolServer(runtime.tasks()),
                            "127.0.0.1",
                            0,
                            "short-token"
                    )
            );
        }
    }
}
