package io.opentools.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ToolAutomationService {
    private static final Pattern WORKFLOW_PLACEHOLDER = Pattern.compile(
            "\\$\\{([A-Za-z0-9_-]+)\\.(output(?:\\[\\d+])?|metric\\.[A-Za-z0-9_.-]+|runId)\\}");
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

    public Map<String, Object> runWorkflow(Path workflowFile) throws Exception {
        Path normalized = runtime.workspacePolicy().resolvePath(workflowFile.toString());
        if (!runtime.workspacePolicy().isAllowed(normalized)) {
            throw new AutomationException(
                    "PATH_OUTSIDE_WORKSPACE",
                    "Workflow file is outside the allowed workspace: " + normalized,
                    "Place the workflow under the project or an allowed workspace root."
            );
        }
        if (!Files.isRegularFile(normalized)) {
            throw new AutomationException(
                    "WORKFLOW_NOT_FOUND",
                    "Workflow file does not exist: " + normalized,
                    "Check the path and try again."
            );
        }
        if (Files.size(normalized) > 10L * 1024 * 1024) {
            throw new AutomationException(
                    "WORKFLOW_FILE_TOO_LARGE",
                    "Workflow file exceeds the 10 MB limit.",
                    "Reduce embedded text parameters or split the workflow."
            );
        }
        WorkflowSpec spec = JsonSupport.MAPPER.readValue(normalized.toFile(), WorkflowSpec.class);
        return runWorkflow(spec);
    }

    /**
     * Runs a workflow after replacing explicit watch variables such as
     * ${watch.file} and ${watch.name} in the JSON text.
     *
     * @param workflowFile workflow JSON under the workspace
     * @param variables placeholder values keyed without the surrounding braces
     * @return per-step status, run ids, artifacts, and metrics
     * @throws Exception when the file cannot be read or is invalid
     */
    public Map<String, Object> runWorkflow(Path workflowFile, Map<String, String> variables) throws Exception {
        if (variables == null || variables.isEmpty()) {
            return runWorkflow(workflowFile);
        }
        Path normalized = runtime.workspacePolicy().resolvePath(workflowFile.toString());
        if (!runtime.workspacePolicy().isAllowed(normalized)) {
            throw new AutomationException(
                    "PATH_OUTSIDE_WORKSPACE",
                    "Workflow file is outside the allowed workspace: " + normalized,
                    "Place the workflow under the project or an allowed workspace root."
            );
        }
        if (!Files.isRegularFile(normalized)) {
            throw new AutomationException(
                    "WORKFLOW_NOT_FOUND",
                    "Workflow file does not exist: " + normalized,
                    "Check the path and try again."
            );
        }
        String text = Files.readString(normalized);
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String replacement = JsonSupport.MAPPER.writeValueAsString(
                    entry.getValue() == null ? "" : entry.getValue());
            text = text.replace("${" + entry.getKey() + "}",
                    replacement.substring(1, replacement.length() - 1));
        }
        Path directory = normalized.getParent() == null ? runtime.project().root() : normalized.getParent();
        Path temporary = Files.createTempFile(directory, ".workflow-", ".json");
        try {
            Files.writeString(temporary, text);
            return runWorkflow(temporary);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public Map<String, Object> runWorkflow(WorkflowSpec spec) throws Exception {
        if (spec.steps().size() > 100) {
            throw new AutomationException(
                    "WORKFLOW_TOO_LARGE",
                    "A workflow may contain at most 100 steps.",
                    "Split the workflow into smaller files."
            );
        }
        Map<String, WorkflowStep> byId = new LinkedHashMap<>();
        for (WorkflowStep step : spec.steps()) {
            if (step.id() == null || step.id().isBlank()) {
                throw new AutomationException(
                        "WORKFLOW_STEP_ID_MISSING",
                        "Every workflow step needs a non-blank id.",
                        "Add an id such as stats or plot."
                );
            }
            if (byId.putIfAbsent(step.id(), step) != null) {
                throw new AutomationException(
                        "WORKFLOW_STEP_ID_DUPLICATE",
                        "Duplicate workflow step id: " + step.id(),
                        "Use unique ids for every step."
                );
            }
        }
        for (WorkflowStep step : spec.steps()) {
            for (String dependency : step.dependsOn()) {
                if (!byId.containsKey(dependency)) {
                    throw new AutomationException(
                            "WORKFLOW_DEPENDENCY_MISSING",
                            "Step " + step.id() + " depends on unknown step " + dependency + ".",
                            "Check the dependsOn list."
                    );
                }
            }
        }
        List<String> order = topologicalOrder(spec.steps(), byId);
        Map<String, StepOutput> completed = new LinkedHashMap<>();
        Map<String, String> statuses = new LinkedHashMap<>();
        List<Map<String, Object>> stepResults = new ArrayList<>();
        boolean stopped = false;

        for (String id : order) {
            WorkflowStep step = byId.get(id);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", id);
            entry.put("toolId", step.toolId());
            boolean dependencyFailed = step.dependsOn().stream()
                    .anyMatch(dependency -> !"SUCCEEDED".equals(statuses.get(dependency)));
            if (stopped || dependencyFailed) {
                statuses.put(id, "SKIPPED");
                entry.put("status", "SKIPPED");
                entry.put("errorCode", dependencyFailed ? "WORKFLOW_DEPENDENCY_FAILED" : "WORKFLOW_STOPPED");
                stepResults.add(entry);
                continue;
            }
            try {
                Map<String, Object> parameters = resolveParameters(step.parameters(), completed);
                ValidationReport report = validate(step.toolId(), parameters);
                entry.put("validation", report);
                if (!report.valid()) {
                    throw validationException(report);
                }
                Map<String, Object> submitted = submit(step.toolId(), report.parameters());
                String runId = String.valueOf(submitted.get("runId"));
                JobRecord job = awaitWorkflowJob(runId);
                entry.put("runId", runId);
                entry.put("status", job.status().name());
                if (job.status() != JobStatus.SUCCEEDED) {
                    throw new AutomationException(
                            job.errorCode() == null ? "WORKFLOW_STEP_FAILED" : job.errorCode(),
                            job.errorMessage() == null
                                    ? "Workflow step " + id + " ended with " + job.status() + "."
                                    : job.errorMessage(),
                            "Inspect the step run directory and log."
                    );
                }
                StepOutput output = readStepOutput(runId);
                completed.put(id, output);
                statuses.put(id, "SUCCEEDED");
                entry.put("outputs", output.outputs());
                entry.put("metrics", output.metrics());
            } catch (AutomationException ex) {
                statuses.put(id, "FAILED");
                entry.put("status", "FAILED");
                entry.put("errorCode", ex.code());
                entry.put("errorMessage", ex.getMessage());
                entry.put("issues", ex.issues());
                if (!spec.continueOnError()) {
                    stopped = true;
                }
            } catch (Exception ex) {
                statuses.put(id, "FAILED");
                entry.put("status", "FAILED");
                entry.put("errorCode", "WORKFLOW_STEP_FAILED");
                entry.put("errorMessage", ex.getMessage());
                if (!spec.continueOnError()) {
                    stopped = true;
                }
            }
            stepResults.add(entry);
        }

        long succeeded = statuses.values().stream().filter("SUCCEEDED"::equals).count();
        long failed = statuses.values().stream().filter("FAILED"::equals).count();
        long skipped = statuses.values().stream().filter("SKIPPED"::equals).count();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("name", spec.name());
        response.put("continueOnError", spec.continueOnError());
        response.put("total", spec.steps().size());
        response.put("succeeded", succeeded);
        response.put("failed", failed);
        response.put("skipped", skipped);
        response.put("stoppedOnError", stopped);
        response.put("steps", stepResults);
        return response;
    }

    private List<String> topologicalOrder(
            List<WorkflowStep> steps,
            Map<String, WorkflowStep> byId
    ) throws AutomationException {
        Map<String, Integer> indegree = new LinkedHashMap<>();
        Map<String, List<String>> children = new LinkedHashMap<>();
        for (WorkflowStep step : steps) {
            indegree.put(step.id(), step.dependsOn().size());
            children.put(step.id(), new ArrayList<>());
        }
        for (WorkflowStep step : steps) {
            for (String dependency : step.dependsOn()) {
                if (dependency.equals(step.id())) {
                    throw new AutomationException(
                            "WORKFLOW_CYCLE",
                            "Step " + step.id() + " depends on itself.",
                            "Remove the self-dependency."
                    );
                }
                children.get(dependency).add(step.id());
            }
        }
        Deque<String> ready = new ArrayDeque<>();
        for (WorkflowStep step : steps) {
            if (indegree.get(step.id()) == 0) {
                ready.add(step.id());
            }
        }
        List<String> order = new ArrayList<>();
        while (!ready.isEmpty()) {
            String id = ready.removeFirst();
            order.add(id);
            for (String child : children.get(id)) {
                int remaining = indegree.merge(child, -1, Integer::sum);
                if (remaining == 0) {
                    ready.addLast(child);
                }
            }
        }
        if (order.size() != byId.size()) {
            throw new AutomationException(
                    "WORKFLOW_CYCLE",
                    "The workflow contains a dependency cycle.",
                    "Remove cyclic dependsOn edges."
            );
        }
        return order;
    }

    private JobRecord awaitWorkflowJob(String runId) throws Exception {
        JobRecord job = runtime.execution().find(runId);
        long deadline = System.nanoTime() + 3_600_000_000_000L;
        while ((job.status() == JobStatus.QUEUED || job.status() == JobStatus.RUNNING)
                && System.nanoTime() < deadline) {
            Thread.sleep(200);
            job = runtime.execution().find(runId);
        }
        if (job.status() == JobStatus.QUEUED || job.status() == JobStatus.RUNNING) {
            throw new AutomationException(
                    "WORKFLOW_TIMEOUT",
                    "Workflow step exceeded the one-hour automation timeout: " + runId,
                    "Inspect the running job or split the workload."
            );
        }
        return job;
    }

    private StepOutput readStepOutput(String runId) throws Exception {
        JobRecord job = runtime.execution().find(runId);
        Path resultPath = job.runDirectory().resolve("result.json");
        if (!Files.isRegularFile(resultPath)) {
            throw new AutomationException(
                    "WORKFLOW_RESULT_MISSING",
                    "Successful workflow step has no result.json: " + runId,
                    "Inspect the run directory."
            );
        }
        JsonNode root = JsonSupport.MAPPER.readTree(resultPath.toFile());
        List<String> outputs = new ArrayList<>();
        for (JsonNode artifact : root.path("artifacts")) {
            String path = artifact.path("path").asText("");
            if (!path.isBlank()) {
                outputs.add(path);
            }
        }
        Map<String, Object> metrics = JsonSupport.MAPPER.convertValue(
                root.path("metrics"),
                new TypeReference<LinkedHashMap<String, Object>>() {
                }
        );
        return new StepOutput(runId, List.copyOf(outputs), Map.copyOf(metrics));
    }

    private Map<String, Object> resolveParameters(
            Map<String, Object> parameters,
            Map<String, StepOutput> completed
    ) throws Exception {
        Map<String, Object> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            resolved.put(entry.getKey(), resolveValue(entry.getValue(), completed));
        }
        return resolved;
    }

    private Object resolveValue(Object value, Map<String, StepOutput> completed) throws Exception {
        if (value instanceof String text) {
            return resolveText(text, completed);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put(String.valueOf(entry.getKey()), resolveValue(entry.getValue(), completed));
            }
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>();
            for (Object item : list) {
                copy.add(resolveValue(item, completed));
            }
            return copy;
        }
        return value;
    }

    private String resolveText(String text, Map<String, StepOutput> completed) throws Exception {
        Matcher matcher = WORKFLOW_PLACEHOLDER.matcher(text);
        StringBuilder resolved = new StringBuilder();
        while (matcher.find()) {
            String stepId = matcher.group(1);
            String expression = matcher.group(2);
            StepOutput output = completed.get(stepId);
            if (output == null) {
                throw new AutomationException(
                        "WORKFLOW_DEPENDENCY_NOT_READY",
                        "Placeholder references a step that has not succeeded: " + stepId,
                        "Check dependsOn and earlier failures."
                );
            }
            String replacement;
            if (expression.equals("runId")) {
                replacement = output.runId();
            } else if (expression.startsWith("metric.")) {
                String key = expression.substring("metric.".length());
                Object metric = output.metrics().get(key);
                if (metric == null) {
                    throw new AutomationException(
                            "WORKFLOW_METRIC_MISSING",
                            "Step " + stepId + " has no metric " + key + ".",
                            "Check the step result metrics."
                    );
                }
                replacement = String.valueOf(metric);
            } else {
                int index = 0;
                if (expression.startsWith("output[")) {
                    String number = expression.substring("output[".length(), expression.length() - 1);
                    try {
                        index = Integer.parseInt(number);
                    } catch (NumberFormatException ex) {
                        throw new AutomationException(
                                "WORKFLOW_PLACEHOLDER_INVALID",
                                "Invalid output index: " + expression,
                                "Use output or output[0]."
                        );
                    }
                }
                if (index < 0 || index >= output.outputs().size()) {
                    throw new AutomationException(
                            "WORKFLOW_OUTPUT_MISSING",
                            "Step " + stepId + " has no artifact at index " + index + ".",
                            "Check the step artifacts or use a different index."
                    );
                }
                replacement = output.outputs().get(index);
            }
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(resolved);
        return resolved.toString();
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

    public record WorkflowSpec(String name, boolean continueOnError, List<WorkflowStep> steps) {
        public WorkflowSpec {
            name = name == null || name.isBlank() ? "workflow" : name;
            steps = steps == null ? List.of() : List.copyOf(steps);
        }
    }

    public record WorkflowStep(
            String id,
            String toolId,
            Map<String, Object> parameters,
            List<String> dependsOn
    ) {
        public WorkflowStep {
            parameters = parameters == null
                    ? Map.of()
                    : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
            dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
        }
    }

    private record StepOutput(String runId, List<String> outputs, Map<String, Object> metrics) {
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