# Tool reference

OpenTools `0.10.2-SNAPSHOT` registers 101 built-in tools. The in-app Docs tree lists
all of them. Each tool page can start with a conventional Markdown guide and
then continues with a generated reference from the active language files and
`ToolDescriptor`. The generated section includes:

- purpose and appropriate use cases;
- input and output formats;
- parameter title, ID, type, default, options, and range;
- a PowerShell/CLI example;
- category-specific cautions and failure boundaries.

Host guide path:

```text
docs/<language>/tool/<Tool ID>.md
```

Plugin guide path:

```text
META-INF/opentools/docs/<language>/tool/<Tool ID>.md
```

All 101 built-in tools ship with English and Chinese guides. Guides are discovered
automatically and no documentation registration API is required. Without a
guide, only the generated reference is shown.

## Categories

| Category | Entry | Tools |
| --- | --- | --- |
| Sequence | [Sequence](categories/sequence.md) | 17 |
| Annotation and BED | [Genome annotation / Annotation](categories/annotation-bed.md) | 15 |
| Tables | [Table](categories/table.md) | 8 |
| Alignment and phylogeny | [Alignment / Phylogeny / Comparative genomics](categories/alignment-phylogeny.md) | 18 |
| Expression and enrichment | [Expression / Enrichment](categories/expression-enrichment.md) | 8 |
| HTS | [HTS](categories/hts.md) | 6 |
| Visualization | [Visualization](categories/visualization.md) | 21 |
| Plugin | [Plugin tools](categories/plugin.md) | bundled |

Inspect tools from the terminal with:

```powershell
OpenToolsCLI.exe tools list --json
OpenToolsCLI.exe tools describe <tool-id> --schema
```

Plugin-provided tools and their discovered guides appear at runtime but do not
count toward the 101 built-in baseline.
