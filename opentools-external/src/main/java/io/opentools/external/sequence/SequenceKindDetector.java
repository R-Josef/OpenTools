package io.opentools.external.sequence;

import io.opentools.formats.fasta.FastaReader;
import io.opentools.formats.fasta.FastaRecord;

import java.nio.file.Path;
import java.util.Locale;

public final class SequenceKindDetector {
    public SequenceKind detect(Path fasta) throws Exception {
        try (FastaReader reader = new FastaReader(fasta)) {
            FastaRecord record = reader.next();
            if (record == null || record.sequence().isBlank()) {
                return SequenceKind.UNKNOWN;
            }
            String sequence = record.sequence().toUpperCase(Locale.ROOT);
            String proteinMarkers = "EFILPQZJOX*";
            int canonicalNucleotides = 0;
            int letters = 0;
            for (int i = 0; i < sequence.length() && letters < 20_000; i++) {
                char base = sequence.charAt(i);
                if (!Character.isLetter(base) && base != '*') {
                    continue;
                }
                letters++;
                if ("ACGTU".indexOf(base) >= 0) {
                    canonicalNucleotides++;
                }
                if (proteinMarkers.indexOf(base) >= 0) {
                    return SequenceKind.PROTEIN;
                }
            }
            if (letters == 0) {
                return SequenceKind.UNKNOWN;
            }
            return canonicalNucleotides / (double) letters >= 0.85
                    ? SequenceKind.NUCLEOTIDE
                    : SequenceKind.PROTEIN;
        }
    }
}
