package io.opentools.analysis.sequence;

import io.opentools.api.tool.OpenToolException;
import io.opentools.formats.fastq.FastqReader;
import io.opentools.formats.fastq.FastqRecord;

import java.nio.file.Path;
import java.util.Locale;

public enum FastqQualityEncoding {
    PHRED_33(33, false),
    PHRED_64(64, false),
    SOLEXA_64(64, true);

    private final int asciiOffset;
    private final boolean solexa;

    FastqQualityEncoding(int asciiOffset, boolean solexa) {
        this.asciiOffset = asciiOffset;
        this.solexa = solexa;
    }

    public double score(char encoded) {
        int raw = encoded - asciiOffset;
        int minimum = this == PHRED_33 ? 0 : this == PHRED_64 ? 0 : -5;
        int maximum = this == PHRED_33 ? 93 : 62;
        if (raw < minimum || raw > maximum) {
            throw invalid(encoded, null);
        }
        if (!solexa) {
            return raw;
        }
        return 10.0 * Math.log10(1.0 + Math.pow(10.0, raw / 10.0));
    }

    public int asciiOffset() {
        return asciiOffset;
    }

    public boolean solexa() {
        return solexa;
    }

    public static Resolution resolve(Path input, String requested) throws Exception {
        FastqQualityEncoding encoding = explicit(requested);
        if (encoding != null) {
            return new Resolution(encoding, false, false, -1, -1);
        }
        int minimum = 127;
        int maximum = 0;
        try (FastqReader reader = new FastqReader(input)) {
            FastqRecord record;
            while ((record = reader.next()) != null) {
                for (int index = 0; index < record.quality().length(); index++) {
                    int value = record.quality().charAt(index);
                    minimum = Math.min(minimum, value);
                    maximum = Math.max(maximum, value);
                }
            }
        }
        return detect(minimum, maximum);
    }

    public static Resolution detect(int minimum, int maximum) {
        if (maximum == 0 || minimum == 127) {
            return new Resolution(PHRED_33, true, true, -1, -1);
        }
        if (minimum < 33 || maximum > 126) {
            throw new OpenToolException(
                    "FASTQ_QUALITY_INVALID",
                    "FASTQ quality characters must use printable ASCII 33-126.",
                    "Check the FASTQ quality encoding."
            );
        }
        boolean ambiguous = minimum >= 59 && maximum <= 74;
        FastqQualityEncoding selected = minimum >= 64 && maximum > 74
                ? PHRED_64 : PHRED_33;
        return new Resolution(selected, true, ambiguous, minimum, maximum);
    }

    public static FastqQualityEncoding explicit(String requested) {
        if (requested == null || requested.isBlank() || "AUTO".equalsIgnoreCase(requested)) {
            return null;
        }
        try {
            return valueOf(requested.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new OpenToolException(
                    "FASTQ_QUALITY_ENCODING_UNSUPPORTED",
                    "Unsupported FASTQ quality encoding: " + requested,
                    "Use AUTO, PHRED_33, PHRED_64, or SOLEXA_64."
            );
        }
    }

    private static OpenToolException invalid(char encoded, String recordId) {
        String context = recordId == null ? "" : " in record " + recordId;
        return new OpenToolException(
                "FASTQ_QUALITY_INVALID",
                "Quality character U+" + Integer.toHexString(encoded).toUpperCase(Locale.ROOT)
                        + " is invalid for the selected encoding" + context + ".",
                "Choose the encoding used by the source sequencer or use AUTO."
        );
    }

    public record Resolution(
            FastqQualityEncoding encoding,
            boolean autodetected,
            boolean ambiguous,
            int observedMinimumAscii,
            int observedMaximumAscii
    ) {
    }
}
