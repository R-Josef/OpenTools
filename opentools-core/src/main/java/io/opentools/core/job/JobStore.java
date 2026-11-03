package io.opentools.core.job;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface JobStore {
    void initialize() throws SQLException;

    void create(JobRecord job) throws SQLException;

    void update(JobRecord job) throws SQLException;

    Optional<JobRecord> find(String runId) throws SQLException;

    List<JobRecord> list(int limit) throws SQLException;

    /**
     * Persists the latest progress value without rewriting the whole record.
     *
     * @param runId run identifier
     * @param progress fraction from {@code 0.0} through {@code 1.0}
     * @param message latest progress text, or {@code null}
     * @throws SQLException when the update fails
     */
    default void updateProgress(String runId, double progress, String message) throws SQLException {
    }

    /**
     * Refreshes the liveness heartbeat for active runs.
     *
     * @param runIds active run identifiers
     * @param now current instant
     * @throws SQLException when the update fails
     */
    default void heartbeat(Collection<String> runIds, Instant now) throws SQLException {
    }

    /**
     * Marks jobs whose owning process disappeared as interrupted.
     *
     * @param now current instant
     * @param staleAfter how long a heartbeat may be missing before a job is considered stale
     * @return number of recovered jobs
     * @throws SQLException when the update fails
     */
    default int recoverInterrupted(Instant now, Duration staleAfter) throws SQLException {
        return 0;
    }
}
