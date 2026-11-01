package io.opentools.formats.table;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

public final class DelimitedTableWriter {
    private DelimitedTableWriter() {
    }

    public static void writeRow(Writer writer, List<String> fields, char delimiter)
            throws IOException {
        for (int index = 0; index < fields.size(); index++) {
            if (index > 0) {
                writer.write(delimiter);
            }
            writeField(writer, fields.get(index) == null ? "" : fields.get(index), delimiter);
        }
        writer.write('\n');
    }

    public static void writeField(Writer writer, String value, char delimiter)
            throws IOException {
        boolean quote = value.indexOf(delimiter) >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        if (!quote) {
            writer.write(value);
            return;
        }
        writer.write('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '"') {
                writer.write('"');
            }
            writer.write(character);
        }
        writer.write('"');
    }
}
