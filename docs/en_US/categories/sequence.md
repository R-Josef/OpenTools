# Sequence processing

Sequence tools cover FASTA/FASTQ statistics, filtering, conversion, extraction,
ORF prediction, SSR mining, and CDS translation. Most record-oriented tools stream
input and accept gzip-compressed files.

## Cautions

- FASTA subsequences use 1-based inclusive coordinates.
- One very large record can still require substantial memory.
- Automatic sequence typing is alphabet-based.
- FASTQ AUTO cannot distinguish Phred+64 from Solexa+64 in every file.
- Use a `.gz` output suffix only with tools that support compressed output.

The in-app Docs tree exposes all 14 built-in sequence tools.
