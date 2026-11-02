package io.opentools.formats.fastq;

import io.opentools.formats.fasta.FastaRecord;
import io.opentools.formats.fasta.FastaWriter;

import java.io.BufferedWriter;
import java.nio.file.Path;

public final class FastqFastaConverter {
    private FastqFastaConverter() {
    }

    public static long convert(Path input, Path output) throws Exception {
        long[] totals = new long[1];
        io.opentools.formats.FormatToolSupport.writeAtomically(output, writer -> {
            long count = 0;
            try (FastqReader reader = new FastqReader(input)) {
                FastqRecord record;
                while ((record = reader.next()) != null) {
                    FastaWriter.write(writer, new FastaRecord(record.id(), record.header(), record.sequence()), 80);
                    count++;
                }
            }
            totals[0] = count;
        });
        return totals[0];
    }
}
