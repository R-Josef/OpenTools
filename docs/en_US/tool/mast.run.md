# MAST Motif Scanning

`mast.run` · Sequence · v1.0.0

## What this tool does

Run MAST with a MEME XML motif file against FASTA sequences, keep the hit XML, and import a one-based hit table.

The external MAST executable scans the sequences; OpenTools captures the command log, copies `mast.xml`, and parses sequence id, motif, coordinates, strand, p-value, and matched text.

## When to use it

- Scan a genome, promoter set, or protein set with motifs discovered by [MEME](tool:meme.run).
- Compare motif occurrence across sequences.
- Export hit coordinates for downstream overlap or annotation work.

## Prepare the input

- Declared formats: `MEME XML`, `FASTA`
- A `meme.xml` with motif definitions.
- DNA or protein FASTA matching the motif alphabet.
- MAST installed and on `PATH`, or set `OPENTOOLS_MAST`, or provide `executablePath`.
- MAST is not bundled; it is an external dependency.

### Input sketch

```text
motifs.xml  +  sequences.fa
```

## Recommended workflow

1. Confirm the motif XML and sequences use the same alphabet.
2. Run `mast.run` with the defaults and inspect the hit count.
3. Review coordinates and p-values in the hit TSV; a header-only table is a valid no-hit result.
4. Keep the XML, TSV, log, and parameters for reproducibility.
5. Use the coordinates as input to overlap, annotation, or visualization tasks.

## How to choose parameters

| Parameter | Required | Default | Guidance |
| --- | --- | --- | --- |
| `motifs` MEME motif XML | yes | empty | `meme.xml` from MEME or another MEME XML source. |
| `sequences` Sequences | yes | empty | DNA or protein FASTA to scan. |
| `timeoutMinutes` Timeout minutes | no | 60 | Maximum command runtime. |
| `executablePath` MAST executable | no | empty | Optional explicit executable path. |
| `output` Output XML | no | empty | Leave blank to save `mast.xml` in the run directory. |
| `extraArgs` Extra MAST arguments | no | empty | Quote-aware arguments; reserved options are rejected. |
| `overwrite` Overwrite outputs | no | false | Allow replacing existing outputs. |

## Read the result

- Output formats: `MAST XML` and `TSV`.
- TSV columns are `sequence`, `motif`, `start`, `end`, `strand`, `pvalue`, and `match`.
- Coordinates are 1-based inclusive; MAST XML positions are zero-based and are converted on import.
- `metrics.hits` counts hits; `metrics.sequences` counts distinct sequences with at least one hit.
- A header-only TSV means no hit passed MAST's thresholds; check the XML/log before treating it as a failure.

## Minimal command example

```powershell
opentools run mast.run --param motifs=C:\data\meme.xml --param sequences=C:\data\promoters.fa
```

Replace placeholder paths with real workspace files. Keep parameter IDs unchanged.

## Checks and failure boundaries

- MAST must be installed; a missing executable fails with `EXTERNAL_TOOL_MISSING`.
- Motif and sequence alphabets must agree; mixed DNA/protein input can fail or produce no hits.
- The parser supports both `motif` and `motiff` hit attributes and uses the `match` length for the end coordinate.
- Malformed XML fails with `MEME_XML_INVALID`.
- Existing outputs are protected by default.

## Related next steps

- [MEME Motif Discovery](tool:meme.run)
- [FASTA Statistics](tool:fasta.stats)
- [MAST Motif Scanning](tool:mast.run)

[Back to documentation home](opentools-doc:README.md)
