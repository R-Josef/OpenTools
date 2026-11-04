package io.opentools.core.job;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * SQLite-backed job history for one project.
 *
 * <p>Every operation opens its own short-lived connection so that the GUI, CLI,
 * and MCP server can share one project database safely. WAL and a busy timeout
 * keep concurrent readers from failing under normal contention.</p>
 */
public final class SqliteJobStore implements JobStore {
    private static final String INTERRUPTED_MESSAGE =
            "The application exited before this job finished. Retry the job to run it again.";
    private final Path databasePath;

    public SqliteJobStore(Path databasePath) {
        SqliteNativeSupport.prepare();
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
                        progress REAL NOT NULL DEFAULT 0,
                        progress_message TEXT,
                        heartbeat_at TEXT,
                        params_json TEXT NOT NULL,
                        manifest_json TEXT,
                        error_code TEXT,
                        error_message TEXT,
                        run_directory TEXT NOT NULL,
                        cancel_requested INTEGER NOT NULL DEFAULT 0
                    )
                    """);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS jobs_created_idx ON jobs(created_at DESC)");
            ensureColumn(connection, "progress", "REAL NOT NULL DEFAULT 0");
            ensureColumn(connection, "progress_message", "TEXT");
            ensureColumn(connection, "heartbeat_at", "TEXT");
            ensureColumn(connection, "cancel_requested", "INTEGER NOT NULL DEFAULT 0");
        }
        recoverInterrupted(Instant.now(), staleAfter());
    }

    @Override
    public void create(JobRecord job) throws SQLException {
        String sql = """
                INSERT INTO jobs(run_id, tool_id, tool_version, status, created_at, started_at, finished_at,
                                 progress, progress_message, heartbeat_at, params_json, manifest_json,
                                 error_code, error_message, run_directory)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, job, Instant.now());
            statement.executeUpdate();
        }
    }

    @Override
    public void update(JobRecord job) throws SQLException {
        String sql = """
                UPDATE jobs SET tool_id = ?, tool_version = ?, status = ?, created_at = ?, started_at = ?,
                    finished_at = ?, progress = ?, progress_message = ?, heartbeat_at = ?,
                    params_json = ?, manifest_json = ?, error_code = ?, error_message = ?, run_directory = ?
                WHERE run_id = ?
                """;
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, job.toolId());
            statement.setString(2, job.toolVersion());
            statement.setString(3, job.status().name());
            statement.setString(4, job.createdAt().toString());
            statement.setString(5, instant(job.startedAt()));
            statement.setString(6, instant(job.finishedAt()));
            statement.setDouble(7, job.progress());
            statement.setString(8, job.progressMessage());
            statement.setString(9, Instant.now().toString());
            statement.setString(10, job.paramsJson());
            statement.setString(11, job.manifestJson());
            statement.setString(12, job.errorCode());
            statement.setString(13, job.errorMessage());
            statement.setString(14, job.runDirectory().toString());
            statement.setString(15, job.runId());
            statement.executeUpdate();
        }
    }

    @Override
    public void updateProgress(String runId, double progress, String message) throws SQLException {
        String sql = "UPDATE jobs SET progress = ?, progress_message = ?, heartbeat_at = ? WHERE run_id = ?";
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDouble(1, Math.max(0.0, Math.min(1.0, progress)));
            statement.setString(2, message);
            statement.setString(3, Instant.now().toString());
            statement.setString(4, runId);
            statement.executeUpdate();
        }
    }

    @Override
    public void heartbeat(Collection<String> runIds, Instant now) throws SQLException {
        if (runIds == null || runIds.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(runIds.size(), "?"));
        String sql = "UPDATE jobs SET heartbeat_at = ? WHERE run_id IN (" + placeholders + ")";
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, now.toString());
            int index = 2;
            for (String runId : runIds) {
                statement.setString(index++, runId);
            }
            statement.executeUpdate();
        }
    }

    @Override
    public int recoverInterrupted(Instant now, Duration staleAfter) throws SQLException {
        Duration threshold = staleAfter == null || staleAfter.isNegative() ? Duration.ZERO : staleAfter;
        String cutoff = now.minus(threshold).toString();
        String sql = """
                UPDATE jobs SET status = 'FAILED', error_code = 'JOB_INTERRUPTED',
                    error_message = ?, finished_at = ?, heartbeat_at = NULL
                WHERE status IN ('RUNNING', 'QUEUED')
                  AND (heartbeat_at IS NULL OR julianday(heartbeat_at) < julianday(?))
                """;
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, INTERRUPTED_MESSAGE);
            statement.setString(2, now.toString());
            statement.setString(3, cutoff);
            return statement.executeUpdate();
        }
    }

    @Override
    public boolean requestCancel(String runId) throws SQLException {
        String sql = "UPDATE jobs SET cancel_requested = 1 "
                + "WHERE run_id = ? AND status IN ('QUEUED', 'RUNNING')";
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, runId);
            return statement.executeUpdate() > 0;
        }
    }

    @Override
    public boolean isCancelRequested(String runId) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT cancel_requested FROM jobs WHERE run_id = ?")) {
            statement.setString(1, runId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) != 0;
            }
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

    private static void bind(PreparedStatement statement, JobRecord job, Instant heartbeat) throws SQLException {
        statement.setString(1, job.runId());
        statement.setString(2, job.toolId());
        statement.setString(3, job.toolVersion());
        statement.setString(4, job.status().name());
        statement.setString(5, job.createdAt().toString());
        statement.setString(6, instant(job.startedAt()));
        statement.setString(7, instant(job.finishedAt()));
        statement.setDouble(8, job.progress());
        statement.setString(9, job.progressMessage());
        statement.setString(10, heartbeat.toString());
        statement.setString(11, job.paramsJson());
        statement.setString(12, job.manifestJson());
        statement.setString(13, job.errorCode());
        statement.setString(14, job.errorMessage());
        statement.setString(15, job.runDirectory().toString());
    }

    private static void ensureColumn(Connection connection, String column, String definition) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA table_info(jobs)")) {
            while (resultSet.next()) {
                if (column.equalsIgnoreCase(resultSet.getString("name"))) {
                    return;
                }
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE jobs ADD COLUMN " + column + " " + definition);
        } catch (SQLException ex) {
            // Another process may have added the column between the check and the ALTER.
            if (!String.valueOf(ex.getMessage()).toLowerCase(java.util.Locale.ROOT).contains("duplicate column")) {
                throw ex;
            }
        }
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
                resultSet.getDouble("progress"),
                resultSet.getString("progress_message"),
                resultSet.getString("params_json"),
                resultSet.getString("manifest_json"),
                resultSet.getString("error_code"),
                resultSet.getString("error_message"),
                Path.of(resultSet.getString("run_directory"))
        );
    }

    private static Duration staleAfter() {
        String configured = System.getProperty("opentools.jobs.staleAfterSeconds");
        if (configured != null && !configured.isBlank()) {
            try {
                long seconds = Long.parseLong(configured.trim());
                if (seconds > 0) {
                    return Duration.ofSeconds(seconds);
                }
            } catch (NumberFormatException ignored) {
                // Fall through to the default.
            }
        }
        return Duration.ofSeconds(120);
    }

    private static String instant(Instant value) {
        return value == null ? null : value.toString();
    }

    private static Instant parseInstant(String value) {
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }
}
