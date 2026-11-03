package io.opentools.cli;

import io.opentools.core.job.JobRecord;
import io.opentools.core.job.JobStatus;
import io.opentools.core.job.ToolValidationException;
import io.opentools.api.tool.OpenToolException;
import picocli.CommandLine;

import java.util.concurrent.CancellationException;

public final class ExitCodes {
    public static final int SUCCESS = 0;
    public static final int INPUT = 2;
    public static final int DEPENDENCY = 3;
    public static final int EXECUTION = 4;
    public static final int CANCELLED = 5;
    public static final int INTERNAL = 6;

    private ExitCodes() {
    }

    public static int forJob(JobRecord job) {
        return switch (job.status()) {
            case SUCCEEDED, QUEUED, RUNNING -> SUCCESS;
            case CANCELLED -> CANCELLED;
            case PARTIAL -> EXECUTION;
            case FAILED -> "EXTERNAL_TOOL_MISSING".equals(job.errorCode())
                    ? DEPENDENCY
                    : EXECUTION;
        };
    }

    public static int forException(Throwable throwable) {
        Throwable relevant = relevant(throwable);
        if (relevant instanceof ToolValidationException
                || relevant instanceof CommandLine.ParameterException) {
            return INPUT;
        }
        if (relevant instanceof CancellationException) {
            return CANCELLED;
        }
        if (relevant instanceof OpenToolException error) {
            String code = error.code();
            if ("TOOL_NOT_FOUND".equals(code)
                    || "JOB_NOT_FOUND".equals(code)
                    || "JOB_PARAMS_INVALID".equals(code)
                    || code.endsWith("_INVALID")
                    || code.contains("VALIDATION")
                    || code.startsWith("EMPTY_")) {
                return INPUT;
            }
            if (dependency(code)) {
                return DEPENDENCY;
            }
            return EXECUTION;
        }
        return INTERNAL;
    }

    public static boolean dependency(String code) {
        return "EXTERNAL_TOOL_MISSING".equals(code)
                || "EXTERNAL_PROCESS_START_FAILED".equals(code)
                || "DEPENDENCY_MISSING".equals(code);
    }

    private static Throwable relevant(Throwable throwable) {
        Throwable relevant = throwable;
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ToolValidationException
                    || current instanceof CommandLine.ParameterException
                    || current instanceof CancellationException
                    || current instanceof OpenToolException) {
                relevant = current;
            }
            current = current.getCause();
        }
        return relevant;
    }
}
