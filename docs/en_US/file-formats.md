# File formats

## FASTA and FASTQ

FASTA records start with `>` and FASTQ uses four-line records. Inputs may be
gzip-compressed. FASTA subsequences and GFF intervals use 1-based inclusive
coordinates. BED uses 0-based half-open coordinates.

FASTQ quality encodings include Phred+33, Phred+64, and Solexa+64. AUTO cannot
always distinguish Phred+64 from Solexa+64; choose explicitly when necessary.

## GFF3 and GTF

GFF3/GTF coordinates are 1-based inclusive. Sequence names must match the
reference FASTA. Readers report malformed columns, coordinates, strands, phases,
and attributes. Annotation repair applies only explicitly safe transformations.

## BED

BED3 and BED3+ are supported. The first fields are `chrom`, `start`, and `end`,
with `0 <= start < end`. Strand is `+` or `-`; additional fields are interpreted
by the selected task.

## Delimited text

TSV and CSV support RFC 4180-style quoting, escaped quotes, and fields spanning
multiple lines. The first row is normally the header. AUTO detects tabs, commas,
and semicolons; ambiguous data should use an explicit delimiter.

## SAM, BAM, and CRAM

These are read with HTSJDK. CRAM may need a matching reference FASTA. Indexed
BAM/CRAM reads use BAI/CRAI when available; some tasks fall back to a linear scan.

## VCF and BCF

Inputs may be plain VCF, bgzip VCF, or BCF. Index creation requires a `##contig`
dictionary and dictionary-ordered records. Compressed VCF uses TBI and BCF uses
CSI; plain uncompressed VCF is not indexed.

## Newick

Common Newick trees, branch lengths, support values, and standard quoted labels
are supported. Invalid brackets, missing semicolons, and unknown references are
reported as diagnostics.

## Images and tables

Plot tasks can emit PNG, SVG, PDF, and JSON plot models or data. PDF Chinese text
depends on available embeddable fonts; PNG and SVG preserve Unicode labels.
Table previews are bounded, while task artifacts contain complete output data.
