# OpenTools 文档

这些文档面向终端用户、自动化调用者和第三方插件开发者。简体中文界面从
`docs/zh_CN` 加载 Markdown，因此 GitHub、便携包和桌面内帮助保持一致。英文界面
使用对应的 `docs/en_US` 内容。

## 按目标阅读

| 目标 | 文档 |
| --- | --- |
| 第一次运行 | [快速开始](quick-start.md) |
| 安装或升级 | [安装与运行](installation.md) |
| 查找工具 | [工具参考](tool-reference.md) |
| 理解输入输出 | [文件格式](file-formats.md) |
| 命令行或 Agent | [CLI](cli.md)、[MCP](mcp.md) |
| 把任务串成 DAG | [工作流](workflow.md) |
| 配置外部程序 | [外部工具](external-tools.md) |
| 开发插件 | [插件开发者指南](plugin-development.md) |
| Plugin API 与 Javadoc | [Plugin API 2.1](plugin-api.md) |
| UI 与文档开发 | [界面、Markdown 和视觉验证](ui-and-docs.md) |
| 排查失败 | [错误与退出码](errors-exit-codes.md)、[FAQ](faq.md) |
| 选择发布物 | [发布包说明](release-packages.md) |

## 分类入口

- [序列处理](categories/sequence.md)
- [注释与 BED](categories/annotation-bed.md)
- [表格](categories/table.md)
- [比对与系统发育](categories/alignment-phylogeny.md)
- [表达与富集](categories/expression-enrichment.md)
- [HTS/BAM/VCF](categories/hts.md)
- [可视化](categories/visualization.md)
- [插件工具](categories/plugin.md)
- [CLI 与 MCP](cli.md)
- [工作流](workflow.md)
- [外部工具](external-tools.md)
- [FAQ](faq.md)

内置 Docs 还会在左侧目录中列出全部 92 个工具。工具页的参数名称、说明、默认值、
类型、选项和范围来自当前注册的 `ToolDescriptor` 与语言文件，避免文档和实际
Schema 长期分离。
