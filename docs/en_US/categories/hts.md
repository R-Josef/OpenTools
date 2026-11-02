# HTS / BAM / VCF

HTS tools cover SAM/BAM/CRAM statistics, indexed region subsetting, coverage,
VCF/BCF statistics and filtering, and BED mean coverage. The implementation has
been exercised with large local inputs; capacity depends on the machine and
parameters.

## Cautions

- CRAM may require an external reference.
- Indexed BAM/CRAM reads are usually faster, but indexes must match the file.
- Mate-overlap deduplication is optional and slower.
- VCF indexing requires a sequence dictionary and sorted records.
- Large BED region files need temporary-space and query-cost planning.

The in-app Docs tree exposes all 6 tools.
