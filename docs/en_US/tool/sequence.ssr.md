# SSR Mining

`sequence.ssr` · Sequence · v1.0.0

## What this tool does

Find perfect simple sequence repeats (SSRs, microsatellites) in nucleotide FASTA records.

The scanner tests motif lengths from 1 to `maxMotifLength` at each position, keeps the shortest motif that reaches `minRepeats`, and reports maximal perfect runs. Results are a TSV table with sequence ID, 1-based coordinates, motif, repeat count, and repeat length.

## When to use it

- Mine microsatellite candidates from a genome, chromosome, or transcript assembly.
- Count repeats by motif length before primer design or marker development.
- Check whether a repeat motif is present in a candidate sequence.

## Prepare the input

- Declared formats: `FASTA`
- Nucleotide sequences with standard A/C/G/T bases; lowercase is accepted and uppercased.
- Ambiguous bases such as N are not used as repeat motifs, but they do not stop the scan.

### Input sketch

```text
>chr1
ACACACACGTTATATATATC
```

## Recommended workflow

1. Check sequence identifiers and remove unintended vector or low-quality records.
2. Start with `maxMotifLength=6`, `minRepeats=3`, and the default `maxLoci`.
3. Inspect the locus table and the per-motif-length counts.
4. Keep the FASTA, parameters, and output manifest if the loci feed primer design or publication.
5. For large genomes, raise `maxLoci` only when you can review the output and disk usage.

## How to choose parameters

| Parameter | Required | Default | Guidance |
| --- | --- | --- | --- |
| `input` Input FASTA | yes | empty | Nucleotide FASTA, optionally gzip-compressed. |
| `maxMotifLength` Maximum motif length | no | 6 | Check every motif length from 1 through this value (maximum 10). |
| `minRepeats` Minimum repeats | no | 3 | Minimum consecutive motif copies; 2 catches shorter repeats but increases output. |
| `maxLoci` Maximum loci | no | 100000 | Stop after this many loci and mark the result truncated. |
| `output` Output TSV | no | empty | Leave blank to save in the run directory. |
| `overwrite` Overwrite output | no | false | Allow replacing an existing output. |

## Read the result

- Output format: `TSV` with `sequence`, `start`, `end`, `motif`, `repeats`, `length`, and `motif_length`.
- `start` and `end` are 1-based inclusive coordinates.
- `metrics.loci` is the number of loci; `metrics.repeatedBases` is the total repeated span.
- `metrics.truncated=true` and a final `# truncated at N loci` line mean the scan stopped at `maxLoci`.
- A header-only table means no perfect repeat passed the thresholds; that is a valid result, not a failure.

## Minimal command example

```powershell
opentools run sequence.ssr --param input=C:\data\genome.fa --param maxMotifLength=6 --param minRepeats=3
```

Replace placeholder paths with real workspace files. Keep parameter IDs unchanged.

## Checks and failure boundaries

- Only perfect repeats are reported; interrupted or compound repeats need a different tool.
- The shortest motif at a position wins, so `ATATAT` is reported as `AT`, not `ATATAT`.
- Ambiguous bases are skipped as motifs; a sequence of only N bases yields no loci.
- The scan is linear in sequence length times motif length; very large genomes can take time, and `maxLoci` protects output size but not runtime.
- Existing outputs are protected by default.

## Related next steps

- [Sequence ORF Finder](tool:sequence.orf)
- [FASTA Statistics](tool:fasta.stats)
- [FASTA Filter](tool:fasta.filter)

[Back to documentation home](opentools-doc:README.md)
