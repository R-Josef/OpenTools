# Annotation and BED

Annotation tools cover GFF3/GTF inspection, filtering, conversion, overlap,
representative transcripts, ID mapping, merge/split, safe repair, gene density,
and sequence extraction. BED statistics, BED/GFF conversion, Pfam import, and
eggNOG import are also in this category.

## Cautions

- GFF/GTF and BED use different coordinate systems.
- Genome and annotation sequence names, lengths, and strands must agree.
- Promoter and flank extraction follow transcript direction.
- Dense interval and disk-spill modes need adequate temporary space.
- Safe repair does not invent missing biological attributes.

The in-app Docs tree exposes all 15 related tools.
