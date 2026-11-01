package io.opentools.formats;

public enum FormatType {
    FASTA("FASTA"),
    FASTQ("FASTQ"),
    GFF3("GFF3"),
    GTF("GTF"),
    BED("BED"),
    VCF("VCF"),
    NEWICK("Newick"),
    TSV("TSV"),
    CSV("CSV"),
    UNKNOWN("Unknown");

    private final String displayName;

    FormatType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
