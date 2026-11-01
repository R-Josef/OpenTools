package io.opentools.core.job;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface JobStore {
    void initialize() throws SQLException;

    void create(JobRecord job) throws SQLException;

    void update(JobRecord job) throws SQLException;

    Optional<JobRecord> find(String runId) throws SQLException;

    List<JobRecord> list(int limit) throws SQLException;
}
