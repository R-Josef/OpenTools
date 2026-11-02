package io.opentools.plugin.host.fixture;

import io.opentools.api.tool.OutputArtifact;
import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolResult;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolRequest;
import io.opentools.plugin.OpenToolsPlugin;
import io.opentools.plugin.PluginContext;

import javax.swing.JPanel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class TestPlugin implements OpenToolsPlugin {
    @Override
    public void onEnable(PluginContext context) {
        context.registerPage("main", "page.main.title", "Test Page", JPanel::new);
        context.registerTool(new PluginEchoTool());
    }

    private static final class PluginEchoTool extends io.opentools.api.tool.AbstractOpenTool {
        private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
                "fixture.plugin.echo",
                "Plugin Echo",
                "Test plugin tool.",
                "Plugin",
                List.of(),
                List.of("Text"),
                List.of("Text"),
                "1.0.0",
                List.of()
        );

        @Override
        public ToolDescriptor descriptor() {
            return DESCRIPTOR;
        }

        @Override
        public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
            Path output = context.defaultOutputPath("plugin-echo.txt");
            Files.writeString(output, "plugin");
            return ToolResult.success(
                    "Plugin tool",
                    "",
                    null,
                    List.of(new OutputArtifact(output, "Plugin output", "text/plain"))
            );
        }
    }
}
