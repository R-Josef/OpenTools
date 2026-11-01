package io.opentools.cli;

import io.opentools.core.job.JobRecord;
import io.opentools.core.job.JobStatus;
import io.opentools.api.tool.OpenToolException;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.CancellationException;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ExitCodesTest {
    @Test
    void mapsJobsAndExceptionsToStableExitCodes() {
        assertEquals(ExitCodes.SUCCESS, ExitCodes.forJob(job(JobStatus.SUCCEEDED, null)));
        assertEquals(ExitCodes.EXECUTION, ExitCodes.forJob(job(JobStatus.FAILED, "TASK_FAILED")));
        assertEquals(ExitCodes.DEPENDENCY,
                ExitCodes.forJob(job(JobStatus.FAILED, "EXTERNAL_TOOL_MISSING")));
        assertEquals(ExitCodes.CANCELLED, ExitCodes.forJob(job(JobStatus.CANCELLED, "CANCELLED")));
        assertEquals(ExitCodes.INPUT,
                ExitCodes.forException(new OpenToolException("TOOL_NOT_FOUND", "missing", "")));
        assertEquals(ExitCodes.DEPENDENCY,
                ExitCodes.forException(new OpenToolException("EXTERNAL_TOOL_MISSING", "missing", "")));
        assertEquals(ExitCodes.CANCELLED,
                ExitCodes.forException(new CancellationException("cancelled")));
    }

    private static JobRecord job(JobStatus status, String errorCode) {
        return new JobRecord(
                "run",
                "tool",
                "1",
                status,
                Instant.now(),
                null,
                null,
                "{}",
                null,
                errorCode,
                null,
                Path.of("run")
        );
    }
}
