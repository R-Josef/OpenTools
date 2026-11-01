package io.opentools.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

public final class McpStdioServer {
    private static final int MAX_MESSAGE_CHARS = 4 * 1024 * 1024;
    private final McpToolServer protocol;

    public McpStdioServer(McpToolServer protocol) {
        this.protocol = protocol;
    }

    public void run(InputStream input, OutputStream output) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                if (line.length() > MAX_MESSAGE_CHARS) {
                    write(writer, parseError("JSON-RPC message exceeds size limit"));
                    continue;
                }
                JsonNode response;
                try {
                    JsonNode request = JsonSupport.MAPPER.readTree(line);
                    response = request.isArray() ? handleBatch(request) : protocol.handle(request);
                } catch (Exception ex) {
                    response = parseError("Invalid JSON: " + ex.getMessage());
                }
                if (response != null) {
                    write(writer, response);
                }
            }
        }
    }

    private JsonNode handleBatch(JsonNode requests) {
        ArrayNode responses = JsonSupport.MAPPER.createArrayNode();
        for (JsonNode request : requests) {
            JsonNode response = protocol.handle(request);
            if (response != null) {
                responses.add(response);
            }
        }
        return responses.isEmpty() ? null : responses;
    }

    private static void write(BufferedWriter writer, JsonNode response) throws Exception {
        writer.write(JsonSupport.MAPPER.writeValueAsString(response));
        writer.newLine();
        writer.flush();
    }

    private static ObjectNode parseError(String message) {
        ObjectNode response = JsonSupport.MAPPER.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.putNull("id");
        ObjectNode error = response.putObject("error");
        error.put("code", -32700);
        error.put("message", message);
        return response;
    }
}
