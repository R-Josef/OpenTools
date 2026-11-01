package io.opentools.external.alignment;

public record BlastHit(
        String query,
        String subject,
        double percentIdentity,
        long alignmentLength,
        double evalue,
        double bitscore
) {
}
