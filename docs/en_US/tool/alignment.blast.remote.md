# Remote BLAST Search

`alignment.blast.remote` · Alignment · v1.0.0

## What this tool does

Upload a query to the NCBI BLAST URL API, poll for completion, and save tabular hits.

It performs one explicit, reproducible analysis step. The run record preserves normalized parameters, input hashes, and logs; always review the source data, settings, and biological interpretation before publication.

## When to use it

- Run local alignment, multiple-sequence alignment, phylogenetic modeling, or normalize their outputs.
- Use it when one step needs preserved parameters, logs, and outputs for repeatability or troubleshooting.

## Prepare the input

- Declared formats: `FASTA`
- Required parameters: `query`, `program`
- Confirm query and subject molecule types and formats; for external tools, check executable paths, versions, threads, and timeouts.

### Input sketch

**query**

```text
>seq1
ATGCGTACGT
>seq2
ATGCGTTCGT
```


## Recommended workflow

1. Confirm that file formats, identifiers or column names, and coordinate conventions agree.
2. Validate with defaults or the recommendations below to catch paths, headers, and type errors first.
3. After submission, inspect the summary, result table, and artifacts instead of relying only on the success status.
4. Keep the original inputs, parameters, run log, and output hashes when the result must be reproducible.

## How to choose parameters

| Parameter | Required | Default | Guidance |
| --- | --- | --- | --- |
| `query` Query FASTA | yes | empty | Select a complete real input file; do not submit the placeholder example path. Nucleotide or protein query. The sequence is sent to NCBI only with allowUpload. |
| `program` BLAST program | yes | blastn | Prefer the default mode and switch only when the input type or analysis goal requires it. NCBI BLAST program name. |
| `database` Database | no | nt | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. NCBI database name such as nt, nr, refseq_protein, or swissprot. |
| `maxHits` Maximum hits | no | 100 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. HITLIST_SIZE requested from NCBI. |
| `allowUpload` Allow upload | no | false | Start with the default and change it only after understanding the explanation. Explicitly confirm that the query is sent to NCBI. |
| `timeoutSeconds` Timeout | no | 600 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Total time to wait for the remote search. |
| `pollSeconds` Poll interval | no | 5 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Seconds between status checks. |
| `baseUrl` Service URL | no | https://blast.ncbi.nlm.nih.gov/Blast.cgi | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. NCBI BLAST URL API endpoint. HTTPS is required except for loopback testing. |
| `output` Output table | no | empty | Leave blank to write inside the run directory; custom paths should stay in the workspace and use the correct extension. Leave blank to save in the run directory. |
| `overwrite` Overwrite output | no | false | Keep `false` unless the target is known to be safe to replace. Allow replacing an existing custom output file. |

## Read the result

- Output formats: `TSV`
- Results depend on the selected inputs and parameters; review output files, summary metrics, and diagnostics against the experimental design.
- The result panel is the quick-review surface; complete data, manifests, and logs live in the run directory.

## Minimal command example

```powershell
opentools run alignment.blast.remote --param query=C:\data\query.fasta --param program=blastn
```

Replace placeholder paths with real workspace files. Keep parameter IDs unchanged and use `true` or `false` for booleans.

## Checks and failure boundaries

- External program versions and arguments can change hits, alignments, or trees; record versions and complete commands for publication.
- Existing outputs are protected by default; only consider `overwrite` after explicitly choosing the target path.

## Related next steps

- [trimAl Alignment Trimming](tool:alignment.trim)
- [Reciprocal Best BLAST Hits](tool:alignment.blast.reciprocal)
- [BLAST Alignment Viewer Data](tool:alignment.blast.alignments)

[Back to documentation home](opentools-doc:README.md)
