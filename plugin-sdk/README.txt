OpenTools plugin compile SDK
============================

This directory contains the compile-time contract for OpenTools plugins:

  opentools-plugin-api-<version>.jar
  opentools-plugin-api-<version>-javadoc.jar
  javadoc/

Compile plugin Java sources with Java 21 and this JAR on the compiler class
path. Do not bundle the API JAR into the plugin JAR. OpenTools supplies the same
packages at runtime and loads them from the host class loader.

Plugin interfaces use io.opentools.plugin. Public tool contracts use
io.opentools.api.tool. Interactive tools implement InteractiveOpenTool and
return a ToolPanel that submits completed data through ToolUiContext.

Plugin API 2.1 adds plugin-scoped localization. Put UTF-8 properties in the
plugin JAR under:

  META-INF/opentools/i18n/en_US.properties
  META-INF/opentools/i18n/zh_CN.properties

Use String PluginContext.text(String key, String fallback, Object... args)
and ToolUiContext.text(...). The host maps each registered Tool ID to the
plugin that owns it. Language lookup uses the full active code, the language
code, en_US, then the fallback. Values support java.text.MessageFormat.
ToolDescriptor and ToolParameter fallback text remains the final fallback.

Keys passed to context.text(...) are exact and are not automatically prefixed
with a plugin ID. Standard tool UI constructs these full keys:

  tool.<Tool ID>.name
  tool.<Tool ID>.description
  tool.<Tool ID>.parameter.<Parameter ID>.title
  tool.<Tool ID>.parameter.<Parameter ID>.description

The three-argument registerPage(id, fallbackTitle, panelFactory) overload
derives page.<Page ID>.title. Plugin metadata localization always uses
plugin.name and plugin.description; the manifest name and description are
fallbacks. See docs/en_US/plugin-development.md or
docs/zh_CN/plugin-development.md for the complete naming and packaging guide.

Optional tool Markdown is discovered without registration:

  META-INF/opentools/docs/<language>/tool/<Tool ID>.md

When a guide is found, OpenTools shows the custom Markdown followed by a
separator and the generated descriptor reference. Missing guides are allowed.

Do not put plugin-private tool, result, or page keys in the host lang/
directory. Host category keys such as category.plugin remain application-owned.

Open javadoc/index.html for offline API documentation. The javadoc JAR contains
the same HTML for IDEs and Maven-compatible artifact repositories.

Maven plugins should use the opentools-plugin-api dependency with provided
scope. Third-party runtime libraries must be unpacked into the plugin JAR by
shading or provided by another declared OpenTools plugin JAR; nested JAR files
are not loaded automatically. See the bundled reference plugin in plugins/.
