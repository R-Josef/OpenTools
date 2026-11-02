# Tool reference

OpenTools `0.8.1-SNAPSHOT` registers 86 built-in tools. The in-app Docs tree lists
all of them. Each tool page is generated from the active language files and
`ToolDescriptor`, including:

- purpose and appropriate use cases;
- input and output formats;
- parameter title, ID, type, default, options, and range;
- a PowerShell/CLI example;
- category-specific cautions and failure boundaries.

## Categories

| Category | Entry | Tools |
| --- | --- | --- |
| Sequence | [Sequence](categories/sequence.md) | 13 |
| Annotation and BED | [Genome annotation / Annotation](categories/annotation-bed.md) | 15 |
| Tables | [Table](categories/table.md) | 8 |
| Alignment and phylogeny | [Alignment / Phylogeny / Comparative genomics](categories/alignment-phylogeny.md) | 17 |
| Expression and enrichment | [Expression / Enrichment](categories/expression-enrichment.md) | 7 |
| HTS | [HTS](categories/hts.md) | 6 |
| Visualization | [Visualization](categories/visualization.md) | 20 |
| Plugin | [Plugin tools](categories/plugin.md) | bundled |

Inspect tools from the terminal with:

```powershell
OpenToolsCLI.exe tools list --json
OpenToolsCLI.exe tools describe <tool-id> --schema
```

Plugin-provided tools appear in the catalog but do not count toward the 86
built-in baseline.
