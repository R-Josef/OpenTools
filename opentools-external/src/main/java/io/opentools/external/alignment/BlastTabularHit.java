package io.opentools.external.alignment;

public record BlastTabularHit(
        String query,
        String subject,
        double percentIdentity,
        long alignmentLength,
        long mismatches,
        long gapOpenings,
        long queryStart,
        long queryEnd,
        long subjectStart,
        long subjectEnd,
        double evalue,
        double bitscore
) {
}
