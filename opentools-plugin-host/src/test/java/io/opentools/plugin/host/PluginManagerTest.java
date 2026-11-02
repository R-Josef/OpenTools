package io.opentools.plugin.host;

import io.opentools.core.tool.ToolRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
        PluginManager manager = new PluginManager(tempDir, registry);
        PluginInfo initial = manager.scan().getFirst();

        assertEquals(PluginState.DISABLED, initial.state());
        PluginInfo info = manager.enable("working").getFirst();
        assertEquals(PluginState.ENABLED, info.state());
        assertEquals(1, manager.pages().size());
        assertTrue(registry.find("fixture.plugin.echo").isPresent());
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
                        context.registerPage("main", "%s", JPanel::new);
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
