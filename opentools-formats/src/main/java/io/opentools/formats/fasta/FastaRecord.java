package io.opentools.formats.fasta;

public record FastaRecord(String id, String header, String sequence) {
    public String fullHeader() {
        return header;
    }
}
