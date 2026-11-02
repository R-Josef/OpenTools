package io.opentools.core.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import io.opentools.api.tool.CancellationToken;
import io.opentools.api.tool.OpenTool;
import io.opentools.api.tool.OpenToolException;
import io.opentools.api.tool.OutputArtifact;
import io.opentools.api.tool.ParameterType;
import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolResult;
import io.opentools.api.tool.ToolParameter;
import io.opentools.core.tool.ToolRegistry;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ValidationResult;
import io.opentools.core.util.Hashing;
import io.opentools.core.workspace.Project;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class ToolExecutionService implements AutoCloseable {
    private final ToolRegistry registry;
    private final JobStore jobStore;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<String, CancellationToken> cancellations = new ConcurrentHashMap<>();
    private final Map<String, Future<?>> futures = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public ToolExecutionService(ToolRegistry registry, JobStore jobStore) {
        this.registry = registry;
        this.jobStore = jobStore;
    }

    public ValidationResult validate(ToolRequest request) {
        OpenTool tool = registry.find(request.toolId())
                .orElseThrow(() -> new OpenToolException("TOOL_NOT_FOUND", "Unknown tool: " + request.toolId(), "Run `tools list`."));
        return tool.validate(withDefaults(tool, request));
    }

    public String submit(Project project, ToolRequest request) throws SQLException, IOException {
        OpenTool tool = registry.find(request.toolId())
                .orElseThrow(() -> new OpenToolException("TOOL_NOT_FOUND", "Unknown tool: " + request.toolId(), "Run `tools list`."));
        ToolRequest effectiveRequest = withDefaults(tool, request);
        ValidationResult validationResult = tool.validate(effectiveRequest);
        if (!validationResult.valid()) {
            throw new ToolValidationException(validationResult);
        }

        String runId = UUID.randomUUID().toString();
        Path runDirectory = project.runsDirectory().resolve(runId);
        Files.createDirectories(runDirectory.resolve("outputs"));
        Files.createDirectories(runDirectory.resolve("tmp"));

        String paramsJson = objectMapper.writeValueAsString(effectiveRequest.parameters());
        Files.writeString(runDirectory.resolve("params.json"), paramsJson + System.lineSeparator());

        JobRecord queued = new JobRecord(
                runId,
                effectiveRequest.toolId(),
                tool.descriptor().version(),
                JobStatus.QUEUED,
                Instant.now(),
                null,
                null,
                paramsJson,
                null,
                null,
                null,
                runDirectory
        );
        jobStore.create(queued);

        CancellationToken cancellation = new CancellationToken();
        cancellations.put(runId, cancellation);
        Future<?> future = executor.submit(() -> execute(project, effectiveRequest, tool, queued, cancellation));
        futures.put(runId, future);
        return runId;
    }

    public JobRecord runAndWait(Project project, ToolRequest request) throws Exception {
        String runId = submit(project, request);
        Future<?> future = futures.get(runId);
        if (future != null) {
            future.get();
        }
        return find(runId);
    }

    public boolean cancel(String runId) {
        CancellationToken token = cancellations.get(runId);
        if (token == null) {
            return false;
        }
        token.cancel();
        Future<?> future = futures.get(runId);
        if (future != null) {
            future.cancel(true);
        }
        return true;
    }

    public JobRecord find(String runId) throws SQLException {
        return jobStore.find(runId).orElseThrow(() ->
                new OpenToolException("JOB_NOT_FOUND", "Unknown job: " + runId, "List recent jobs."));
    }

    public List<JobRecord> recentJobs(int limit) throws SQLException {
        return jobStore.list(limit);
    }

    public ToolRequest requestFromJob(String runId) throws SQLException, IOException {
        JobRecord job = find(runId);
        try {
            Map<String, Object> parameters = objectMapper.readValue(
                    job.paramsJson(),
                    new TypeReference<LinkedHashMap<String, Object>>() {
                    }
            );
            return new ToolRequest(job.toolId(), parameters);
        } catch (Exception ex) {
            throw new OpenToolException(
                    "JOB_PARAMS_INVALID",
                    "Stored parameters for job " + runId + " could not be read.",
                    "Check params.json in the run directory.",
                    ex
            );
        }
    }

    public String retry(Project project, String runId) throws SQLException, IOException {
        return submit(project, requestFromJob(runId));
    }

    private void execute(Project project, ToolRequest request, OpenTool tool, JobRecord queued, CancellationToken cancellation) {
        Path logPath = queued.runDirectory().resolve("run.log");
        Instant startedAt = Instant.now();
        JobRecord running = new JobRecord(
                queued.runId(), queued.toolId(), queued.toolVersion(), JobStatus.RUNNING,
                queued.createdAt(), startedAt, null, queued.paramsJson(), null, null, null, queued.runDirectory()
        );
        try {
            jobStore.update(running);
            try (BufferedWriter logWriter = Files.newBufferedWriter(
                    logPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            )) {
                ToolExecutionContext context = new ToolExecutionContext(
                        queued.runId(),
                        project.root(),
                        queued.runDirectory(),
                        queued.runDirectory().resolve("outputs"),
                        message -> writeLog(logWriter, message),
                        ignored -> { },
                        cancellation
                );
                context.log("Tool: " + tool.descriptor().id() + " " + tool.descriptor().version());
                context.log("Project: " + project.root());
                context.progress(0.03, "Preparing run manifest");

                Map<String, Object> manifest = buildManifest(project, tool, request, queued, context);
                objectMapper.writeValue(queued.runDirectory().resolve("manifest.json").toFile(), manifest);
                context.progress(0.1, "Starting task");

                ToolResult result = tool.execute(request, context);
                cancellation.throwIfCancelled();

                List<Map<String, Object>> artifacts = describeArtifacts(result.artifacts());
                String toolId = tool.descriptor().id();
                Map<String, Object> resultDocument = new LinkedHashMap<>();
                resultDocument.put("summary", result.summary());
                resultDocument.put("summaryKey", "result." + toolId + ".summary");
                resultDocument.put("summaryArguments", result.summaryArguments());
                resultDocument.put("detail", result.detail());
                resultDocument.put("detailKey", "result." + toolId + ".detail");
                resultDocument.put("detailArguments", result.detailArguments());
                resultDocument.put("metrics", result.metrics());
                resultDocument.put("table", result.table());
                resultDocument.put("artifacts", artifacts);
                objectMapper.writeValue(queued.runDirectory().resolve("result.json").toFile(), resultDocument);

                manifest.put("finishedAt", Instant.now().toString());
                manifest.put("result", Map.of(
                        "summary", result.summary(),
                        "summaryKey", "result." + toolId + ".summary",
                        "metrics", result.metrics(),
                        "artifacts", artifacts
                ));
                objectMapper.writeValue(queued.runDirectory().resolve("manifest.json").toFile(), manifest);
                String manifestJson = objectMapper.writeValueAsString(manifest);

                JobRecord succeeded = new JobRecord(
                        queued.runId(), queued.toolId(), queued.toolVersion(), JobStatus.SUCCEEDED,
                        queued.createdAt(), startedAt, Instant.now(), queued.paramsJson(), manifestJson,
                        null, null, queued.runDirectory()
                );
                jobStore.update(succeeded);
                context.progress(1.0, "Completed: " + result.summary());
            }
        } catch (java.util.concurrent.CancellationException ex) {
            recordFailure(queued, startedAt, "CANCELLED", "Tool run cancelled.", logPath);
        } catch (OpenToolException ex) {
            recordFailure(queued, startedAt, ex.code(), ex.getMessage() + " " + safeSuggestion(ex.suggestion()), logPath);
        } catch (Exception ex) {
            recordFailure(queued, startedAt, "TOOL_FAILED", rootMessage(ex), logPath);
        } finally {
            cancellations.remove(queued.runId());
            futures.remove(queued.runId());
        }
    }

    private Map<String, Object> buildManifest(
            Project project,
            OpenTool tool,
            ToolRequest request,
            JobRecord job,
            ToolExecutionContext context
    ) throws IOException {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("runId", job.runId());
        manifest.put("openToolsVersion", openToolsVersion());
        manifest.put("toolId", tool.descriptor().id());
        manifest.put("toolVersion", tool.descriptor().version());
        manifest.put("projectId", project.id());
        manifest.put("projectRoot", project.root().toString());
        manifest.put("createdAt", job.createdAt().toString());
        manifest.put("parameters", request.parameters());
        manifest.put("javaVersion", System.getProperty("java.version"));
        manifest.put("javaVendor", System.getProperty("java.vendor"));
        manifest.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version"));

        List<Map<String, Object>> inputs = new ArrayList<>();
        for (ToolParameter parameter : tool.descriptor().parameters()) {
            if (parameter.type() != ParameterType.FILE) {
                continue;
            }
            String pathValue = request.string(parameter.id());
            if (pathValue.isBlank()) {
                continue;
            }
            Path path = Path.of(pathValue).toAbsolutePath().normalize();
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("parameter", parameter.id());
            input.put("path", path.toString());
            input.put("size", Files.size(path));
            input.put("sha256", Hashing.sha256(path));
            inputs.add(input);
            context.log("Input " + parameter.id() + ": " + path);
        }
        manifest.put("inputs", inputs);
        return manifest;
    }

    private static ToolRequest withDefaults(OpenTool tool, ToolRequest request) {
        Map<String, Object> effective = new LinkedHashMap<>();
        for (ToolParameter parameter : tool.descriptor().parameters()) {
            if (parameter.defaultValue() != null) {
                effective.put(parameter.id(), parameter.defaultValue());
            }
        }
        effective.putAll(request.parameters());
        return new ToolRequest(tool.descriptor().id(), effective);
    }

    private static String openToolsVersion() {
        String version = ToolExecutionService.class.getPackage().getImplementationVersion();
        return version == null || version.isBlank() ? "development" : version;
    }

    private List<Map<String, Object>> describeArtifacts(List<OutputArtifact> artifacts) throws IOException {
        List<Map<String, Object>> descriptions = new ArrayList<>();
        for (OutputArtifact artifact : artifacts) {
            Path path = artifact.path().toAbsolutePath().normalize();
            if (!Files.isRegularFile(path)) {
                throw new OpenToolException(
                        "OUTPUT_MISSING",
                        "Tool declared a missing output: " + path,
                        "Check the task output path."
                );
            }
            Map<String, Object> description = new LinkedHashMap<>();
            description.put("title", artifact.title());
            description.put("path", path.toString());
            description.put("mediaType", artifact.mediaType());
            description.put("size", Files.size(path));
            description.put("sha256", Hashing.sha256(path));
            descriptions.add(description);
        }
        return descriptions;
    }

    private void recordFailure(JobRecord queued, Instant startedAt, String code, String message, Path logPath) {
        String safeMessage = message == null ? "Tool run failed" : message;
        appendPlainLog(logPath, "ERROR " + code + ": " + safeMessage);
        try {
            JobRecord failed = new JobRecord(
                    queued.runId(), queued.toolId(), queued.toolVersion(),
                    "CANCELLED".equals(code) ? JobStatus.CANCELLED : JobStatus.FAILED,
                    queued.createdAt(), startedAt, Instant.now(), queued.paramsJson(), null,
                    code, safeMessage, queued.runDirectory()
            );
            jobStore.update(failed);
        } catch (SQLException ignored) {
            // The run log remains the fallback diagnostic if history persistence fails.
        }
    }

    private static void writeLog(BufferedWriter writer, String message) {
        try {
            writer.write("[" + Instant.now() + "] " + message);
            writer.newLine();
            writer.flush();
        } catch (IOException ex) {
            throw new OpenToolException("LOG_WRITE_FAILED", "Could not write run log.", "Check project write permissions.", ex);
        }
    }

    private static void appendPlainLog(Path logPath, String message) {
        try {
            Files.writeString(logPath, message + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // Best effort after a failed run.
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private static String safeSuggestion(String suggestion) {
        return suggestion == null || suggestion.isBlank() ? "" : "Suggestion: " + suggestion;
    }

    @Override
    public void close() throws Exception {
        executor.shutdownNow();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }
}
