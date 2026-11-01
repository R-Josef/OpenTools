OpenTools plugin compile SDK
============================

This directory contains the compile-time contract for OpenTools plugins:

  opentools-plugin-api-<version>.jar
  opentools-plugin-api-<version>-javadoc.jar
  javadoc/

Compile plugin Java sources with Java 21 and this JAR on the compiler class
path. Do not bundle the API JAR into the plugin JAR. OpenTools supplies the same
packages at runtime and loads them from the host class loader.

Plugin interfaces use io.opentools.plugin. Public task contracts use
io.opentools.api.tool.

Open javadoc/index.html for offline API documentation. The javadoc JAR contains
the same HTML for IDEs and Maven-compatible artifact repositories.

Maven plugins should use the opentools-plugin-api dependency with provided
scope. See the reference plugin in examples/plugins/.
