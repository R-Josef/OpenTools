# OpenTools language files

OpenTools loads interface text from this directory at startup. Language files
are external files and are not packaged into the application JAR.

File naming:

```text
en_US.properties
zh_CN.properties
xx_XX.properties
```

`en_US.properties` is the required fallback. If the selected language or an
individual key is missing, OpenTools uses English.

Create a new language by copying `en_US.properties`, translating the values,
and keeping the keys unchanged. The `language.name` value is shown in the
language selector.

The selection order is:

1. Explicit `--language` or `-Dopentools.language`.
2. The language saved from Settings.
3. The operating-system language.
4. `en_US`.

Restart the application after changing a language file. Missing or malformed
keys fall back to English rather than preventing startup.
