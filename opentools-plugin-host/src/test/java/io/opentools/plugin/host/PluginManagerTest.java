package io.opentools.plugin.host;

import io.opentools.core.tool.ToolRegistry;
import io.opentools.core.i18n.LanguageManager;
import io.opentools.api.tool.ToolDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PluginManagerTest {
    @TempDir
    Path tempDir;

    @Test
    void reportsMalformedNewPluginWithoutBreakingScan() throws Exception {
        Path plugins = Files.createDirectories(tempDir.resolve("plugins"));
        try (JarWriter jar = new JarWriter(plugins.resolve("broken.jar"))) {
            jar.write("META-INF/opentools-plugin.json", """
                    {
                      "id": "broken",
                      "name": "Broken Plugin",
                      "version": "2.0",
                      "apiVersion": "2.0",
                      "mainClass": "missing.Plugin",
                      "description": "",
                      "author": ""
                    }
                    """);
        }

        PluginManager manager = new PluginManager(tempDir, new ToolRegistry());
        var scanned = manager.scan();
        assertEquals(PluginState.DISABLED, scanned.getFirst().state());
        scanned = manager.enable("broken");

        assertEquals(1, scanned.size());
        assertEquals(PluginState.FAILED, scanned.getFirst().state());
        assertTrue(scanned.getFirst().diagnostic().contains("missing.Plugin"));
    }

    @Test
    void rejectsPluginJarWithTraversalEntry() throws Exception {
        Path plugins = Files.createDirectories(tempDir.resolve("plugins"));
        try (JarWriter jar = new JarWriter(plugins.resolve("unsafe.jar"))) {
            jar.write("META-INF/opentools-plugin.json", """
                    {
                      "id": "unsafe",
                      "name": "Unsafe Plugin",
                      "version": "2.0",
                      "apiVersion": "2.0",
                      "mainClass": "unsafe.Plugin",
                      "dependencies": [],
                      "requestedCapabilities": ["tool"]
                    }
                    """);
            jar.write("../escape.class", new byte[]{0});
        }

        assertThrows(
                java.io.IOException.class,
                () -> PluginManager.validatePluginJar(plugins.resolve("unsafe.jar"))
        );
    }

    @Test
    void enablesAndDisablesNewPluginContributions() throws Exception {
        Path plugins = Files.createDirectories(tempDir.resolve("plugins"));
        writePluginJar(
                plugins.resolve("working.jar"),
                "working",
                "Working Plugin",
                "2.0",
                "io.opentools.plugin.host.fixture.TestPlugin",
                "",
                List.of()
        );

        ToolRegistry registry = new ToolRegistry();
        LanguageManager language = testLanguage("zh_CN");
        PluginManager manager = new PluginManager(tempDir, registry, language::activeCode);
        PluginInfo initial = manager.scan().getFirst();

        assertEquals(PluginState.DISABLED, initial.state());
        PluginInfo info = manager.enable("working").getFirst();
        assertEquals(PluginState.ENABLED, info.state());
        assertEquals(1, manager.pages().size());
        assertTrue(registry.find("fixture.plugin.echo").isPresent());
        ToolDescriptor descriptor = registry.find("fixture.plugin.echo").orElseThrow().descriptor();
        assertEquals("Plugin Echo", new ToolTextResolver(manager, language).name(descriptor));
        manager.disable("working");
        assertTrue(manager.pages().isEmpty());
        assertTrue(registry.find("fixture.plugin.echo").isEmpty());
        assertEquals(PluginState.DISABLED, manager.list().getFirst().state());
    }

    @Test
    void packagedPluginStateCanEnableTrustedPluginByDefault() throws Exception {
        Path plugins = Files.createDirectories(tempDir.resolve("plugins"));
        writePluginJar(
                plugins.resolve("bundled.jar"),
                "bundled",
                "Bundled Plugin",
                "2.0",
                "io.opentools.plugin.host.fixture.TestPlugin",
                "2.0",
                List.of(),
                List.of("tool", "page")
        );
        Files.writeString(plugins.resolve("plugin-state.json"), """
                {
                  "schemaVersion": 1,
                  "enabled": {
                    "bundled": true
                  }
                }
                """);

        ToolRegistry registry = new ToolRegistry();
        PluginManager manager = new PluginManager(tempDir, registry);

        assertEquals(PluginState.ENABLED, manager.scan().getFirst().state());
        assertTrue(registry.find("fixture.plugin.echo").isPresent());

        manager.disable("bundled");
        assertEquals(PluginState.DISABLED, manager.scan().getFirst().state());
        assertTrue(registry.find("fixture.plugin.echo").isEmpty());
    }

    @Test
    void reportsMissingDependencyWhileLoadingIndependentPlugin() throws Exception {
        Path plugins = Files.createDirectories(tempDir.resolve("plugins"));
        writePluginJar(
                plugins.resolve("missing-dependency.jar"),
                "needs.missing",
                "Missing Dependency",
                "2.0",
                "example.Missing",
                "",
                List.of("not-installed")
        );
        writePluginJar(
                plugins.resolve("working.jar"),
                "working",
                "Working Plugin",
                "2.0",
                "io.opentools.plugin.host.fixture.TestPlugin",
                "",
                List.of()
        );

        PluginManager manager = new PluginManager(tempDir, new ToolRegistry());
        List<PluginInfo> infos = manager.scan();
        assertEquals(PluginState.DISABLED, info(infos, "working").state());
        manager.enable("working");
        infos = manager.enable("needs.missing");

        PluginInfo missing = info(infos, "needs.missing");
        assertEquals(PluginState.INCOMPATIBLE, missing.state());
        assertTrue(missing.diagnostic().contains("not-installed"));
        assertEquals(PluginState.ENABLED, info(infos, "working").state());
    }

    @Test
    void disabledStatePersistsAndControlsDependentPlugins() throws Exception {
        Path plugins = Files.createDirectories(tempDir.resolve("plugins"));
        writePluginJar(
                plugins.resolve("dependency.jar"),
                "dependency",
                "Dependency",
                "2.0",
                "io.opentools.plugin.host.fixture.TestPlugin",
                "",
                List.of()
        );
        writePluginJar(
                plugins.resolve("dependent.jar"),
                "dependent",
                "Dependent",
                "2.0",
                "io.opentools.plugin.host.fixture.DependentPlugin",
                "",
                List.of("dependency")
        );

        PluginManager manager = new PluginManager(tempDir, new ToolRegistry());
        List<PluginInfo> initial = manager.scan();
        assertEquals(PluginState.DISABLED, info(initial, "dependency").state());
        assertEquals(PluginState.DISABLED, info(initial, "dependent").state());
        manager.enable("dependent");
        initial = manager.list();
        assertEquals(PluginState.ENABLED, info(initial, "dependency").state());
        assertEquals(PluginState.ENABLED, info(initial, "dependent").state());

        manager.disable("dependency");
        PluginManager restarted = new PluginManager(tempDir, new ToolRegistry());
        List<PluginInfo> disabled = restarted.scan();
        assertEquals(PluginState.DISABLED, info(disabled, "dependency").state());
        assertEquals(PluginState.INCOMPATIBLE, info(disabled, "dependent").state());
        assertTrue(info(disabled, "dependent").diagnostic().contains("dependency"));
        assertTrue(Files.isRegularFile(plugins.resolve("plugin-state.json")));

        restarted.enable("dependency");
        assertEquals(PluginState.ENABLED, info(restarted.list(), "dependency").state());
        assertEquals(PluginState.ENABLED, info(restarted.list(), "dependent").state());
    }

    @Test
    void rejectsIncompatibleApiAndUnknownCapabilitiesWithoutBlockingOthers() throws Exception {
        Path plugins = Files.createDirectories(tempDir.resolve("plugins"));
        writePluginJar(
                plugins.resolve("future-api.jar"),
                "future-api",
                "Future API",
                "2.0",
                "io.opentools.plugin.host.fixture.TestPlugin",
                "3.0",
                List.of(),
                List.of("tool", "page")
        );
        writePluginJar(
                plugins.resolve("unknown-capability.jar"),
                "unknown-capability",
                "Unknown Capability",
                "2.0",
                "io.opentools.plugin.host.fixture.TestPlugin",
                "",
                List.of(),
                List.of("tool", "network-unrestricted")
        );
        writePluginJar(
                plugins.resolve("compatible-api.jar"),
                "compatible-api",
                "Compatible API",
                "2.0",
                "io.opentools.plugin.host.fixture.TestPlugin",
                "2.0",
                List.of(),
                List.of("tool", "page")
        );

        PluginManager manager = new PluginManager(tempDir, new ToolRegistry());
        List<PluginInfo> infos = manager.scan();

        assertEquals(PluginState.INCOMPATIBLE, info(infos, "future-api").state());
        assertEquals(PluginState.INCOMPATIBLE, info(infos, "unknown-capability").state());
        infos = manager.enable("compatible-api");
        assertEquals(PluginState.ENABLED, info(infos, "compatible-api").state());
    }

    @Test
    void dependencyLoadsBeforeDependentPlugin() throws Exception {
        Path plugins = Files.createDirectories(tempDir.resolve("plugins"));
        writePluginJar(
                plugins.resolve("dependency.jar"),
                "dependency",
                "Dependency",
                "2.0",
                "io.opentools.plugin.host.fixture.TestPlugin",
                "",
                List.of()
        );
        writePluginJar(
                plugins.resolve("dependent.jar"),
                "dependent",
                "Dependent",
                "2.0",
                "io.opentools.plugin.host.fixture.DependentPlugin",
                "",
                List.of("dependency")
        );

        PluginManager manager = new PluginManager(tempDir, new ToolRegistry());
        List<PluginInfo> infos = manager.scan();
        infos = manager.enable("dependent");

        assertEquals(List.of("dependency", "dependent"),
                infos.stream().map(PluginInfo::id).toList());
        assertEquals(2, manager.pages().size());
        assertEquals(List.of("dependency"), info(infos, "dependent").dependencies());
    }

    @Test
    void isolatesSamePluginClassAcrossDifferentJars() throws Exception {
        Path plugins = Files.createDirectories(tempDir.resolve("plugins"));
        writeCompiledPluginJar(
                plugins.resolve("version-a.jar"),
                "version.a",
                "Version A",
                "Version A Page"
        );
        writeCompiledPluginJar(
                plugins.resolve("version-b.jar"),
                "version.b",
                "Version B",
                "Version B Page"
        );

        PluginManager manager = new PluginManager(tempDir, new ToolRegistry());
        try {
            manager.scan();
            manager.enable("version.a");
            List<PluginInfo> infos = manager.enable("version.b");

            assertEquals(PluginState.ENABLED, info(infos, "version.a").state());
            assertEquals(PluginState.ENABLED, info(infos, "version.b").state());
            assertEquals(
                    List.of("Version A Page", "Version B Page"),
                    manager.pages().stream().map(io.opentools.plugin.PageContribution::title).sorted().toList()
            );
        } finally {
            manager.close();
        }
    }

    @Test
    void rejectsContributionWithoutRequestedCapability() throws Exception {
        Path plugins = Files.createDirectories(tempDir.resolve("plugins"));
        writePluginJar(
                plugins.resolve("no-page-capability.jar"),
                "no-page-capability",
                "No Page Capability",
                "2.0",
                "io.opentools.plugin.host.fixture.TestPlugin",
                "",
                List.of(),
                List.of("tool")
        );

        PluginManager manager = new PluginManager(tempDir, new ToolRegistry());
        assertEquals(PluginState.DISABLED, manager.scan().getFirst().state());
        PluginInfo info = manager.enable("no-page-capability").getFirst();

        assertEquals(PluginState.FAILED, info.state());
        assertTrue(info.diagnostic().contains("capability: page"));
    }

    @Test
    void reportsDependencyCycleWithoutLoadingEitherPlugin() throws Exception {
        Path plugins = Files.createDirectories(tempDir.resolve("plugins"));
        writePluginJar(
                plugins.resolve("cycle-a.jar"),
                "cycle.a",
                "Cycle A",
                "2.0",
                "io.opentools.plugin.host.fixture.TestPlugin",
                "",
                List.of("cycle.b")
        );
        writePluginJar(
                plugins.resolve("cycle-b.jar"),
                "cycle.b",
                "Cycle B",
                "2.0",
                "io.opentools.plugin.host.fixture.TestPlugin",
                "",
                List.of("cycle.a")
        );

        PluginManager manager = new PluginManager(tempDir, new ToolRegistry());
        List<PluginInfo> infos = manager.scan();

        assertEquals(PluginState.DISABLED, info(infos, "cycle.a").state());
        assertEquals(PluginState.DISABLED, info(infos, "cycle.b").state());
        List<PluginInfo> afterEnable = manager.enable("cycle.a");
        assertEquals(PluginState.INCOMPATIBLE, info(afterEnable, "cycle.a").state());
        assertTrue(info(afterEnable, "cycle.a").diagnostic().contains("cycle"));
    }

    @Test
    void bindsLocalizationToExactToolAndFollowsLanguageChanges() throws Exception {
        Path plugins = Files.createDirectories(tempDir.resolve("plugins"));
        writeLocalizedPluginJar(plugins.resolve("localized.jar"), Map.of(
                "en_US", """
                        plugin.name=Localized Plugin
                        plugin.description=English description
                        page.main.title=Localized Page
                        tool.fixture.plugin.echo.name=Plugin Echo
                        tool.fixture.plugin.echo.description=English tool description
                        formatted=Read {0} rows
                        """,
                "zh_CN", """
                        plugin.name=\u672c\u5730\u5316\u63d2\u4ef6
                        plugin.description=\u4e2d\u6587\u63cf\u8ff0
                        page.main.title=\u672c\u5730\u5316\u9875\u9762
                        tool.fixture.plugin.echo.name=\u63d2\u4ef6\u56de\u58f0
                        tool.fixture.plugin.echo.description=\u4e2d\u6587\u5de5\u5177\u63cf\u8ff0
                        formatted=\u5df2\u8bfb\u53d6 {0} \u884c
                        """
        ));
        LanguageManager language = testLanguage("zh_CN");
        ToolRegistry registry = new ToolRegistry();
        PluginManager manager = new PluginManager(tempDir, registry, language::activeCode);
        manager.scan();

        PluginInfo chinese = manager.enable("working").getFirst();
        ToolDescriptor descriptor = registry.find("fixture.plugin.echo")
                .orElseThrow()
                .descriptor();
        ToolTextResolver resolver = new ToolTextResolver(manager, language);

        assertEquals(PluginState.ENABLED, chinese.state());
        assertEquals("\u672c\u5730\u5316\u63d2\u4ef6", chinese.name());
        assertEquals("\u4e2d\u6587\u63cf\u8ff0", manager.displayDescription(chinese));
        assertEquals("\u672c\u5730\u5316\u9875\u9762", manager.pages().getFirst().title());
        assertEquals("\u63d2\u4ef6\u56de\u58f0", resolver.name(descriptor));
        assertEquals("\u5df2\u8bfb\u53d6 3 \u884c",
                resolver.text("fixture.plugin.echo", "formatted", "fallback", 3));
        assertTrue(manager.localizerForPlugin("working") != null);
        assertTrue(manager.localizerForTool("fixture.plugin.echo") != null);

        language.setLanguage("en_US");
        assertEquals("Localized Plugin", manager.list().getFirst().name());
        assertEquals("Localized Page", manager.pages().getFirst().title());
        assertEquals("Plugin Echo", resolver.name(descriptor));

        manager.disable("working");
        assertTrue(manager.localizerForPlugin("working") == null);
        assertTrue(manager.localizerForTool("fixture.plugin.echo") == null);
        assertEquals("Plugin Echo", resolver.name(descriptor));
    }

    @Test
    void reportsDuplicatePluginLanguageKeysAndFallsBackToEnglish() throws Exception {
        Path plugins = Files.createDirectories(tempDir.resolve("plugins"));
        writeLocalizedPluginJar(plugins.resolve("diagnostic.jar"), Map.of(
                "en_US", "tool.fixture.plugin.echo.name=English fallback\n",
                "zh_CN", "duplicate=one\nduplicate=two\n"
        ));
        LanguageManager language = testLanguage("zh_CN");
        ToolRegistry registry = new ToolRegistry();
        PluginManager manager = new PluginManager(tempDir, registry, language::activeCode);
        manager.scan();
        PluginInfo info = manager.enable("working").getFirst();

        assertEquals(PluginState.ENABLED, info.state());
        assertEquals("I18N_DUPLICATE_KEY", manager.diagnostics("working").getFirst().code());
        assertTrue(info.diagnostic().contains("I18N_DUPLICATE_KEY"));
        ToolDescriptor descriptor = registry.find("fixture.plugin.echo").orElseThrow().descriptor();
        assertEquals("English fallback", new ToolTextResolver(manager, language).name(descriptor));
    }

    @Test
    void discoversPluginDocumentationByCategoryAndToolId() throws Exception {
        Path plugins = Files.createDirectories(tempDir.resolve("plugins"));
        writeLocalizedPluginJar(
                plugins.resolve("documented.jar"),
                Map.of("en_US", "tool.fixture.plugin.echo.name=Plugin Echo\n"),
                Map.of(
                        "en_US/tool/fixture.plugin.echo.md",
                        "# English guide\n\nEnglish-only documentation.",
                        "zh_CN/tool/fixture.plugin.echo.md",
                        "# 中文指南\n\n中文文档。"
                )
        );
        LanguageManager language = testLanguage("zh_CN");
        ToolRegistry registry = new ToolRegistry();
        PluginManager manager = new PluginManager(tempDir, registry, language::activeCode);
        manager.scan();
        manager.enable("working");
        ToolDescriptor descriptor = registry.find("fixture.plugin.echo").orElseThrow().descriptor();

        assertTrue(manager.toolDocumentation(descriptor).orElseThrow().contains("中文指南"));
        language.setLanguage("en_US");
        assertTrue(manager.toolDocumentation(descriptor).orElseThrow().contains("English guide"));

        manager.disable("working");
        assertTrue(manager.toolDocumentation(descriptor).isEmpty());
    }

    private void writePluginJar(
            Path path,
            String id,
            String name,
            String version,
            String mainClass,
            String apiVersion,
            List<String> dependencies
    ) throws Exception {
        writePluginJar(path, id, name, version, mainClass, apiVersion, dependencies, List.of("tool", "page"));
    }

    private void writeLocalizedPluginJar(Path path, Map<String, String> resources) throws Exception {
        writeLocalizedPluginJar(path, resources, Map.of());
    }

    private void writeLocalizedPluginJar(
            Path path,
            Map<String, String> resources,
            Map<String, String> documentation
    ) throws Exception {
        String fixture = "io/opentools/plugin/host/fixture/TestPlugin.class";
        byte[] classBytes;
        try (var input = getClass().getClassLoader().getResourceAsStream(fixture)) {
            classBytes = input == null ? new byte[0] : input.readAllBytes();
        }
        String json = """
                {
                  "id": "working",
                  "name": "Working Plugin",
                  "version": "2.0",
                  "apiVersion": "2.1",
                  "mainClass": "io.opentools.plugin.host.fixture.TestPlugin",
                  "description": "Fallback description",
                  "author": "",
                  "dependencies": [],
                  "requestedCapabilities": ["tool", "page"]
                }
                """;
        try (JarWriter jar = new JarWriter(path)) {
            jar.write("META-INF/opentools-plugin.json", json);
            jar.write(fixture, classBytes);
            for (Map.Entry<String, String> resource : resources.entrySet()) {
                jar.write(
                        "META-INF/opentools/i18n/" + resource.getKey() + ".properties",
                        resource.getValue()
                );
            }
            for (Map.Entry<String, String> document : documentation.entrySet()) {
                jar.write(
                        "META-INF/opentools/docs/" + document.getKey(),
                        document.getValue()
                );
            }
        }
    }

    private LanguageManager testLanguage(String activeCode) throws Exception {
        Path languages = Files.createDirectories(tempDir.resolve("lang"));
        Files.writeString(languages.resolve("en_US.properties"), "language.name=English\n");
        Files.writeString(languages.resolve("zh_CN.properties"), "language.name=Chinese\n");
        return new LanguageManager(tempDir, tempDir.resolve("user"), activeCode);
    }

    private void writePluginJar(
            Path path,
            String id,
            String name,
            String version,
            String mainClass,
            String apiVersion,
            List<String> dependencies,
            List<String> capabilities
    ) throws Exception {
        String fixture = mainClass.replace('.', '/') + ".class";
        byte[] classBytes;
        try (var input = getClass().getClassLoader().getResourceAsStream(fixture)) {
            classBytes = input == null ? new byte[0] : input.readAllBytes();
        }
        if (classBytes.length == 0 && mainClass.startsWith("io.opentools.")) {
            throw new IllegalStateException("Fixture class missing: " + fixture);
        }
        String actualApi = apiVersion == null || apiVersion.isBlank() ? "2.0" : apiVersion;
        String json = """
                {
                  "id": "%s",
                  "name": "%s",
                  "version": "%s",
                  "apiVersion": "%s",
                  "mainClass": "%s",
                  "description": "",
                  "author": "",
                  "dependencies": %s,
                  "requestedCapabilities": %s
                }
                """.formatted(
                id,
                name,
                version,
                actualApi,
                mainClass,
                jsonArray(dependencies),
                jsonArray(capabilities)
        );
        try (JarWriter jar = new JarWriter(path)) {
            jar.write("META-INF/opentools-plugin.json", json);
            if (classBytes.length > 0) {
                jar.write(fixture, classBytes);
            }
        }
    }

    private static String jsonArray(List<String> values) {
        return values.stream()
                .map(value -> "\"" + value + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private void writeCompiledPluginJar(
            Path jarPath,
            String id,
            String name,
            String pageTitle
    ) throws Exception {
        Path sourceRoot = tempDir.resolve("source-" + id);
        Path source = sourceRoot.resolve("isolate/Plugin.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package isolate;
                import io.opentools.plugin.OpenToolsPlugin;
                import io.opentools.plugin.PluginContext;
                import javax.swing.JPanel;
                public final class Plugin implements OpenToolsPlugin {
                    public void onEnable(PluginContext context) {
                        context.registerPage("main", "page.main.title", "%s", JPanel::new);
                    }
                }
                """.formatted(pageTitle));
        Path classes = tempDir.resolve("classes-" + id);
        Files.createDirectories(classes);
        Path apiLocation = Path.of(
                io.opentools.plugin.OpenToolsPlugin.class.getProtectionDomain()
                        .getCodeSource().getLocation().toURI()
        );
        String classpath = System.getProperty("java.class.path")
                + java.io.File.pathSeparator
                + apiLocation;
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("JDK compiler is unavailable");
        }
        int exit = compiler.run(
                null,
                null,
                null,
                "-classpath",
                classpath,
                "-d",
                classes.toString(),
                source.toString()
        );
        assertEquals(0, exit);
        List<String> classNames = new ArrayList<>();
        try (var files = Files.walk(classes)) {
            files.filter(path -> path.toString().endsWith(".class"))
                    .forEach(path -> classNames.add(classes.relativize(path).toString().replace('\\', '/')));
        }
        String json = """
                {
                  "id": "%s",
                  "name": "%s",
                  "version": "2.0",
                  "apiVersion": "2.0",
                  "mainClass": "isolate.Plugin",
                  "dependencies": [],
                  "requestedCapabilities": ["page"]
                }
                """.formatted(id, name);
        try (JarWriter jar = new JarWriter(jarPath)) {
            jar.write("META-INF/opentools-plugin.json", json);
            for (String className : classNames) {
                jar.write(className, Files.readAllBytes(classes.resolve(className)));
            }
        }
    }

    private static PluginInfo info(List<PluginInfo> infos, String id) {
        return infos.stream()
                .filter(info -> info.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static final class JarWriter implements AutoCloseable {
        private final ZipOutputStream output;

        private JarWriter(Path path) throws Exception {
            output = new ZipOutputStream(Files.newOutputStream(path));
        }

        private void write(String name, String text) throws Exception {
            write(name, text.getBytes(StandardCharsets.UTF_8));
        }

        private void write(String name, byte[] bytes) throws Exception {
            output.putNextEntry(new ZipEntry(name));
            output.write(bytes);
            output.closeEntry();
        }

        @Override
        public void close() throws Exception {
            output.close();
        }
    }
}
