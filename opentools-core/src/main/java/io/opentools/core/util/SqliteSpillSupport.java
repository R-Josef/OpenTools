package io.opentools.core.util;

import io.opentools.api.tool.OpenToolException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;

public final class SqliteSpillSupport {
    public static final long MEBIBYTE = 1024L * 1024L;

    private SqliteSpillSupport() {
    }

    public static Path createTemporaryDatabase(Path directory, String prefix)
            throws OpenToolException {
        try {
            Files.createDirectories(directory);
            // Attempting the write is the reliable test. Files.isWritable can
            // report false on Windows ACLs that still allow the process to write.
            return Files.createTempFile(directory, prefix, ".sqlite");
        } catch (IOException ex) {
            throw new OpenToolException(
                    "SPILL_DIRECTORY_UNAVAILABLE",
                    "The temporary SQLite directory is not writable: " + directory,
                    "Choose a different project/run directory or fix filesystem permissions.",
                    ex
            );
        }
    }

    public static long megabytes(int value) {
        return value <= 0 ? 0 : Math.multiplyExact((long) value, MEBIBYTE);
    }

    public static void enforceLimit(Path database, long maxBytes) {
        if (maxBytes <= 0) {
            return;
        }
        try {
            long used = Files.size(database);
            for (String suffix : new String[]{"-journal", "-wal", "-shm"}) {
                Path sidecar = Path.of(database + suffix);
                if (Files.isRegularFile(sidecar)) {
                    used = Math.addExact(used, Files.size(sidecar));
                }
            }
            if (used > maxBytes) {
                throw new OpenToolException(
                        "SPILL_SPACE_LIMIT",
                        "The temporary SQLite database exceeded maxTempMegabytes: "
                                + formatMebibytes(used) + " MiB used, "
                                + formatMebibytes(maxBytes) + " MiB allowed.",
                        "Increase maxTempMegabytes, partition the input, or choose a larger temporary workspace."
                );
            }
        } catch (IOException | ArithmeticException ex) {
            throw new OpenToolException(
                    "SPILL_IO_ERROR",
                    "Could not inspect temporary SQLite disk usage: " + database,
                    "Check free space and filesystem permissions.",
                    ex
            );
        }
    }

    public static OpenToolException translateSqlException(SQLException ex, String operation) {
        int code = ex.getErrorCode();
        if (code == 13) {
            return new OpenToolException(
                    "SPILL_DISK_FULL",
                    operation + " could not complete because the temporary disk is full.",
                    "Free temporary storage, increase available space, or reduce maxTempMegabytes/input size.",
                    ex
            );
        }
        if (code == 8 || code == 10 || code == 14) {
            return new OpenToolException(
                    "SPILL_IO_ERROR",
                    operation + " could not read or write its temporary SQLite database.",
                    "Check temporary-directory permissions and free space.",
                    ex
            );
        }
        if (code == 7) {
            return new OpenToolException(
                    "SPILL_MEMORY_ERROR",
                    operation + " exhausted memory while using its temporary SQLite database.",
                    "Increase the Java heap or reduce the input.",
                    ex
            );
        }
        return new OpenToolException(
                "SPILL_DATABASE_ERROR",
                operation + " failed while using its temporary SQLite database.",
                "Check run.log, temporary-directory permissions, and free disk space.",
                ex
        );
    }

    public static void deleteDatabase(Path database) throws IOException {
        Files.deleteIfExists(database);
        for (String suffix : new String[]{"-journal", "-wal", "-shm"}) {
            Files.deleteIfExists(Path.of(database + suffix));
        }
    }

    public static void deleteDatabaseQuietly(Path database) {
        try {
            deleteDatabase(database);
        } catch (IOException ignored) {
            // Best-effort cleanup must not hide the original task failure.
        }
    }

    private static String formatMebibytes(long bytes) {
        return String.format(java.util.Locale.ROOT, "%.1f", bytes / (double) MEBIBYTE);
    }
}
