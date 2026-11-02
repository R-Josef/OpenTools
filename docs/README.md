# OpenTools documentation

The in-app Docs browser loads the directory matching the active interface
language:

| Language | Docs root |
| --- | --- |
| English (`en_US`) | [`en_US/`](en_US/README.md) |
| Simplified Chinese (`zh_CN`) | [`zh_CN/`](zh_CN/README.md) |

Tool pages can start with a Markdown guide discovered from
`<language>/categories/<category>/<Tool ID>.md`. Plugin guides use the same
layout under `META-INF/opentools/docs/` in the plugin JAR. The custom guide is
followed by a separator and a reference generated from the same
`ToolDescriptor` and tool text resolver as the GUI, CLI, and MCP endpoints.
Built-in tools use host language files; plugin tools use their own JAR-localized
resources. Each documentation language directory contains its own Markdown
links, screenshots, categories, and image resources so relative navigation stays
inside a single language root.

Application resources are packaged under `/docs/en_US/` and `/docs/zh_CN/`. A
custom language that has no bundled guide falls back explicitly to English; task
parameter references still use the active language keys with English fallback.
