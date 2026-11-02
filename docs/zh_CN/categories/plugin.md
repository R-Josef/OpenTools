# 插件工具

随发布包提供的默认插件使用与内置工具相同的校验、作业、结果、CLI 和 MCP
执行契约。

## 金币跑酷

金币跑酷是一个 `InteractiveOpenTool`。输入玩家名称后点击游戏画面开始，按空格
跳过障碍物；再次点击正在运行的画面会立即结束本局。

结束后会产生：

- 玩家、距离、分数、金币、时长和结束方式表格；
- 包含结构化运行数据的 `coin-runner-result.json`；
- 包含角色和分数画面的 `coin-runner-result.png`。

自定义 Swing 页面不会绕过正式执行链路。页面通过 `ToolUiContext` 提交标准
`ToolRequest`，因此作业会像其他工具一样出现在 Jobs 和 Results 中。
