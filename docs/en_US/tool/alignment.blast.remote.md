# Remote BLAST Search

`alignment.blast.remote` · Alignment · v1.0.0

## What this tool does

Submit a query FASTA to the NCBI BLAST URL API, wait for the search to finish, and save the tabular hits.

It performs one explicit, reproducible analysis step. The run record preserves normalized parameters, input hashes, and logs; always review the source data, settings, and biological interpretation before publication.

## When to use it

- Search a public NCBI database when no local BLAST database is available.
- Use it when one step needs preserved parameters, logs, and outputs for repeatability or troubleshooting.
- Prefer local BLAST when the query must not leave the computer.

## Prepare the input

- Declared formats: `FASTA`
- Required parameters: `query`, `program`
- Confirm the molecule type matches the program and database. `blastn` needs a nucleotide database such as `nt`; `blastp` needs a protein database such as `nr`.
- The query is uploaded to NCBI only after `allowUpload=true`.

### Input sketch

```text
>seq1
ATGCGTACGTACGTACGT
>seq2
ATGCGTTCGTACGTACGT
```

## Recommended workflow

1. Confirm that the query is public or may be sent to NCBI.
2. Choose the program and a matching database, then set `allowUpload=true`.
3. Submit and watch the run log; the tool polls NCBI with the returned RID.
4. Inspect the hit count, RID, and output table before interpreting the result.

## How to choose parameters

| Parameter | Required | Default | Guidance |
| --- | --- | --- | --- |
| `query` Query FASTA | yes | empty | Select a complete real input file; the sequence is sent to NCBI only with `allowUpload`. |
| `program` BLAST program | yes | blastn | Use blastn for nucleotide queries, blastp for protein queries, and blastx/tblastn/tblastx for translated searches. |
| `database` Database | no | nt | Use a database matching the program, such as nt, nr, refseq_protein, or swissprot. |
| `maxHits` Maximum hits | no | 100 | Sets HITLIST_SIZE; lower it for a faster, smaller response. |
| `allowUpload` Allow upload | no | false | Must be true to submit. This is the explicit consent that the query leaves the computer. |
| `timeoutSeconds` Timeout | no | 600 | Total wait for the remote search; increase it for large databases or busy periods. |
| `pollSeconds` Poll interval | no | 5 | Seconds between status checks; do not set it below the NCBI service guidance. |
| `baseUrl` Service URL | no | https://blast.ncbi.nlm.nih.gov/Blast.cgi | Keep the default unless a trusted HTTPS mirror is required. HTTP is accepted only on loopback for testing. |
| `output` Output table | no | empty | Leave blank to write inside the run directory; custom paths should stay in the workspace. |
| `overwrite` Overwrite output | no | false | Keep `false` unless the target is known to be safe to replace. |

## Read the result

- Output formats: `TSV`
- The table uses BLAST outfmt 6 columns; the result summary records the RID, program, database, HSP count, and elapsed time.
- A RID is the NCBI handle for this search. Keep it in the run record when the result must be audited.
- No hits is a successful empty result, not a failure.

## Minimal command example

```powershell
opentools run alignment.blast.remote --param query=C:\data\query.fasta --param program=blastn --param database=nt --param allowUpload=true
```

Replace placeholder paths with real workspace files. Keep parameter IDs unchanged and use `true` or `false` for booleans.

## Checks and failure boundaries

- The tool refuses to run without `allowUpload=true`; this keeps accidental uploads out of automation.
- The service URL must use HTTPS, except for a loopback test endpoint.
- NCBI can queue, rate-limit, or reject a request; the run log and error message retain the RID and HTTP status.
- Remote results depend on the database version at run time; record the date and database name for publication.
- Existing outputs are protected by default; only consider `overwrite` after explicitly choosing the target path.

## Related next steps

- [Local BLAST](tool:alignment.blast.local)
- [Dot Plot](tool:plot.dotplot)
- [Convert BLAST XML to Table](tool:alignment.blast.xml-to-table)

[Back to documentation home](opentools-doc:README.md)
