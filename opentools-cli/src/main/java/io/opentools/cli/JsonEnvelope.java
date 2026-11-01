package io.opentools.cli;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.opentools.core.job.JobRecord;
import io.opentools.core.job.JobStatus;
import io.opentools.core.job.ToolValidationException;
import io.opentools.api.tool.OpenToolException;
import io.opentools.api.tool.ValidationIssue;
import picocli.CommandLine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record JsonEnvelope(boolean ok, String apiVersion, Object data, Error error) {
    public static final String API_VERSION = "1.0";

    public static JsonEnvelope success(Object data) {
        return new JsonEnvelope(true, API_VERSION, data, null);
    }

    public static JsonEnvelope failure(
            String code,
            String message,
            String suggestion,
            List<?> issues
    ) {
        return failure(code, message, suggestion, issues, null);
    }

    public static JsonEnvelope failure(
            String code,
            String message,
            String suggestion,
            List<?> issues,
            Object details
    ) {
        return new JsonEnvelope(
                false,
                API_VERSION,
                null,
                new Error(
                        code == null || code.isBlank() ? "COMMAND_FAILED" : code,
                        message == null ? "Command failed." : message,
                        suggestion == null ? "" : suggestion,
                        issues == null ? List.of() : List.copyOf(issues),
                        details
                )
        );
    }

    public static JsonEnvelope validationFailure(List<ValidationIssue> issues) {
        String message = issues.stream()
                .filter(issue -> issue.severity() == ValidationIssue.Severity.ERROR)
                .map(ValidationIssue::message)
                .findFirst()
                .orElse("Input validation failed.");
        String suggestion = issues.stream()
                .filter(issue -> issue.severity() == ValidationIssue.Severity.ERROR)
                .map(ValidationIssue::suggestion)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse("Correct the listed input issues and retry.");
        return failure("VALIDATION_FAILED", message, suggestion, issues);
    }

    public static JsonEnvelope forJobFailure(JobRecord job) {
        String code = job.errorCode();
        if (code == null || code.isBlank()) {
            code = job.status() == JobStatus.CANCELLED ? "CANCELLED"
                    : job.status() == JobStatus.PARTIAL ? "PARTIAL_RESULT"
                    : "TASK_FAILED";
        }
        String message = job.errorMessage();
        if (message == null || message.isBlank()) {
            message = "Job " + job.runId() + " finished with status " + job.status() + ".";
        }
        return failure(
                code,
                message,
                job.status() == JobStatus.CANCELLED
                        ? "Submit the job again when ready."
                        : "Inspect run.log and correct the task input or dependency.",
                List.of(),
                Map.of("job", job)
        );
    }

    public static JsonEnvelope failure(Throwable throwable) {
        Throwable relevant = throwable;
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof OpenToolException
                    || current instanceof ToolValidationException
                    || current instanceof CommandLine.ParameterException
                    || current instanceof java.util.concurrent.CancellationException) {
                relevant = current;
            }
            current = current.getCause();
        }
        if (relevant instanceof ToolValidationException validation) {
            return validationFailure(validation.validationResult().issues());
        }
        if (relevant instanceof OpenToolException error) {
            return failure(error.code(), error.getMessage(), error.suggestion(), List.of());
        }
        if (relevant instanceof CommandLine.ParameterException error) {
            return failure(
                    "INVALID_ARGUMENTS",
                    error.getMessage(),
                    "Run with --help and correct the command arguments.",
                    List.of()
            );
        }
        if (relevant instanceof java.util.concurrent.CancellationException) {
            return failure("CANCELLED", "Command was cancelled.", "Retry when ready.", List.of());
        }
        return failure(
                "INTERNAL_ERROR",
                rootMessage(relevant),
                "Inspect the command output and application logs.",
                List.of()
        );
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Error(
            String code,
            String message,
            String suggestion,
            List<?> issues,
            Object details
    ) {
        public Error {
            issues = issues == null ? List.of() : List.copyOf(issues);
        }
    }
}
