# Expression and enrichment

Expression tools normalize matrices, calculate correlation and PCA, and prepare
differential-expression input. Enrichment tools support offline GO levels,
hypergeometric tests, Benjamini-Hochberg correction, and KEGG.

## Cautions

- Expression matrices need unique row IDs and parseable numeric values.
- Normalization does not replace experimental design or batch correction.
- GO/KEGG annotation versions affect results and should be retained.
- KEGG enrichment requires a matching background and annotation table.
- Multiple-testing results need effect-size and study-design interpretation.

The in-app Docs tree exposes all 7 tools.
