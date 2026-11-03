# 管理 GO/KEGG 资源包

`resource.manage` · 富集 · v1.0.0

## 一句话说明

列出、导入并启用带版本的本地 GO/KEGG 资源包，让富集任务直接按资源包 ID 运行，不必每次重选文件。

每个资源包都是不可变的，记录 SHA-256 和条目数。新包不会删除旧包，因此启用旧包就是回滚。

## 什么时候使用

- 一次安装 GO OBO 与 Gene-GO 注释，在多个项目中复用。
- 安装 KEGG 基因-通路映射，并在不同版本间切换。
- 追溯某次富集使用的是哪个资源版本和摘要。

## 输入准备

- 声明格式：`GO OBO`、`Gene-GO TSV`、`Gene-pathway TSV`
- GO 导入需要 `obo`；如需从资源包运行 GO 富集，再加 `annotations`。
- KEGG 导入需要 `mapping`；`pathwayNames` 可选。
- 资源包目录由 `OPENTOOLS_RESOURCES`、`-Dopentools.resources.dir` 或 `~/.opentools/resources` 决定。

### 输入示意

```text
gene1	GO:0005737;GO:0006412
gene2	GO:0006412
```

## 推荐运行步骤

1. 从官方来源下载 GO OBO 与注释文件，或 KEGG 基因-通路映射。
2. 用 `action=IMPORT_GO` 或 `IMPORT_KEGG` 导入，检查结果中的条目数。
3. 用 `action=LIST` 记录当前资源包 ID、版本和摘要。
4. 在 `go.enrich`、`go.level` 或 `kegg.enrich` 中把 `resourceId` 设为该 ID。
5. 需要回滚时 `ACTIVATE` 旧包；旧文件仍然保留。

## 参数怎么选

| 参数 | 必填 | 默认值 | 选择建议 |
| --- | --- | --- | --- |
| `action` 操作 | 否 | LIST | LIST 查看；IMPORT_GO/IMPORT_KEGG 导入不可变资源包；ACTIVATE 切换当前启用包。 |
| `kind` 列表过滤 | 否 | ALL | 把 LIST 限制为 GO、KEGG 或全部。 |
| `id` 资源包 ID | 否 | 空 | 导入时可指定稳定 ID，或作为 ACTIVATE 的目标。只允许字母、数字、点、下划线和连字符。 |
| `name` 资源包名称 | 否 | 空 | 显示名称；默认使用导入文件名。 |
| `version` 资源包版本 | 否 | 空 | 例如 go-basic-2026-11；留空时使用导入日期。 |
| `obo` GO OBO 文件 | 否 | 空 | IMPORT_GO 必填；至少包含一个 [Term] 段。 |
| `annotations` Gene-GO 注释 | 否 | 空 | 导入时可选；从该包运行 GO 富集时需要。 |
| `mapping` 基因-通路映射 | 否 | 空 | IMPORT_KEGG 必填；两列，基因与通路 ID。 |
| `pathwayNames` 通路名称 | 否 | 空 | 可选的通路/名称两列表。 |
| `activate` 导入后启用 | 否 | true | 把新包设为当前启用包，并保留旧包。 |

## 结果怎么读

- 结果表列出资源包 ID、类型、名称、版本、是否启用、条目数和摘要前缀。
- 导入结果还会输出资源包目录下的 `pack.json`。
- 富集结果记录 `resourceId`、`resourceVersion` 和 `resourceSha256`。
- LIST 能发现缺失的资源文件；重新导入或启用其他包即可。

## 最小运行示例

```powershell
opentools run resource.manage --param action=IMPORT_GO --param obo=C:\data\go-basic.obo --param annotations=C:\data\gene-go.tsv --param id=go-basic-2026-11
```

把占位路径替换为工作区中的真实文件。参数名必须保持原样，布尔值使用 `true` 或 `false`。

## 检查清单与失败边界

- 资源包不可变；重复使用已有 ID 会失败，不会覆盖历史。
- 导入前先解析 OBO、注释和映射；坏文件不会替换当前启用包。
- 资源包按用户共享，不按项目隔离；每次运行的清单记录所用摘要。
- 旧包不会自动删除；确认没有运行依赖后再手工清理目录。

## 相关下一步

- [GO 富集](tool:go.enrich)
- [GO term 层级](tool:go.level)
- [KEGG 富集](tool:kegg.enrich)

[返回文档首页](opentools-doc:README.md)
