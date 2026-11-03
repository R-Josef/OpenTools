# MEME Motif Discovery

`meme.run` · Sequence · v1.0.0

## What this tool does

Run MEME on a FASTA file, keep the motif XML, and import a motif summary table.

The external MEME executable performs discovery; OpenTools validates the command, captures the run log, copies `meme.xml` into the run directory, and parses motif id, name, width, contributing sites, and E-value.

## When to use it

- Discover conserved motifs in a promoter, peak, or protein family FASTA.
- Produce a motif XML that can be scanned against sequences with [MAST](tool:mast.run).
- Keep motif discovery parameters and output in the same reproducible run record.

## Prepare the input

- Declared formats: `FASTA`
- DNA or protein sequences with unique identifiers.
- MEME Suite installed and on `PATH`, or set `OPENTOOLS_MEME`, or provide `executablePath`.
- The tool does not bundle MEME; the executable is an external dependency.

### Input sketch

```text
>seq1
ACGTACGTACGTACGT
>seq2
ACGTACGTACGTACGT
```

## Recommended workflow

1. Check sequence alphabet and remove duplicate or very short records.
2. Start with `molecule=DNA`, 10 motifs, widths 6–50, and 4 threads.
3. Inspect the motif summary; empty XML is a valid no-motif result.
4. Feed the produced XML to [MAST](tool:mast.run) for scanning.
5. Retain the XML, summary TSV, command log, and parameters.

## How to choose parameters

| Parameter | Required | Default | Guidance |
| --- | --- | --- | --- |
| `input` Input sequences | yes | empty | DNA or protein FASTA. |
| `molecule` Molecule type | no | DNA | DNA or PROTEIN; selects `-dna` or `-protein`. |
| `motifs` Motif count | no | 10 | Maximum motifs to discover. |
| `minMotifWidth` Minimum motif width | no | 6 | Minimum motif width. |
| `maxMotifWidth` Maximum motif width | no | 50 | Maximum motif width; must be at least the minimum. |
| `threads` Threads | no | 4 | MEME worker threads. |
| `timeoutMinutes` Timeout minutes | no | 60 | Maximum command runtime. |
| `executablePath` MEME executable | no | empty | Optional explicit executable path. |
| `output` Output XML | no | empty | Leave blank to save `meme.xml` in the run directory. |
| `extraArgs` Extra MEME arguments | no | empty | Quote-aware arguments; reserved options are rejected. |
| `overwrite` Overwrite outputs | no | false | Allow replacing existing outputs. |

## Read the result

- Output formats: `MEME XML` and `TSV`.
- The TSV columns are `motif`, `name`, `width`, `sites`, and `evalue`.
- `metrics.motifs` and `metrics.sites` summarize the parsed XML.
- A valid run with no motifs produces a header-only TSV and zero metrics; review the MEME log before treating it as a failure.

## Minimal command example

```powershell
opentools run meme.run --param input=C:\data\promoters.fa --param motifs=10 --param molecule=DNA
```

Replace placeholder paths with real workspace files. Keep parameter IDs unchanged.

## Checks and failure boundaries

- MEME must be installed; a missing executable fails with `EXTERNAL_TOOL_MISSING`.
- The command uses argument arrays, not shell interpolation; `extraArgs` is quote-aware and cannot override reserved options.
- Protein mode requires a protein FASTA; mixing alphabets can produce an empty or failed run.
- The XML is copied before parsing; malformed XML fails with `MEME_XML_INVALID`.
- Existing outputs are protected by default.

## Related next steps

- [MAST Motif Scanning](tool:mast.run)
- [FASTA Statistics](tool:fasta.stats)
- [MEME Motif Discovery](tool:meme.run)

[Back to documentation home](opentools-doc:README.md)
