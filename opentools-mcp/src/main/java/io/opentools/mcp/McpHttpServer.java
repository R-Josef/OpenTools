package io.opentools.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;

public final class McpHttpServer implements AutoCloseable {
    private static final int MAX_BODY_BYTES = 4 * 1024 * 1024;

    private final McpToolServer protocol;
    private final String token;
    private final HttpServer server;
    private final ExecutorService executor = Executors.newFixedThreadPool(
            Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors())),
            Thread.ofPlatform().name("opentools-mcp-http-", 0).factory()
    );
    private final String host;
    private final CountDownLatch stopped = new CountDownLatch(1);

    public McpHttpServer(McpToolServer protocol, String host, int port, String token) throws IOException {
        this.protocol = protocol;
        if (token != null && !token.isBlank() && token.length() < 16) {
            throw new IllegalArgumentException("Explicit MCP HTTP token must contain at least 16 characters");
        }
        this.token = token == null || token.isBlank() ? McpSecurity.generateToken() : token;
        InetAddress address = InetAddress.getByName(host);
        if (!address.isLoopbackAddress()) {
            throw new IllegalArgumentException("MCP HTTP server must bind to a loopback address");
        }
        this.host = address.getHostAddress();
        this.server = HttpServer.create(new InetSocketAddress(address, port), 0);
        this.server.createContext("/mcp", this::handleMcp);
        this.server.createContext("/health", this::handleHealth);
        this.server.setExecutor(executor);
    }

    public void start() {
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public String endpoint() {
        String hostPart = host.contains(":") ? "[" + host + "]" : host;
        return "http://" + hostPart + ":" + port() + "/mcp";
    }

    public String token() {
        return token;
    }

    public void await() throws InterruptedException {
        stopped.await();
    }

    private void handleMcp(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
        if (method.equals("OPTIONS")) {
            if (!allowedOrigin(exchange)) {
                respond(exchange, 403, "{\"error\":\"Disallowed origin\"}");
                return;
            }
            addCors(exchange);
            respond(exchange, 204, "");
            return;
        }
        if (!method.equals("POST")) {
            respond(exchange, 405, "{\"error\":\"Use POST /mcp\"}");
            return;
        }
        if (!authorized(exchange) || !allowedOrigin(exchange)) {
            respond(exchange, 403, "{\"error\":\"Unauthorized or disallowed origin\"}");
            return;
        }
        addCors(exchange);
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("application/json")) {
            respond(exchange, 415, "{\"error\":\"Content-Type must be application/json\"}");
            return;
        }
        byte[] body = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) {
            respond(exchange, 413, "{\"error\":\"Request body exceeds limit\"}");
            return;
        }
        JsonNode response;
        try {
            JsonNode request = JsonSupport.MAPPER.readTree(body);
            if (request == null) {
                respond(exchange, 400, "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32700,\"message\":\"Empty JSON body\"}}");
                return;
            }
            if (request.isArray()) {
                var responses = JsonSupport.MAPPER.createArrayNode();
                for (JsonNode item : request) {
                    JsonNode itemResponse = protocol.handle(item);
                    if (itemResponse != null) {
                        responses.add(itemResponse);
                    }
                }
                response = responses.isEmpty() ? null : responses;
            } else {
                response = protocol.handle(request);
            }
        } catch (Exception ex) {
            respond(
                    exchange,
                    400,
                    "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32700,\"message\":\"Invalid JSON\"}}"
            );
            return;
        }
        if (response == null) {
            respond(exchange, 202, "");
            return;
        }
        respond(exchange, 200, JsonSupport.MAPPER.writeValueAsString(response));
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            respond(exchange, 405, "{\"error\":\"Use GET /health\"}");
            return;
        }
        if (!authorized(exchange) || !allowedOrigin(exchange)) {
            respond(exchange, 403, "{\"error\":\"Unauthorized or disallowed origin\"}");
            return;
        }
        addCors(exchange);
        respond(
                exchange,
                200,
                "{\"ok\":true,\"name\":\"" + McpToolServer.SERVER_NAME
                        + "\",\"version\":\"" + McpToolServer.SERVER_VERSION + "\"}"
        );
    }

    private boolean authorized(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        String expected = "Bearer " + token;
        return header != null && MessageDigest.isEqual(
                header.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8)
        );
    }

    private boolean allowedOrigin(HttpExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin == null || origin.isBlank()) {
            return true;
        }
        if (origin.equalsIgnoreCase("null")) {
            return true;
        }
        try {
            URI uri = URI.create(origin);
            String originHost = uri.getHost();
            return originHost != null && (
                    originHost.equalsIgnoreCase("localhost")
                            || originHost.equals("127.0.0.1")
                            || originHost.equals("::1")
                            || originHost.equals("[::1]")
            );
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static void addCors(HttpExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin != null) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
        }
        exchange.getResponseHeaders().set(
                "Access-Control-Allow-Headers",
                "Authorization, Content-Type, X-Requested-With"
        );
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
        exchange.getResponseHeaders().set("Vary", "Origin");
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        exchange.getResponseHeaders().set("Content-Security-Policy", "default-src 'none'");
        exchange.getResponseHeaders().set("Cross-Origin-Resource-Policy", "same-origin");
        exchange.getResponseHeaders().set(
                "Content-Type",
                body.startsWith("{") ? "application/json; charset=utf-8" : "text/plain; charset=utf-8"
        );
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
        stopped.countDown();
    }
}
