package io.opentools.examples.plugin;

import io.opentools.api.tool.OpenTool;
import io.opentools.plugin.PageContribution;
import io.opentools.plugin.PluginContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JPanel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ExamplePluginTest {
    @TempDir
    Path tempDir;

    @Test
    void registersReferenceTaskAndPage() {
        TestContext context = new TestContext(tempDir);

        new ExamplePlugin().onEnable(context);

        assertEquals(List.of("plugin.example.hello"),
                context.tools.stream().map(tool -> tool.descriptor().id()).toList());
        assertEquals(List.of("main"), context.pages);
        assertEquals(1, context.logMessages.size());
    }

    private static final class TestContext implements PluginContext {
        private final Path dataDirectory;
        private final List<OpenTool> tools = new ArrayList<>();
        private final List<String> pages = new ArrayList<>();
        private final List<String> logMessages = new ArrayList<>();

        private TestContext(Path dataDirectory) {
            this.dataDirectory = dataDirectory;
        }

        @Override
        public String pluginId() {
            return "example.plugin";
        }

        @Override
        public Path dataDirectory() {
            return dataDirectory;
        }

        @Override
        public void log(String message) {
            logMessages.add(message);
        }

        @Override
        public void registerTool(OpenTool tool) {
            tools.add(tool);
        }

        @Override
        public void registerPage(String id, String title, Supplier<JPanel> panelFactory) {
            pages.add(id);
        }
    }
}
