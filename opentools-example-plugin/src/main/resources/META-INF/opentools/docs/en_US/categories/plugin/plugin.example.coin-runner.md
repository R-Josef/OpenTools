# Coin Runner: a reproducible demonstration

CUSTOM-DOC: plugin.example.coin-runner

Coin Runner is a deterministic demonstration of an `InteractiveOpenTool`. It
exercises the normal request, validation, job, result, and artifact pipeline
without replacing the standard execution service.

## Suggested test run

1. Start the scene.
2. Press Space to jump at least once.
3. Click the running scene to finish.
4. Open the result in the Results view.

## What to check

- The result table contains player, distance, score, coins, duration, and end reason.
- The JSON artifact records the same values used by the table.
- The PNG artifact keeps the `1000:460` scene aspect ratio.
- Changing the interface language changes the plugin labels without changing
  request parameter IDs or stored JSON values.
