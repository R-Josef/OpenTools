package io.opentools.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import io.opentools.core.automation.ToolContracts;
import io.opentools.core.job.JobRecord;
import io.opentools.core.job.JobStatus;
import io.opentools.api.tool.OpenToolException;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ValidationIssue;
import io.opentools.api.tool.ValidationResult;
import io.opentools.formats.FormatDetection;
import io.opentools.formats.FormatDetector;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ToolAutomationService {
    private final AutomationRuntime runtime;

    public ToolAutomationService(AutomationRuntime runtime) {
        this.runtime = runtime;
    }

    public AutomationRuntime runtime() {
        return runtime;
    }

    public List<Map<String, Object>> searchTools(String query, int limit) {
        int maximum = Math.max(1, Math.min(limit <= 0 ? 50 : limit, 200));
        return runtime.registry().search(query).stream()
                .limit(maximum)
                .map(this::toolSummary)
                .toList();
    }

    public Map<String, Object> describeTool(String toolId) throws AutomationException {
        ToolDescriptor descriptor = tool(toolId);
        Map<String, Object> result = toolSummary(descriptor);
        result.put("contract", ToolContracts.contract(descriptor));
        return result;
    }

    public ValidationReport validate(String toolId, Map<String, Object> parameters) throws AutomationException {
        ToolDescriptor descriptor = tool(toolId);
        WorkspacePolicy.NormalizedRequest normalized = runtime.workspacePolicy().normalize(descriptor, parameters);
        List<AutomationIssue> issues = new ArrayList<>(normalized.issues());
        ValidationResult toolValidation = runtime.execution().validate(normalized.request());
        for (ValidationIssue issue : toolValidation.issues()) {
            issues.add(new AutomationIssue(
                    "TOOL_VALIDATION",
                    issue.field(),
                    issue.severity().name(),
                    issue.message(),
                    issue.suggestion()
            ));
        }
        return new ValidationReport(
                issues.stream().noneMatch(AutomationIssue::error),
                toolId,
                descriptor.version(),
                normalized.request().parameters(),
                List.copyOf(issues)
        );
    }

    public Map<String, Object> submit(String toolId, Map<String, Object> parameters) throws Exception {
        ValidationReport report = validate(toolId, parameters);
        if (!report.valid()) {
            throw validationException(report);
        }
        ToolRequest request = new ToolRequest(toolId, report.parameters());
        String runId = runtime.execution().submit(runtime.project(), request);
        JobRecord job = runtime.execution().find(runId);
        return jobSummary(job);
    }

    public Map<String, Object> jobStatus(String runId, int waitSeconds) throws Exception {
        JobRecord job = runtime.execution().find(runId);
        int seconds = Math.max(0, Math.min(waitSeconds, 60));
        long deadline = System.nanoTime() + seconds * 1_000_000_000L;
        while (seconds > 0 && active(job.status()) && System.nanoTime() < deadline) {
            Thread.sleep(200);
            job = runtime.execution().find(runId);
        }
        return jobSummary(job);
    }

    public Map<String, Object> cancel(String runId) throws Exception {
        JobRecord job = runtime.execution().find(runId);
        boolean accepted = runtime.execution().cancel(runId);
        return Map.of(
                "runId", runId,
                "accepted", accepted,
                "status", job.status().name()
        );
    }

    public Map<String, Object> readResult(String runId) throws Exception {
        JobRecord job = runtime.execution().find(runId);
        Path resultPath = job.runDirectory().resolve("result.json");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("job", jobSummary(job));
        response.put("available", Files.isRegularFile(resultPath));
        if (!Files.isRegularFile(resultPath)) {
            if (!job.status().equals(JobStatus.SUCCEEDED)) {
                throw new AutomationException(
                        "RESULT_NOT_AVAILABLE",
                        "No result is available for job " + runId + " in state " + job.status() + ".",
                        "Check job_status and run logs before reading the result."
                );
            }
            throw new AutomationException(
                    "RESULT_MISSING",
                    "The successful job has no result.json.",
                    "Check the run directory and task logs."
            );
        }
        response.put("result", JsonSupport.MAPPER.readTree(resultPath.toFile()));
        return response;
    }

    public List<Map<String, Object>> recentJobs(int limit) throws Exception {
        return runtime.execution().recentJobs(Math.max(1, Math.min(limit, 200))).stream()
                .map(this::jobSummary)
                .toList();
    }

    public Map<String, Object> retry(String runId) throws Exception {
        String nextRunId = runtime.execution().retry(runtime.project(), runId);
        return jobSummary(runtime.execution().find(nextRunId));
    }

    public FormatDetection detectFormat(Path path) throws Exception {
        Path normalized = runtime.workspacePolicy().resolvePath(path.toString());
        if (!runtime.workspacePolicy().isAllowed(normalized)) {
            throw new AutomationException(
                    "PATH_OUTSIDE_WORKSPACE",
                    "Path is outside the allowed workspace: " + normalized,
                    "Use a file under the project or configured workspace roots."
            );
        }
        return new FormatDetector().detect(normalized);
    }

    public Map<String, Object> runBatch(BatchSpec spec) throws Exception {
        List<Map<String, Object>> results = new ArrayList<>();
        boolean stopped = false;
        for (int index = 0; index < spec.jobs().size(); index++) {
            BatchItem item = spec.jobs().get(index);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("index", index);
            result.put("toolId", item.toolId());
            try {
                ValidationReport report = validate(item.toolId(), item.parameters());
                result.put("validation", report);
                if (!report.valid()) {
                    throw validationException(report);
                }
                if (item.dryRun()) {
                    result.put("status", "VALID");
                } else {
                    Map<String, Object> submitted = submit(item.toolId(), report.parameters());
                    String runId = String.valueOf(submitted.get("runId"));
                    JobRecord current = runtime.execution().find(runId);
                    long deadline = System.nanoTime() + 3_600_000_000_000L;
                    while ((current.status() == JobStatus.QUEUED || current.status() == JobStatus.RUNNING)
                            && System.nanoTime() < deadline) {
                        Thread.sleep(200);
                        current = runtime.execution().find(runId);
                    }
                    if (current.status() == JobStatus.QUEUED || current.status() == JobStatus.RUNNING) {
                        throw new AutomationException(
                                "BATCH_TIMEOUT",
                                "Batch job exceeded the one-hour automation timeout: " + runId,
                                "Inspect the running job or split the workload."
                        );
                    }
                    Map<String, Object> job = jobSummary(current);
                    result.put("runId", runId);
                    result.put("status", job.get("status"));
                    result.put("job", job);
                }
            } catch (AutomationException ex) {
                result.put("status", "FAILED");
                result.put("errorCode", ex.code());
                result.put("errorMessage", ex.getMessage());
                result.put("issues", ex.issues());
            } catch (Exception ex) {
                result.put("status", "FAILED");
                result.put("errorCode", "BATCH_ITEM_FAILED");
                result.put("errorMessage", ex.getMessage());
            }
            results.add(result);
            if ("FAILED".equals(result.get("status")) && !spec.continueOnError()) {
                stopped = true;
                break;
            }
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("total", spec.jobs().size());
        response.put("completed", results.size());
        response.put("stoppedOnError", stopped);
        response.put("jobs", results);
        return response;
    }

    private ToolDescriptor tool(String toolId) throws AutomationException {
        return runtime.registry().find(toolId)
                .map(tool -> tool.descriptor())
                .orElseThrow(() -> new AutomationException(
                        "TOOL_NOT_FOUND",
                        "Unknown tool: " + toolId,
                        "Call search_tools to discover available tool IDs."
                ));
    }

    private Map<String, Object> toolSummary(ToolDescriptor descriptor) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("toolId", descriptor.id());
        result.put("name", descriptor.name());
        result.put("description", descriptor.description());
        result.put("category", descriptor.category());
        result.put("version", descriptor.version());
        result.put("aliases", descriptor.aliases());
        result.put("inputFormats", descriptor.inputFormats());
        result.put("outputFormats", descriptor.outputFormats());
        result.put("parameters", descriptor.parameters());
        return result;
    }

    private Map<String, Object> jobSummary(JobRecord job) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runId", job.runId());
        result.put("toolId", job.toolId());
        result.put("toolVersion", job.toolVersion());
        result.put("status", job.status().name());
        result.put("createdAt", job.createdAt());
        result.put("startedAt", job.startedAt());
        result.put("finishedAt", job.finishedAt());
        result.put("errorCode", job.errorCode());
        result.put("errorMessage", job.errorMessage());
        result.put("runDirectory", job.runDirectory());
        result.put("resultPath", job.runDirectory().resolve("result.json"));
        result.put("logPath", job.runDirectory().resolve("run.log"));
        try {
            result.put("parameters", JsonSupport.MAPPER.readValue(
                    job.paramsJson(),
                    new TypeReference<LinkedHashMap<String, Object>>() {
                    }
            ));
        } catch (Exception ex) {
            result.put("parameters", Map.of());
        }
        return result;
    }

    private static AutomationException validationException(ValidationReport report) {
        String first = report.issues().stream()
                .filter(AutomationIssue::error)
                .findFirst()
                .map(AutomationIssue::message)
                .orElse("Input validation failed.");
        return new AutomationException(
                "VALIDATION_FAILED",
                first,
                "Correct the listed input issues and validate again.",
                report.issues()
        );
    }

    private static boolean active(JobStatus status) {
        return status == JobStatus.QUEUED || status == JobStatus.RUNNING;
    }

    public record ValidationReport(
            boolean valid,
            String toolId,
            String toolVersion,
            Map<String, Object> parameters,
            List<AutomationIssue> issues
    ) {
        public ValidationReport {
            parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
            issues = issues == null ? List.of() : List.copyOf(issues);
        }
    }

    public record BatchSpec(boolean continueOnError, List<BatchItem> jobs) {
        public BatchSpec {
            jobs = jobs == null ? List.of() : List.copyOf(jobs);
            if (jobs.size() > 100) {
                throw new IllegalArgumentException("A batch may contain at most 100 jobs");
            }
        }
    }

    public record BatchItem(String toolId, Map<String, Object> parameters, boolean dryRun) {
        public BatchItem {
            if (toolId == null || toolId.isBlank()) {
                throw new IllegalArgumentException("Batch item toolId cannot be blank");
            }
            parameters = parameters == null
                    ? Map.of()
                    : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
        }
    }
}
