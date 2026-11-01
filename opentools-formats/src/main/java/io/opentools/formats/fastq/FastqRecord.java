package io.opentools.formats.fastq;

public record FastqRecord(String id, String header, String sequence, String quality, long lineNumber) {
    public FastqRecord {
        if (sequence.length() != quality.length()) {
            throw new IllegalArgumentException("FASTQ sequence and quality lengths differ");
        }
    }
}
