# Plugin tools

The bundled default plugin contributes tools that use the same validation, job,
result, CLI, and MCP contracts as built-in tools.

## Coin Runner

Coin Runner is an `InteractiveOpenTool`. Enter a player name, click the game
scene to start, and press Space to jump over obstacles. Clicking the running
scene again finishes the run immediately.

The finished run creates:

- a table with player, distance, score, coins, duration, and end reason;
- `coin-runner-result.json` with the structured run data;
- `coin-runner-result.png` with the final player and score scene.

The custom Swing panel does not bypass the normal pipeline. It submits a
standard `ToolRequest` through `ToolUiContext`, so the run appears in Jobs and
Results like any other tool execution.
