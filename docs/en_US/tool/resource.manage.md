# Manage GO/KEGG Resource Packs

`resource.manage` · Enrichment · v1.0.0

## What this tool does

List, import, and activate versioned local GO and KEGG resource packs, then use them from enrichment tasks without re-selecting files.

It stores every pack immutably with a SHA-256 digest and record counts. Importing a new pack never deletes the old one, so activating an earlier pack is a rollback.

## When to use it

- Install a GO OBO and Gene-GO annotation pair once and reuse it across projects.
- Install a KEGG gene-pathway mapping and switch between releases.
- Audit which resource version and digest produced an enrichment result.

## Prepare the input

- Declared formats: `GO OBO`, `Gene-GO TSV`, `Gene-pathway TSV`
- GO import needs `obo`; add `annotations` when GO enrichment should read the pack.
- KEGG import needs `mapping`; `pathwayNames` is optional.
- Resource packs are stored under `OPENTOOLS_RESOURCES`, `-Dopentools.resources.dir`, or `~/.opentools/resources`.

### Input sketch

```text
gene1	GO:0005737;GO:0006412
gene2	GO:0006412
```

## Recommended workflow

1. Download a GO OBO and annotation file, or a KEGG gene-pathway mapping from an official source.
2. Run `resource.manage` with `action=IMPORT_GO` or `IMPORT_KEGG`, then confirm the counts in the result.
3. Run `action=LIST` to record the active pack id, version, and digest.
4. Run `go.enrich`, `go.level`, or `kegg.enrich` with `resourceId` set to the pack id.
5. To roll back, `ACTIVATE` an older pack; its files were retained.

## How to choose parameters

| Parameter | Required | Default | Guidance |
| --- | --- | --- | --- |
| `action` Action | no | LIST | LIST inspects packs; IMPORT_GO/IMPORT_KEGG create an immutable pack; ACTIVATE switches the active pack for its kind. |
| `kind` List filter | no | ALL | Restrict LIST to GO, KEGG, or all packs. |
| `id` Pack id | no | empty | Set a stable id at import time, or pass the id to activate. Only letters, digits, dot, underscore, and hyphen are allowed. |
| `name` Pack name | no | empty | Human-readable label; defaults to the imported file name. |
| `version` Pack version | no | empty | Label such as go-basic-2026-11; defaults to the import date. |
| `obo` GO OBO file | no | empty | Required for IMPORT_GO; must contain at least one [Term] stanza. |
| `annotations` Gene-GO annotation | no | empty | Optional at import; required later for GO enrichment from the pack. |
| `mapping` Gene-pathway mapping | no | empty | Required for IMPORT_KEGG; two columns, gene and pathway IDs. |
| `pathwayNames` Pathway names | no | empty | Optional two-column pathway/name table. |
| `activate` Activate after import | no | true | Make the new pack active and keep the previous pack installed. |

## Read the result

- The result table lists pack id, kind, name, version, active state, counts, and digest prefix.
- Import results also emit `pack.json` as an artifact under the pack directory.
- Enrichment results record `resourceId`, `resourceVersion`, and `resourceSha256`.
- A missing pack file is visible in LIST; re-import or activate another pack.

## Minimal command example

```powershell
opentools run resource.manage --param action=IMPORT_GO --param obo=C:\data\go-basic.obo --param annotations=C:\data\gene-go.tsv --param id=go-basic-2026-11
```

Replace placeholder paths with real workspace files. Keep parameter IDs unchanged and use `true` or `false` for booleans.

## Checks and failure boundaries

- Imports are immutable; reusing an existing id fails instead of overwriting history.
- The OBO, annotation, and mapping files are parsed before the registry changes, so a bad update leaves the old active pack intact.
- Resource packs are shared per user, not per project; the manifest records the digest used by each run.
- Existing packs are not deleted automatically; manage disk usage by removing old directories only after confirming no run needs them.

## Related next steps

- [GO Enrichment](tool:go.enrich)
- [GO Term Levels](tool:go.level)
- [KEGG Enrichment](tool:kegg.enrich)

[Back to documentation home](opentools-doc:README.md)
