# Tables

Table tools cover preview, filtering, column selection, joins, transpose,
deduplication, pivot, and regex replacement. The shared reader supports quoting
and multiline fields.

## Cautions

- The first row should contain unique column names.
- Join right-side indexing is bounded.
- Pivot supports built-in aggregations, not arbitrary expressions.
- DISK/AUTO transpose uses temporary SQLite and needs writable disk space.
- Record-level table parallelism is explicit opt-in; AUTO may remain sequential.

The in-app Docs tree exposes all 8 tools.
