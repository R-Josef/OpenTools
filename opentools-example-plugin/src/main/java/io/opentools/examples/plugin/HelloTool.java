package io.opentools.examples.plugin;

import io.opentools.api.tool.AbstractOpenTool;
import io.opentools.api.tool.OutputArtifact;
import io.opentools.api.tool.ResultTable;
import io.opentools.api.tool.TaskContext;
import io.opentools.api.tool.TaskResult;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolParameter;
import io.opentools.api.tool.ToolRequest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class HelloTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "plugin.example.hello",
            "Plugin Hello",
            "Write a user-provided message from the example plugin.",
            "Plugin",
            List.of("plugin example", "插件示例"),
            List.of("Text"),
            List.of("Text"),
            "1.0.0",
            List.of(ToolParameter.string(
                    "message",
                    "Message",
                    "Message written to the output file.",
                    "Hello from an OpenTools plugin."
            ))
    );

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public TaskResult execute(ToolRequest request, TaskContext context) throws Exception {
        String message = request.string("message");
        Path output = context.defaultOutputPath("plugin-example-message.txt");
        Files.writeString(output, message + System.lineSeparator(), StandardCharsets.UTF_8);
        context.progress(1.0, "Plugin task completed");
        return new TaskResult(
                "Plugin task completed",
                "result.plugin.example.hello.summary",
                List.of(),
                message,
                null,
                List.of(),
                new ResultTable(List.of("Message"), List.of(List.of(message))),
                List.of(new OutputArtifact(output, "Plugin message", "text/plain")),
                Map.of("messageLength", message.length())
        );
    }
}
