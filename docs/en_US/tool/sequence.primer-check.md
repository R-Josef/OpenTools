# Primer Check (e-PCR)

`sequence.primer-check` · Sequence · v1.0.0

## What this tool does

Locate primer pairs on a genome and report predicted amplicons.

The forward primer is searched on the plus strand; the reverse primer is reverse-complemented and also searched on the plus strand, so the pair amplifies across the region. Each forward match reports the nearest downstream reverse site that satisfies the product-size window.

## When to use it

- Check whether a primer pair is expected to amplify a genome or assembly.
- Estimate amplicon size and coordinates before laboratory work.
- Screen many primer pairs for multiple binding sites or unexpected products.

## Prepare the input

- Declared formats: `FASTA`, `TSV`
- Genome: nucleotide FASTA, optionally gzip-compressed.
- Primer table: a tab-separated header with `name`, `forward`, and `reverse` columns.
- Primer sequences accept A/C/G/T and IUPAC ambiguity codes (R/Y/S/W/K/M/B/D/H/V/N).
- The `reverse` column is the primer sequence as ordered; the tool uses its reverse complement for the search.

### Input sketch

```text
name	forward	reverse
p1	ACGTACGT	TGCATGCA
```

## Recommended workflow

1. Confirm primer orientation: `forward` anneals to the plus strand, `reverse` to the minus strand.
2. Start with `maxMismatches=0` to verify exact matches, then relax to 1–3 only if needed.
3. Set a product window that matches the expected amplicon; the default is 50–3000 bp.
4. Review each predicted product and its mismatch counts before ordering primers.
5. Retain the genome, primer table, parameters, and output TSV.

## How to choose parameters

| Parameter | Required | Default | Guidance |
| --- | --- | --- | --- |
| `genome` Genome FASTA | yes | empty | Nucleotide genome or contigs; both strands are searched. |
| `primers` Primer table | yes | empty | TSV with name, forward, and reverse columns; IUPAC bases are accepted. |
| `maxMismatches` Maximum mismatches | no | 0 | Allowed mismatches per primer; 0 requires exact matches. |
| `minProductSize` Minimum product size | no | 50 | Minimum amplicon length in bp. |
| `maxProductSize` Maximum product size | no | 3000 | Maximum amplicon length in bp. |
| `maxAmplicons` Maximum amplicons | no | 100000 | Stop after this many products and mark the result truncated. |
| `output` Output TSV | no | empty | Leave blank to save in the run directory. |
| `overwrite` Overwrite output | no | false | Allow replacing an existing output. |

## Read the result

- Output format: `TSV` with `primer`, `sequence`, `forward_start`, `forward_end`, `reverse_start`, `reverse_end`, `product_size`, `forward_mismatches`, and `reverse_mismatches`.
- Coordinates are 1-based inclusive; `product_size = reverse_end - forward_start + 1`.
- `metrics.amplicons` counts predicted products; `metrics.truncated` and a trailing `# truncated` line mark a cap.
- A header-only table means no pair passed the match and product-size filters; that is a valid negative result.

## Minimal command example

```powershell
opentools run sequence.primer-check --param genome=C:\data\genome.fa --param primers=C:\data\primers.tsv
```

Replace placeholder paths with real workspace files. Keep parameter IDs unchanged.

## Checks and failure boundaries

- Only the nearest downstream reverse site is reported for each forward match; distant secondary products are not enumerated.
- `maxMismatches` applies per primer; the output reports the observed count for each side.
- Ambiguous genome bases (for example N) count as mismatches unless the primer base is N.
- Large genomes and many primers require time and memory proportional to genome length times primer count; the match and amplicon caps bound output, not runtime.
- Primer rows are limited to 1000 pairs per run; split larger designs.
- Existing outputs are protected by default.

## Related next steps

- [SSR Mining](tool:sequence.ssr)
- [Sequence ORF Finder](tool:sequence.orf)
- [FASTA Statistics](tool:fasta.stats)

[Back to documentation home](opentools-doc:README.md)
