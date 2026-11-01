package io.opentools.core.job;

import java.nio.file.Path;
import java.time.Instant;

public record JobRecord(
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
}
