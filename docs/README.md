# OpenTools documentation

The in-app Docs browser loads the directory matching the active interface
language:

| Language | Docs root |
| --- | --- |
| English (`en_US`) | [`en_US/`](en_US/README.md) |
| Simplified Chinese (`zh_CN`) | [`zh_CN/`](zh_CN/README.md) |

Task pages are generated from the same `ToolDescriptor` and language files as the
GUI, CLI, and MCP endpoints. Each language directory contains its own Markdown
links, screenshots, categories, and image resources so relative navigation stays
inside a single language root.

Application resources are packaged under `/docs/en_US/` and `/docs/zh_CN/`. A
custom language that has no bundled guide falls back explicitly to English; task
parameter references still use the active language keys with English fallback.
