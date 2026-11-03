# 远程 BLAST 搜索

`alignment.blast.remote` · 比对 · v1.0.0

## 一句话说明

把查询 FASTA 提交到 NCBI BLAST URL API，等待搜索完成后保存表格命中。

它完成一个明确、可复现的分析步骤。运行记录会保存规范化参数、输入文件哈希和日志；正式发表前仍应检查原始数据、参数和生物学解释。

## 什么时候使用

- 本机没有本地 BLAST 数据库，需要检索 NCBI 公共库时。
- 需要对单一步骤保留参数、日志和输出，便于重复运行或排查问题时。
- 查询序列不能离开本机时，应改用本地 BLAST。

## 输入准备

- 声明格式：`FASTA`
- 必须提供：`query`、`program`
- 确认分子类型与程序、数据库匹配：`blastn` 使用 `nt` 等核酸库，`blastp` 使用 `nr` 等蛋白库。
- 只有 `allowUpload=true` 时，查询序列才会发送到 NCBI。

### 输入示意

```text
>seq1
ATGCGTACGTACGTACGT
>seq2
ATGCGTTCGTACGTACGT
```

## 推荐运行步骤

1. 先确认查询序列可以发送到 NCBI。
2. 选择程序与匹配的数据库，并设置 `allowUpload=true`。
3. 提交后查看运行日志；工具会用返回的 RID 轮询状态。
4. 查看命中数、RID 和输出表格，再解释结果。

## 参数怎么选

| 参数 | 必填 | 默认值 | 选择建议 |
| --- | --- | --- | --- |
| `query` 查询 FASTA | 是 | 空 | 选择真实且完整的输入文件；只有 `allowUpload` 为 true 时才发送到 NCBI。 |
| `program` BLAST 程序 | 是 | blastn | 核酸用 blastn，蛋白用 blastp，翻译检索用 blastx/tblastn/tblastx。 |
| `database` 数据库 | 否 | nt | 选择与程序匹配的库，例如 nt、nr、refseq_protein 或 swissprot。 |
| `maxHits` 最大命中数 | 否 | 100 | 对应 HITLIST_SIZE；调小可加快响应并减小结果。 |
| `allowUpload` 允许上传 | 否 | false | 必须显式设为 true 才提交，代表同意序列离开本机。 |
| `timeoutSeconds` 超时秒数 | 否 | 600 | 等待远程搜索的总时间；大库或高峰期可适当增加。 |
| `pollSeconds` 轮询间隔 | 否 | 5 | 两次状态检查之间的秒数；不要低于 NCBI 的服务建议。 |
| `baseUrl` 服务地址 | 否 | https://blast.ncbi.nlm.nih.gov/Blast.cgi | 保持默认；只有可信 HTTPS 镜像才需要修改。HTTP 仅允许回环测试。 |
| `output` 输出表格 | 否 | 空 | 留空时写入运行目录；自定义路径应位于工作区。 |
| `overwrite` 覆盖输出 | 否 | false | 默认 false；只有确认目标文件可替换时才设为 true。 |

## 结果怎么读

- 输出格式：`TSV`
- 表格为 BLAST outfmt 6 列；结果摘要记录 RID、程序、数据库、HSP 数和耗时。
- RID 是 NCBI 对本次搜索的句柄；需要审计时保留在运行记录中。
- 无命中是成功的空结果，不是失败。

## 最小运行示例

```powershell
opentools run alignment.blast.remote --param query=C:\data\query.fasta --param program=blastn --param database=nt --param allowUpload=true
```

把占位路径替换为工作区中的真实文件。参数名必须保持原样，布尔值使用 `true` 或 `false`。

## 检查清单与失败边界

- 未设置 `allowUpload=true` 时工具会拒绝运行，避免自动化误上传。
- 服务地址必须使用 HTTPS；仅回环测试端点允许 HTTP。
- NCBI 可能排队、限流或拒绝请求；运行日志和错误信息会保留 RID 与 HTTP 状态。
- 远程结果依赖运行时的数据库版本；发表时记录日期和数据库名。
- 已有输出默认不会被覆盖；只有明确设置输出路径并接受覆盖时，才检查 `overwrite`。

## 相关下一步

- [本地 BLAST](tool:alignment.blast.local)
- [点图](tool:plot.dotplot)
- [BLAST XML 转表格](tool:alignment.blast.xml-to-table)

[返回文档首页](opentools-doc:README.md)
