package io.opentools.formats.bed;

import java.io.BufferedWriter;
import java.io.IOException;

public final class BedWriter {
    private BedWriter() {
    }

    public static void write(BufferedWriter writer, BedRecord record) throws IOException {
        writer.write(record.seqId());
        writer.write('\t');
        writer.write(Long.toString(record.start()));
        writer.write('\t');
        writer.write(Long.toString(record.end()));
        writer.write('\t');
        writer.write(record.name());
        writer.write('\t');
        writer.write(record.score());
        writer.write('\t');
        writer.write(Character.toString(record.strand()));
        for (String field : record.extraFields()) {
            writer.write('\t');
            writer.write(field);
        }
        writer.newLine();
    }
}
