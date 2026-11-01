package io.opentools.core.job;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SqliteJobStore implements JobStore {
    private final Path databasePath;

    public SqliteJobStore(Path databasePath) {
        this.databasePath = databasePath.toAbsolutePath().normalize();
    }

    @Override
    public void initialize() throws SQLException {
        try {
            Files.createDirectories(databasePath.getParent());
        } catch (Exception ex) {
            throw new SQLException("Could not create project metadata directory", ex);
        }
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS jobs (
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
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS jobs_created_idx ON jobs(created_at DESC)");
        }
    }

    @Override
    public void create(JobRecord job) throws SQLException {
        String sql = """
                INSERT INTO jobs(run_id, tool_id, tool_version, status, created_at, started_at, finished_at,
                                 params_json, manifest_json, error_code, error_message, run_directory)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, job);
            statement.executeUpdate();
        }
    }

    @Override
    public void update(JobRecord job) throws SQLException {
        String sql = """
                UPDATE jobs SET tool_id = ?, tool_version = ?, status = ?, created_at = ?, started_at = ?,
                    finished_at = ?, params_json = ?, manifest_json = ?, error_code = ?, error_message = ?,
                    run_directory = ?
                WHERE run_id = ?
                """;
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, job.toolId());
            statement.setString(2, job.toolVersion());
            statement.setString(3, job.status().name());
            statement.setString(4, job.createdAt().toString());
            statement.setString(5, instant(job.startedAt()));
            statement.setString(6, instant(job.finishedAt()));
            statement.setString(7, job.paramsJson());
            statement.setString(8, job.manifestJson());
            statement.setString(9, job.errorCode());
            statement.setString(10, job.errorMessage());
            statement.setString(11, job.runDirectory().toString());
            statement.setString(12, job.runId());
            statement.executeUpdate();
        }
    }

    @Override
    public Optional<JobRecord> find(String runId) throws SQLException {
        try (Connection connection = connect(); PreparedStatement statement =
                     connection.prepareStatement("SELECT * FROM jobs WHERE run_id = ?")) {
            statement.setString(1, runId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(read(resultSet)) : Optional.empty();
            }
        }
    }

    @Override
    public List<JobRecord> list(int limit) throws SQLException {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM jobs ORDER BY created_at DESC LIMIT ?")) {
            statement.setInt(1, safeLimit);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<JobRecord> jobs = new ArrayList<>();
                while (resultSet.next()) {
                    jobs.add(read(resultSet));
                }
                return List.copyOf(jobs);
            }
        }
    }

    private Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA busy_timeout=5000");
        }
        return connection;
    }

    private static void bind(PreparedStatement statement, JobRecord job) throws SQLException {
        statement.setString(1, job.runId());
        statement.setString(2, job.toolId());
        statement.setString(3, job.toolVersion());
        statement.setString(4, job.status().name());
        statement.setString(5, job.createdAt().toString());
        statement.setString(6, instant(job.startedAt()));
        statement.setString(7, instant(job.finishedAt()));
        statement.setString(8, job.paramsJson());
        statement.setString(9, job.manifestJson());
        statement.setString(10, job.errorCode());
        statement.setString(11, job.errorMessage());
        statement.setString(12, job.runDirectory().toString());
    }

    private static JobRecord read(ResultSet resultSet) throws SQLException {
        return new JobRecord(
                resultSet.getString("run_id"),
                resultSet.getString("tool_id"),
                resultSet.getString("tool_version"),
                JobStatus.valueOf(resultSet.getString("status")),
                Instant.parse(resultSet.getString("created_at")),
                parseInstant(resultSet.getString("started_at")),
                parseInstant(resultSet.getString("finished_at")),
                resultSet.getString("params_json"),
                resultSet.getString("manifest_json"),
                resultSet.getString("error_code"),
                resultSet.getString("error_message"),
                Path.of(resultSet.getString("run_directory"))
        );
    }

    private static String instant(Instant value) {
        return value == null ? null : value.toString();
    }

    private static Instant parseInstant(String value) {
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }
}
