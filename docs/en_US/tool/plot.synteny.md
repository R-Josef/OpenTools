# Multi-species Synteny

`plot.synteny` · Visualization · v1.1.0

## What this tool does

Draw a chord/ribbon overview of collinearity links across three or more genomes.

Genomes are arranged around a circle; each genome arc is proportional to the genes that participate in the link table. Every cross-genome pair is a curve through the center, colored by block.

## When to use it

- Summarize MCScanX or orthology links among three or more genomes.
- See which genome pairs dominate a block or comparison.
- Produce a compact multi-species figure before a detailed pairwise collinearity plot.

## Prepare the input

- Declared formats: `TSV`, `CSV`, `GFF3`, `GTF`
- Pairs table columns: `genome_a`, `gene_a`, `genome_b`, `gene_b`; optional `block`.
- Combined annotation: one GFF3/GTF with gene features for all genomes. Gene IDs must be unique across the file.
- Genome labels come from the pairs table, not from the annotation.

### Input sketch

```text
block	genome_a	gene_a	genome_b	gene_b
1	A	gA1	B	gB1
1	A	gA2	B	gB2
2	A	gA3	C	gC3
3	B	gB1	C	gC1
```

## Recommended workflow

1. Confirm gene IDs are unique across the combined annotation.
2. Start with `maxLinks=5000`; raise it only when the figure remains readable.
3. Inspect genome arcs, link density, and block colors.
4. Use pairwise [Collinearity Plot](tool:plot.collinearity) for detailed dot plots.
5. Keep the pairs table, annotation, and run manifest.

## How to choose parameters

| Parameter | Required | Default | Guidance |
| --- | --- | --- | --- |
| `input` Cross-genome pairs | yes | empty | TSV with genome/gene columns for both sides. |
| `annotation` Combined annotation | yes | empty | GFF3/GTF with unique gene IDs for all genomes. |
| `blockColumn` Block column | no | block | Optional block identifier used for link colors. |
| `genomeAColumn` Genome A column | no | genome_a | Genome label for the first side. |
| `geneAColumn` Gene A column | no | gene_a | Gene ID for the first side. |
| `genomeBColumn` Genome B column | no | genome_b | Genome label for the second side. |
| `geneBColumn` Gene B column | no | gene_b | Gene ID for the second side. |
| `maxLinks` Maximum links | no | 5000 | Cap drawn links; the result records truncation. |
| `title` Plot title | no | Multi-species Synteny | Chart title. |
| `subtitle` Plot subtitle | no | empty | Optional subtitle. |
| `valueLabel` Value label | no | empty | Optional value label. |
| `width` Image width | no | 1200 | Export width in pixels. |
| `height` Image height | no | 800 | Export height in pixels. |
| `dpi` PDF DPI | no | 144 | PDF export resolution. |
| `exportFormats` Export formats | no | PNG_SVG_PDF | Plot artifact formats. |
| `output` Output prefix | no | empty | Leave blank to save in the run directory. |
| `overwrite` Overwrite outputs | no | false | Allow replacing existing outputs. |

## Read the result

- Output formats: `PNG`, `SVG`, `PDF`, `JSON`.
- Outer arcs are genomes; inner curves are links colored by block.
- `metrics.genomes`, `metrics.links`, `metrics.blocks`, and `metrics.unmapped` summarize the figure.
- `metrics.truncated=true` means the link cap was reached; the displayed figure is partial.

## Minimal command example

```powershell
opentools run plot.synteny --param input=C:\data\pairs.tsv --param annotation=C:\data\combined.gff3
```

Replace placeholder paths with real workspace files. Keep parameter IDs unchanged.

## Checks and failure boundaries

- Gene IDs must be unique across the combined annotation; duplicate IDs keep the first feature.
- Genes absent from the annotation are counted as `unmapped` and do not draw a link.
- Genome arcs use gene order, not chromosome length; this is a relationship overview, not a physical map.
- Too many links become a dense chord diagram; lower `maxLinks` or aggregate blocks.
- Existing outputs are protected by default.

## Related next steps

- [Collinearity Plot](tool:plot.collinearity)
- [Import MCScanX Collinearity](tool:mcscan.import)
- [Circos Overview](tool:plot.circos)

[Back to documentation home](opentools-doc:README.md)
