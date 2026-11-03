package io.opentools.external.alignment;

import com.sun.net.httpserver.HttpServer;
import io.opentools.api.tool.CancellationToken;
import io.opentools.api.tool.OpenToolException;
import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ToolResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RemoteBlastToolTest {
    @TempDir
    Path tempDir;

    @Test
    void uploadsPollsAndWritesTabularHits() throws Exception {
        AtomicInteger gets = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/Blast.cgi", exchange -> {
            String body;
            if ("POST".equals(exchange.getRequestMethod())) {
                body = "RID = TEST-RID\nRTOE = 1\n";
            } else if (gets.incrementAndGet() == 1) {
                body = "Status=WAITING\n";
            } else {
                body = "# BLASTN 2.16.0+\n"
                        + "q1\tsubject1\t99.0\t100\t1\t0\t1\t100\t5\t104\t1e-50\t180\n";
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            Path query = tempDir.resolve("query.fa");
            Files.writeString(query, ">q1\nACGTACGTACGT\n");
            Path output = tempDir.resolve("remote.tsv");
            ToolResult result = new RemoteBlastTool().execute(
                    new ToolRequest("alignment.blast.remote", Map.of(
                            "query", query.toString(),
                            "program", "blastn",
                            "database", "nt",
                            "maxHits", 10,
                            "allowUpload", true,
                            "timeoutSeconds", 20,
                            "pollSeconds", 2,
                            "baseUrl", "http://127.0.0.1:" + server.getAddress().getPort() + "/Blast.cgi",
                            "output", output.toString()
                    )),
                    context()
            );
            assertTrue(Files.readString(output).contains("q1"));
            assertEquals(1, result.metrics().get("hits"));
            assertEquals("TEST-RID", result.metrics().get("rid"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void requiresExplicitUploadConfirmation() {
        Path query = tempDir.resolve("query.fa");
        ToolRequest request = new ToolRequest("alignment.blast.remote", Map.of(
                "query", query.toString(),
                "program", "blastn",
                "database", "nt",
                "baseUrl", "https://blast.ncbi.nlm.nih.gov/Blast.cgi"
        ));
        var validation = new RemoteBlastTool().validate(request);
        assertFalse(validation.valid());
        assertTrue(validation.issues().stream().anyMatch(issue -> issue.field().equals("allowUpload")));
    }

    @Test
    void rejectsPlainHttpOutsideLoopback() {
        Path query = tempDir.resolve("query.fa");
        ToolRequest request = new ToolRequest("alignment.blast.remote", Map.of(
                "query", query.toString(),
                "program", "blastn",
                "database", "nt",
                "allowUpload", true,
                "baseUrl", "http://example.org/Blast.cgi"
        ));
        var validation = new RemoteBlastTool().validate(request);
        assertFalse(validation.valid());
        assertTrue(validation.issues().stream().anyMatch(issue -> issue.field().equals("baseUrl")));
    }

    @Test
    void reportsHttpFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/Blast.cgi", exchange -> {
            byte[] bytes = "upstream failure".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(503, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            Path query = tempDir.resolve("query.fa");
            Files.writeString(query, ">q1\nACGT\n");
            OpenToolException error = assertThrows(OpenToolException.class, () ->
                    new RemoteBlastTool().execute(
                            new ToolRequest("alignment.blast.remote", Map.of(
                                    "query", query.toString(),
                                    "program", "blastn",
                                    "database", "nt",
                                    "allowUpload", true,
                                    "timeoutSeconds", 20,
                                    "pollSeconds", 2,
                                    "baseUrl", "http://127.0.0.1:" + server.getAddress().getPort() + "/Blast.cgi"
                            )),
                            context()
                    ));
            assertEquals("REMOTE_BLAST_HTTP_ERROR", error.code());
        } finally {
            server.stop(0);
        }
    }

    private ToolExecutionContext context() {
        Path run = tempDir.resolve("run-" + System.nanoTime());
        return new ToolExecutionContext("run", tempDir, run, run,
                ignored -> { }, ignored -> { }, new CancellationToken());
    }
}
