# 外部工具

OpenTools 的 Java 任务不需要外部程序。以下任务调用独立第三方工具：

| 工具 | 用途 | Windows 包目录 |
| --- | --- | --- |
| NCBI BLAST+ | 本地核酸/蛋白比对、建库 | `tools/blast/bin` |
| DIAMOND | 快速蛋白比对 | `tools/diamond` |
| HMMER | hmmscan、hmmsearch、HMM 数据库准备 | `tools/hmmer/bin` |
| MUSCLE | 多序列比对 | `tools/muscle` |
| trimAl | 比对修剪 | `tools/trimal` |
| IQ-TREE | 最大似然系统发育 | `tools/iqtree/bin` |
| MCScanX | 共线性与重复基因分类 | `tools/mcscanx` |

## 发现顺序

1. 便携包内固定 `tools/`；
2. 任务页显式目录或可执行文件；
3. `OPENTOOLS_*` 环境变量；
4. 当前进程继承的系统 `PATH`；
5. Windows 专用后备目录 `C:\Program Files\OpenToolsDeps\bin`。

第 5 项是开发环境兼容后备，只在前面各项均未命中时检查这个固定目录，不会扫描
其他 Program Files 子目录，也不会覆盖有效的显式路径、`OPENTOOLS_*` 或 PATH
结果。命中后备目录时，Settings 会显示警告，提示当前进程的 PATH 未解析到该工具。

应用会执行受限版本检查，并把实际程序路径、版本和参数写入运行清单。外部命令以
结构化参数启动，不通过 shell 拼接。

## 配置

环境变量：

```text
OPENTOOLS_MAKEBLASTDB
OPENTOOLS_BLASTN
OPENTOOLS_BLASTP
OPENTOOLS_BLASTX
OPENTOOLS_TBLASTN
OPENTOOLS_TBLASTX
OPENTOOLS_DIAMOND
OPENTOOLS_HMMSCAN
OPENTOOLS_HMMSEARCH
OPENTOOLS_MUSCLE
OPENTOOLS_TRIMAL
OPENTOOLS_IQTREE
OPENTOOLS_MCSCANX
```

修改机器级 `PATH` 后，已运行的 Explorer、终端和 OpenTools 不会自动获得新值，
因为它们继承的是启动时的环境。双击 JAR 时请关闭旧 OpenTools，并重启 Explorer
或注销/重新登录后再启动。Settings 的检查读取的是当前 OpenTools 进程实际继承的
PATH，并显示实际可执行文件和后备发现警告。

## 失败边界

- 错误架构、缺少 DLL、权限不足或杀毒隔离会导致启动失败。
- 超时会终止子进程树；极短超时不适合大型数据库或系统发育运行。
- HMMER 数据库使用 hmmscan 前必须已 press，所需 sidecar 缺失会明确失败。
- 外部工具的版本和计算语义由其上游决定。升级后应重跑对应 E2E。
- OpenTools 不保证其他平台二进制在 Windows 中可用，也不读取 conda 环境作为便携包依赖。

## 许可证

第三方程序不以 OpenTools 的 GPL 重新授权。便携包为每项工具保留完整许可证、版权、
官方来源、固定 SHA-256 和源码 URL；GPL 工具同时提供对应源码获取方式。升级工具
版本后必须重新检查许可证与分发义务。
