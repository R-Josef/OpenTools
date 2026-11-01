package io.opentools.formats.gxf;

import java.io.BufferedWriter;
import java.io.IOException;

public final class GxfWriter {
    private GxfWriter() {
    }

    public static void write(BufferedWriter writer, GxfRecord record) throws IOException {
        writer.write(record.seqId());
        writer.write('\t');
        writer.write(record.source());
        writer.write('\t');
        writer.write(record.type());
        writer.write('\t');
        writer.write(Long.toString(record.start()));
        writer.write('\t');
        writer.write(Long.toString(record.end()));
        writer.write('\t');
        writer.write(record.score());
        writer.write('\t');
        writer.write(Character.toString(record.strand()));
        writer.write('\t');
        writer.write(record.phase());
        writer.write('\t');
        writer.write(record.rawAttributes());
        writer.newLine();
    }
}
