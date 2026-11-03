package io.opentools.core.job;

import java.nio.file.Path;
import java.time.Instant;

/**
 * One persisted job in a project.
 *
 * <p>{@code progress} is a fraction from {@code 0.0} through {@code 1.0} and
 * {@code progressMessage} is the latest human-readable progress text. Both are
 * advisory: they never change the terminal job status.</p>
 */
public record JobRecord(
        String runId,
        String toolId,
        String toolVersion,
        JobStatus status,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        double progress,
        String progressMessage,
        String paramsJson,
        String manifestJson,
        String errorCode,
        String errorMessage,
        Path runDirectory
) {
    public JobRecord {
        progress = Double.isFinite(progress) ? Math.max(0.0, Math.min(1.0, progress)) : 0.0;
    }

    /**
     * Creates a record without progress details.
     *
     * @param runId run identifier
     * @param toolId tool identifier
     * @param toolVersion tool version
     * @param status job status
     * @param createdAt creation time
     * @param startedAt start time, or {@code null}
     * @param finishedAt finish time, or {@code null}
     * @param paramsJson serialized parameters
     * @param manifestJson serialized manifest, or {@code null}
     * @param errorCode stable error code, or {@code null}
     * @param errorMessage readable error text, or {@code null}
     * @param runDirectory run working directory
     */
    public JobRecord(
            String runId,
            String toolId,
            String toolVersion,
            JobStatus status,
            Instant createdAt,
            Instant startedAt,
            Instant finishedAt,
            String paramsJson,
            String manifestJson,
            String errorCode,
            String errorMessage,
            Path runDirectory
    ) {
        this(runId, toolId, toolVersion, status, createdAt, startedAt, finishedAt,
                0.0, null, paramsJson, manifestJson, errorCode, errorMessage, runDirectory);
    }

    /**
     * Returns a copy with new progress details.
     *
     * @param value fraction from {@code 0.0} through {@code 1.0}
     * @param message latest progress text, or {@code null}
     * @return updated record
     */
    public JobRecord withProgress(double value, String message) {
        return new JobRecord(runId, toolId, toolVersion, status, createdAt, startedAt, finishedAt,
                value, message, paramsJson, manifestJson, errorCode, errorMessage, runDirectory);
    }
}
