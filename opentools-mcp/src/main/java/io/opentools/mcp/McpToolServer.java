package io.opentools.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.opentools.core.runtime.AppVersion;
import io.opentools.formats.FormatDetection;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class McpToolServer {
    public static final String SERVER_NAME = "opentools";
    public static final String SERVER_VERSION = AppVersion.current();
    public static final String LATEST_PROTOCOL_VERSION = "2025-06-18";
    public static final List<String> SUPPORTED_PROTOCOL_VERSIONS = List.of(
            "2024-11-05",
            "2025-03-26",
            LATEST_PROTOCOL_VERSION
    );

    private final TaskAutomationService automation;

    public McpToolServer(TaskAutomationService automation) {
        this.automation = automation;
    }

    public JsonNode handle(JsonNode request) {
        boolean notification = !request.has("id") || request.get("id").isNull();
        try {
            validateRequestEnvelope(request);
            JsonNode response = dispatch(request);
            if (notification || response == null) {
                return null;
            }
            ObjectNode envelope = JsonSupport.MAPPER.createObjectNode();
            envelope.put("jsonrpc", "2.0");
            envelope.set("id", request.get("id"));
            envelope.set("result", response);
            return envelope;
        } catch (McpProtocolException ex) {
            return notification ? null : error(request.get("id"), ex.code(), ex.getMessage());
        } catch (Exception ex) {
            return notification ? null : error(request.get("id"), -32603, rootMessage(ex));
        }
    }

    private JsonNode dispatch(JsonNode request) throws Exception {
        String method = request.path("method").asText();
        JsonNode params = request.path("params");
        return switch (method) {
            case "initialize" -> initialize(params);
            case "notifications/initialized", "notifications/cancelled" -> null;
            case "ping" -> JsonSupport.MAPPER.createObjectNode();
            case "tools/list" -> toolsList();
            case "tools/call" -> callTool(params);
            case "resources/list" -> JsonSupport.MAPPER.createObjectNode()
                    .set("resources", JsonSupport.MAPPER.createArrayNode());
            case "resources/templates/list" -> JsonSupport.MAPPER.createObjectNode()
                    .set("resourceTemplates", JsonSupport.MAPPER.createArrayNode());
            default -> throw new McpProtocolException(-32601, "Method not found: " + method);
        };
    }

    private ObjectNode initialize(JsonNode params) {
        String requested = params.path("protocolVersion").asText(LATEST_PROTOCOL_VERSION);
        String selected = SUPPORTED_PROTOCOL_VERSIONS.contains(requested)
                ? requested
                : LATEST_PROTOCOL_VERSION;
        ObjectNode result = JsonSupport.MAPPER.createObjectNode();
        result.put("protocolVersion", selected);
        ObjectNode capabilities = result.putObject("capabilities");
        capabilities.putObject("tools").put("listChanged", false);
        ObjectNode serverInfo = result.putObject("serverInfo");
        serverInfo.put("name", SERVER_NAME);
        serverInfo.put("version", SERVER_VERSION);
        result.put(
                "instructions",
                "OpenTools is a local-first bioinformatics task service. "
                        + "Use search_tools, describe_tool, validate_inputs, submit_job, "
                        + "job_status, and read_result. Overwrite is always disabled for automation."
        );
        return result;
    }

    private ObjectNode toolsList() {
        ObjectNode result = JsonSupport.MAPPER.createObjectNode();
        ArrayNode tools = result.putArray("tools");
        addTool(
                tools,
                "opentools_search_tools",
                "Search registered tools by ID, name, format, category, or alias.",
                schema(
                        Map.of(
                                "query", property("string", "Search text"),
                                "limit", propertyWithRange("integer", "Maximum results", 1, 200, 50)
                        ),
                        List.of()
                )
        );
        addTool(
                tools,
                "opentools_describe_tool",
                "Return one tool descriptor and Draft 2020-12 parameter schema.",
                schema(Map.of("toolId", property("string", "Stable tool ID")), List.of("toolId"))
        );
        addTool(
                tools,
                "opentools_validate_inputs",
                "Validate task parameters and workspace path safety.",
                taskCallSchema()
        );
        addTool(
                tools,
                "opentools_submit_job",
                "Submit a validated task asynchronously. Overwrite is forbidden.",
                taskCallSchema()
        );
        addTool(
                tools,
                "opentools_job_status",
                "Read a job, optionally waiting up to 60 seconds for completion.",
                schema(
                        Map.of(
                                "runId", property("string", "Run ID"),
                                "waitSeconds", propertyWithRange("integer", "Maximum wait seconds", 0, 60, 0)
                        ),
                        List.of("runId")
                )
        );
        addTool(
                tools,
                "opentools_cancel_job",
                "Request cancellation for a queued or running job.",
                schema(Map.of("runId", property("string", "Run ID")), List.of("runId"))
        );
        addTool(
                tools,
                "opentools_read_result",
                "Read result.json and artifact metadata for a completed job.",
                schema(Map.of("runId", property("string", "Run ID")), List.of("runId"))
        );
        addTool(
                tools,
                "opentools_list_jobs",
                "List recent jobs in the active project.",
                schema(Map.of("limit", propertyWithRange("integer", "Maximum jobs", 1, 200, 20)), List.of())
        );
        addTool(
                tools,
                "opentools_run_batch",
                "Run a sequential JSON batch of task parameter sets.",
                schema(
                        Map.of(
                                "continueOnError", property("boolean", "Continue after an item fails"),
                                "jobs", batchItemsProperty()
                        ),
                        List.of("jobs")
                )
        );
        addTool(
                tools,
                "opentools_retry_job",
                "Retry a job with its stored effective parameters.",
                schema(Map.of("runId", property("string", "Run ID")), List.of("runId"))
        );
        addTool(
                tools,
                "opentools_detect_format",
                "Detect the format of a file inside the allowed workspace.",
                schema(Map.of("path", property("string", "Workspace file path")), List.of("path"))
        );
        return result;
    }

    private ObjectNode callTool(JsonNode params) throws Exception {
        String name = requiredText(params, "name");
        JsonNode arguments = params.path("arguments");
        if (!arguments.isObject() && !arguments.isMissingNode() && !arguments.isNull()) {
            throw new McpProtocolException(-32602, "tools/call arguments must be an object");
        }
        try {
            Object data = switch (name) {
                case "opentools_search_tools" -> automation.searchTools(
                        arguments.path("query").asText(""),
                        arguments.path("limit").asInt(50)
                );
                case "opentools_describe_tool" -> automation.describeTool(requiredText(arguments, "toolId"));
                case "opentools_validate_inputs" -> automation.validate(
                        requiredText(arguments, "toolId"),
                        parameters(arguments)
                );
                case "opentools_submit_job" -> automation.submit(
                        requiredText(arguments, "toolId"),
                        parameters(arguments)
                );
                case "opentools_job_status" -> automation.jobStatus(
                        requiredText(arguments, "runId"),
                        arguments.path("waitSeconds").asInt(0)
                );
                case "opentools_cancel_job" -> automation.cancel(requiredText(arguments, "runId"));
                case "opentools_read_result" -> automation.readResult(requiredText(arguments, "runId"));
                case "opentools_list_jobs" -> automation.recentJobs(arguments.path("limit").asInt(20));
                case "opentools_run_batch" -> automation.runBatch(
                        JsonSupport.MAPPER.treeToValue(
                                arguments,
                                TaskAutomationService.BatchSpec.class
                        )
                );
                case "opentools_retry_job" -> automation.retry(requiredText(arguments, "runId"));
                case "opentools_detect_format" -> automation.detectFormat(
                        Path.of(requiredText(arguments, "path"))
                );
                default -> throw new McpProtocolException(-32602, "Unknown tool: " + name);
            };
            return toolResult(true, Map.of("ok", true, "data", data), "");
        } catch (AutomationException ex) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("code", ex.code());
            error.put("message", ex.getMessage());
            error.put("suggestion", ex.suggestion());
            error.put("issues", ex.issues());
            return toolResult(false, Map.of("ok", false, "error", error), ex.getMessage());
        } catch (McpProtocolException ex) {
            throw ex;
        } catch (Exception ex) {
            Map<String, Object> error = Map.of(
                    "code", "INTERNAL_ERROR",
                    "message", rootMessage(ex),
                    "suggestion", "Check the run log or server diagnostics."
            );
            return toolResult(false, Map.of("ok", false, "error", error), rootMessage(ex));
        }
    }

    private ObjectNode toolResult(boolean success, Object structured, String summary) {
        ObjectNode result = JsonSupport.MAPPER.createObjectNode();
        result.put("isError", !success);
        ArrayNode content = result.putArray("content");
        ObjectNode text = content.addObject();
        text.put("type", "text");
        text.put("text", toJson(structured));
        result.set("structuredContent", JsonSupport.MAPPER.valueToTree(structured));
        return result;
    }

    private Map<String, Object> parameters(JsonNode arguments) throws McpProtocolException {
        JsonNode node = arguments.path("parameters");
        if (node.isMissingNode() || node.isNull()) {
            return Map.of();
        }
        if (!node.isObject()) {
            throw new McpProtocolException(-32602, "parameters must be an object");
        }
        return JsonSupport.MAPPER.convertValue(node, new com.fasterxml.jackson.core.type.TypeReference<>() {
        });
    }

    private static Map<String, Object> taskCallSchema() {
        return schema(
                Map.of(
                        "toolId", property("string", "Stable tool ID"),
                        "parameters", objectProperty("Tool parameters")
                ),
                List.of("toolId", "parameters")
        );
    }

    private static Map<String, Object> schema(
            Map<String, Object> properties,
            List<String> required
    ) {
        return Map.of(
                "type", "object",
                "properties", properties,
                "required", required,
                "additionalProperties", false
        );
    }

    private static Map<String, Object> property(String type, String description) {
        return Map.of("type", type, "description", description);
    }

    private static Map<String, Object> propertyWithRange(
            String type,
            String description,
            int minimum,
            int maximum,
            int defaultValue
    ) {
        return Map.of(
                "type", type,
                "description", description,
                "minimum", minimum,
                "maximum", maximum,
                "default", defaultValue
        );
    }

    private static Map<String, Object> objectProperty(String description) {
        return Map.of(
                "type", "object",
                "description", description,
                "additionalProperties", true
        );
    }

    private static Map<String, Object> batchItemsProperty() {
        return Map.of(
                "type", "array",
                "minItems", 1,
                "items", Map.of(
                        "type", "object",
                        "additionalProperties", false,
                        "properties", Map.of(
                                "toolId", property("string", "Stable tool ID"),
                                "parameters", objectProperty("Task parameters"),
                                "dryRun", property("boolean", "Validate without submitting")
                        ),
                        "required", List.of("toolId", "parameters")
                )
        );
    }

    private static void addTool(
            ArrayNode tools,
            String name,
            String description,
            Map<String, Object> inputSchema
    ) {
        ObjectNode tool = tools.addObject();
        tool.put("name", name);
        tool.put("description", description);
        tool.set("inputSchema", JsonSupport.MAPPER.valueToTree(inputSchema));
    }

    private static void validateRequestEnvelope(JsonNode request) throws McpProtocolException {
        if (!request.isObject()) {
            throw new McpProtocolException(-32600, "Invalid JSON-RPC request");
        }
        if (!"2.0".equals(request.path("jsonrpc").asText())) {
            throw new McpProtocolException(-32600, "jsonrpc must be 2.0");
        }
        if (!request.path("method").isTextual() || request.path("method").asText().isBlank()) {
            throw new McpProtocolException(-32600, "method is required");
        }
    }

    private static String requiredText(JsonNode node, String field) throws McpProtocolException {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new McpProtocolException(-32602, "Missing required string: " + field);
        }
        return value.asText();
    }

    private static ObjectNode error(JsonNode id, int code, String message) {
        ObjectNode response = JsonSupport.MAPPER.createObjectNode();
        response.put("jsonrpc", "2.0");
        if (id == null) {
            response.putNull("id");
        } else {
            response.set("id", id);
        }
        ObjectNode error = response.putObject("error");
        error.put("code", code);
        error.put("message", message == null ? "Unknown error" : message);
        return response;
    }

    private static String toJson(Object value) {
        try {
            return JsonSupport.MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{\"ok\":false,\"error\":{\"code\":\"JSON_SERIALIZATION_FAILED\"}}";
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
