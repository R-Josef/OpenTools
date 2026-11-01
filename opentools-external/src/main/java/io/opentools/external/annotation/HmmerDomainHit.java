package io.opentools.external.annotation;

public record HmmerDomainHit(
        String targetName,
        String targetAccession,
        String queryName,
        double fullEvalue,
        double fullScore,
        double independentEvalue,
        long alignmentFrom,
        long alignmentTo,
        long hmmFrom,
        long hmmTo,
        double accuracy,
        String description
) {
}
