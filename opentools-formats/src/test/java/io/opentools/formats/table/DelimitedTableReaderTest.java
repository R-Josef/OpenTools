package io.opentools.formats.table;

import io.opentools.api.tool.OpenToolException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class DelimitedTableReaderTest {
    @TempDir
    Path tempDir;

    @Test
    void readsQuotedMultilineFieldsAndEscapedQuotes() throws Exception {
        Path input = tempDir.resolve("quoted.tsv");
        Files.writeString(input,
                "id\tnote\tvalue\n"
                        + "a\t\"line one\n"
                        + "line \"\"two\"\"\"\t1\n"
                        + "b\tplain\t2\n");

        try (DelimitedTableReader reader = new DelimitedTableReader(input, '\t')) {
            assertEquals(List.of("id", "note", "value"), reader.nextRow());
            assertEquals(List.of("a", "line one\nline \"two\"", "1"), reader.nextRow());
            assertEquals(List.of("b", "plain", "2"), reader.nextRow());
        }
    }

    @Test
    void reportsUnclosedQuotedField() throws Exception {
        Path input = tempDir.resolve("broken.tsv");
        Files.writeString(input, "id\tnote\na\t\"not closed\n");

        try (DelimitedTableReader reader = new DelimitedTableReader(input, '\t')) {
            reader.nextRow();
            OpenToolException exception = assertThrows(
                    OpenToolException.class,
                    reader::nextRow
            );
            assertEquals("DELIMITED_TABLE_MALFORMED", exception.code());
        }
    }
}
