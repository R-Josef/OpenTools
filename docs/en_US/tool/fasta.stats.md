# FASTA statistics: example and interpretation

Use FASTA statistics as an initial quality-control step before filtering,
alignment, or feature extraction.

## A practical check

For a newly assembled genome, compare the sequence count, total bases, longest
sequence, and N50 with the expected chromosome or contig set. A very large N50
increase after filtering may indicate that low-coverage fragments were removed;
a large decrease may indicate that long records were dropped unexpectedly.

## Interpretation cautions

- N50 depends on the sequence-size distribution and is not a completeness score.
- Duplicate sequence identifiers can hide upstream preparation errors.
- Ambiguous bases are retained in statistics; inspect the N percentage before
  using the assembly for downstream coordinate work.
