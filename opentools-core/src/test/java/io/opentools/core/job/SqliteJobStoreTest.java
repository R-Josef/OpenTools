package io.opentools.core.job;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class SqliteJobStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void migratesLegacySchemaAndRecoversInterruptedJobs() throws Exception {
        Path database = tempDir.resolve("project.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE jobs (
                        run_id TEXT PRIMARY KEY,
                        tool_id TEXT NOT NULL,
                        tool_version TEXT NOT NULL,
                        status TEXT NOT NULL,
                        created_at TEXT NOT NULL,
                        started_at TEXT,
                        finished_at TEXT,
                        params_json TEXT NOT NULL,
                        manifest_json TEXT,
                        error_code TEXT,
                        error_message TEXT,
                        run_directory TEXT NOT NULL
                    )
                    """);
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO jobs(run_id, tool_id, tool_version, status, created_at, params_json, run_directory) "
                            + "VALUES ('legacy', 'test.echo', '1.0.0', 'RUNNING', '2026-01-01T00:00:00Z', '{}', ?)")) {
                insert.setString(1, tempDir.toString());
                insert.executeUpdate();
            }
        }

        SqliteJobStore store = new SqliteJobStore(database);
        store.initialize();

        JobRecord recovered = store.find("legacy").orElseThrow();
        assertEquals(JobStatus.FAILED, recovered.status());
        assertEquals("JOB_INTERRUPTED", recovered.errorCode());
        assertEquals(0.0, recovered.progress(), 0.001);

        store.updateProgress("legacy", 0.5, "Halfway");
        JobRecord updated = store.find("legacy").orElseThrow();
        assertEquals(0.5, updated.progress(), 0.001);
        assertEquals("Halfway", updated.progressMessage());
    }
}
