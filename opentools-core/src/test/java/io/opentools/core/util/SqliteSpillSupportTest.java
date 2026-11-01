package io.opentools.core.util;

import io.opentools.api.tool.OpenToolException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class SqliteSpillSupportTest {
    @TempDir
    Path tempDir;

    @Test
    void enforcesConfiguredSpillSizeAndTranslatesFullDiskErrors() throws Exception {
        Path database = tempDir.resolve("spill.sqlite");
        Files.write(database, new byte[(int) SqliteSpillSupport.MEBIBYTE + 1]);

        OpenToolException limit = assertThrows(
                OpenToolException.class,
                () -> SqliteSpillSupport.enforceLimit(database, SqliteSpillSupport.MEBIBYTE)
        );
        assertEquals("SPILL_SPACE_LIMIT", limit.code());

        OpenToolException full = SqliteSpillSupport.translateSqlException(
                new SQLException("database or disk is full", "SQLITE_FULL", 13),
                "Transpose"
        );
        assertEquals("SPILL_DISK_FULL", full.code());
    }
}
