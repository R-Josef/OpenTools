# Collinearity Plot

`plot.collinearity` · Visualization · v1.1.0

## What this tool does

Plot MCScanX collinearity pairs as a gene-order dot plot between two annotated genomes.

Each genome is laid out by chromosome and gene order; every dot is a collinear gene pair. Blocks are colored by pair count so the dominant syntenic blocks stand out.

## When to use it

- Review MCScanX or `mcscan.import` output after running `plot.dotplot` or before a publication figure.
- Check whether a collinearity run is dominated by one block, has many unmapped genes, or is missing an expected chromosome pair.
- Compare two genome annotations when gene IDs and order are already validated.

## Prepare the input

- Declared formats: `TSV`, `CSV`
- The pairs table needs `block`, `gene_a`, and `gene_b` columns (names configurable).
- `annotationA` and `annotationB` are GFF3 or GTF files with `gene` features. If only `mRNA`/`transcript` features exist, those are used.
- Gene IDs come from GFF3 `ID` (the `gene:` prefix is removed) or GTF `gene_id`. They must match `gene_a`/`gene_b`.

### Input sketch

```text
block	block_score	gene_a	gene_b	pair_index
1	100	gA1	gB1	1
1	100	gA2	gB2	2
2	50	gA3	gB3	1
```

```text
##gff-version 3
chr1	test	gene	1	100	.	+	.	ID=gene:gA1
chr1	test	gene	200	300	.	+	.	ID=gene:gA2
```

## Recommended workflow

1. Import MCScanX output with `mcscan.import` and confirm the pair table.
2. Check that the annotation contains every gene ID used in the pairs table.
3. Run `plot.collinearity` with both annotations and the pairs table.
4. Read the plotted/unmapped counts; unmapped pairs usually mean a missing gene feature or an ID mismatch.
5. Keep the pairs table, both annotations, and the run manifest for reproducibility.

## How to choose parameters

| Parameter | Required | Default | Guidance |
| --- | --- | --- | --- |
| `input` Collinearity table | yes | empty | Output from `mcscan.import`; needs block and gene columns. |
| `annotationA` Genome A annotation | yes | empty | GFF3/GTF genes for genome A; IDs must match `gene_a`. |
| `annotationB` Genome B annotation | yes | empty | GFF3/GTF genes for genome B; IDs must match `gene_b`. |
| `blockColumn` Block column | no | block | Column identifying a collinearity block. |
| `geneAColumn` Genome A gene column | no | gene_a | Gene ID column for genome A. |
| `geneBColumn` Genome B gene column | no | gene_b | Gene ID column for genome B. |
| `maxBlocks` Colored blocks | no | 20 | Top blocks by pair count are colored; the rest are grouped as "Other blocks". |
| `title` Plot title | no | Collinearity | Chart title. |
| `subtitle` Plot subtitle | no | empty string | Optional subtitle. |
| `valueLabel` Value label | no | empty string | Optional Y-axis label override. |
| `width` Image width | no | 1200 | Export width in pixels. |
| `height` Image height | no | 800 | Export height in pixels. |
| `dpi` PDF DPI | no | 144 | PDF export resolution. |
| `exportFormats` Export formats | no | PNG_SVG_PDF | Plot artifact formats. |
| `output` Output prefix | no | empty | Leave blank to save in the run directory. |
| `overwrite` Overwrite outputs | no | false | Allow replacing existing outputs. |

## Read the result

- Output formats: `PNG`, `SVG`, `PDF`, `JSON`.
- X and Y are cumulative gene-order coordinates; chromosome boundaries are implicit in the gene order.
- `pairs` is the number of plotted gene pairs; `unmapped` counts pairs whose gene IDs were absent from an annotation.
- `genesA`/`genesB` and `chromosomesA`/`chromosomesB` describe the two annotated genomes.
- A dense diagonal indicates conserved order; off-diagonal clusters can indicate rearrangements or annotation issues.

## Minimal command example

```powershell
opentools run plot.collinearity --param input=C:\data\collinearity.tsv --param annotationA=C:\data\genomeA.gff3 --param annotationB=C:\data\genomeB.gff3
```

Replace placeholder paths with real workspace files. Keep parameter IDs unchanged.

## Checks and failure boundaries

- Empty pairs, or pairs whose genes are all missing from the annotations, fail with `COLLINEARITY_EMPTY`; inspect the `unmapped` count first.
- An annotation with no gene/mRNA/transcript features fails with `COLLINEARITY_ANNOTATION_EMPTY`.
- GFF3 `gene:` prefixes are stripped; other ID conventions (for example version suffixes) must be normalized before running.
- Existing outputs are protected by default; only use `overwrite` after explicitly choosing the target prefix.

## Related next steps

- [Import MCScanX Collinearity](tool:mcscan.import)
- [Dot Plot](tool:plot.dotplot)
- [BLAST Dot Plot](tool:plot.dotplot)

[Back to documentation home](opentools-doc:README.md)
