package io.opentools.formats.fasta;

import java.io.BufferedWriter;
import java.io.IOException;

public final class FastaWriter {
    private FastaWriter() {
    }

    public static void write(BufferedWriter writer, FastaRecord record, int lineWidth) throws IOException {
        writer.write(">");
        writer.write(record.header());
        writer.newLine();
        String sequence = record.sequence();
        for (int start = 0; start < sequence.length(); start += lineWidth) {
            writer.write(sequence, start, Math.min(lineWidth, sequence.length() - start));
            writer.newLine();
        }
        if (sequence.isEmpty()) {
            writer.newLine();
        }
    }
}
