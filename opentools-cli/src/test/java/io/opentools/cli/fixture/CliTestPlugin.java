package io.opentools.cli.fixture;

import io.opentools.api.tool.AbstractOpenTool;
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

public final class CliTestPlugin implements OpenToolsPlugin {
    @Override
    public void onEnable(PluginContext context) {
        context.registerPage("main", "CLI Test Page", JPanel::new);
        context.registerTool(new CliTestTool());
    }

    private static final class CliTestTool extends AbstractOpenTool {
        private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
                "plugin.cli.test",
                "CLI Plugin Test",
                "Test plugin task loaded by the CLI.",
                "Plugin",
                List.of(),
                List.of(),
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
            Path output = context.defaultOutputPath("cli-plugin.txt");
            Files.writeString(output, "cli-plugin");
            return ToolResult.success(
                    "CLI plugin task completed",
                    "",
                    null,
                    List.of(new OutputArtifact(output, "CLI plugin output", "text/plain"))
            );
        }
    }
}
