# MCP 接入

OpenTools 的 MCP 服务复用与 GUI、CLI 相同的工具注册、校验、作业存储和执行层。
支持 stdio 和只绑定 loopback 的认证 HTTP。

## stdio

```json
{
  "mcpServers": {
    "opentools": {
      "command": "java",
      "args": [
        "-jar",
        "C:\\work\\opentools.jar",
        "--project",
        "C:\\work\\opentools-project",
        "--app-home",
        "C:\\work\\opentools-app",
        "mcp",
        "serve",
        "--transport",
        "stdio"
      ]
    }
  }
}
```

stdio 的 stdout 只承载 JSON-RPC；启动和诊断信息写入 stderr。

## Loopback HTTP

```powershell
opentools --project C:\work\project mcp serve `
  --transport http --host 127.0.0.1 --port 0 --json
```

启动 envelope 的 `data.endpoint` 和 `data.token` 用于连接。请求需要：

```text
Authorization: Bearer <token>
Content-Type: application/json
```

HTTP 只允许本机循环地址、受控 Origin、请求大小上限和固定工作池。token 仅对当前
进程有效。不要写入项目文件、共享日志或聊天记录。

## 推荐调用顺序

1. `opentools_search_tools`
2. `opentools_describe_tool`
3. `opentools_validate_inputs`
4. `opentools_submit_job`
5. `opentools_job_status`
6. `opentools_read_result`

成功提交只表示作业已排队。必须等到 `SUCCEEDED` 后再读取结果。

多步骤 DAG 使用 `opentools_run_workflow`，参数为工作区内的 workflow JSON 路径。
它返回逐步骤状态、运行 ID、产物和指标；格式见[工作流](workflow.md)。

## 安全边界

- 输入、输出和目录默认限制在工作区。
- 自动化拒绝 `overwrite=true`，应使用新的输出路径。
- HTTP 没有 TLS，只适合同机客户端。
- stdio 的访问控制由父进程负责。
- MCP 不解锁隐藏工具，也不绕过对应 CLI/GUI 校验。
