OpenTools Windows x64 portable package
======================================

1. Extract the complete ZIP to a normal user-writable folder.
2. Start OpenTools.exe. Do not copy the EXE by itself.
3. Open Docs from the left navigation for user and task documentation.

Docs follows the current interface language and reads from docs/en_US or docs/zh_CN.
Changing language in Settings rebuilds the interface immediately; restarting is not
required.

This package contains a private Java runtime and independent third-party Windows
programs under tools/. Java, conda, and MSYS2 are not required.

OpenTools is GPL-3.0-or-later. Third-party programs retain their own licenses.
See licenses/ and THIRD_PARTY_NOTICES.md. This portable ZIP is unsigned and has
no installer or automatic updater.

Command-line use:
  OpenToolsCLI.exe tools list --json
  OpenToolsCLI.exe doctor --json

Use OpenTools.exe for the GUI and OpenToolsCLI.exe for command-line and MCP use.
