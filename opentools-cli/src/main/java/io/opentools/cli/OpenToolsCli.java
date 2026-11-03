package io.opentools.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.opentools.core.job.JobRecord;
import io.opentools.core.job.JobStatus;
import io.opentools.core.job.ToolValidationException;
import io.opentools.core.automation.ToolContracts;
import io.opentools.api.tool.OpenToolException;
import io.opentools.api.tool.ParameterType;
import io.opentools.api.tool.OutputArtifact;
import io.opentools.api.tool.ToolResult;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolParameter;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ValidationIssue;
import io.opentools.api.tool.ValidationResult;
import io.opentools.formats.FormatDetection;
import io.opentools.formats.FormatDetector;
import io.opentools.external.ExternalRuntime;
import io.opentools.external.ExternalTools;
import io.opentools.mcp.AutomationRuntime;
import io.opentools.mcp.McpHttpServer;
import io.opentools.mcp.McpStdioServer;
import io.opentools.mcp.McpToolServer;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(
        mixinStandardHelpOptions = true,
        name = "opentools",
        versionProvider = OpenToolsVersionProvider.class,
        description = "Local-first bioinformatics workbench",
        subcommands = {
                OpenToolsCli.ToolsCommand.class,
                OpenToolsCli.RunCommand.class,
                OpenToolsCli.JobsCommand.class,
                OpenToolsCli.PluginsCommand.class,
                OpenToolsCli.FormatCommand.class,
                OpenToolsCli.DoctorCommand.class,
                OpenToolsCli.ProjectCommand.class,
                OpenToolsCli.McpCommand.class,
                OpenToolsCli.BatchCommand.class
        }
)
public final class OpenToolsCli implements AutoCloseable {
    private static final ObjectMapper JSON = createObjectMapper();

    @Spec
    private CommandSpec spec;

    @Option(names = "--project", paramLabel = "PATH", description = "Project directory. Defaults to ~/.opentools/default-project.")
    private Path projectPath;

    @Option(names = "--app-home", paramLabel = "PATH", description = "Application directory containing plugins/.")
    private Path appHome;

    @Option(names = "--language", paramLabel = "CODE", description = "Interface language code, such as en_US or zh_CN.")
    private String languageCode;

    private CliRuntime runtime;

    /**
     * Executes the CLI with encoding-correct writers.
     *
     * <p>JSON automation is always UTF-8; human-readable output keeps the
     * platform console encoding so that Windows terminals render it.</p>
     *
     * @param args command-line arguments
     * @return process exit code
     */
    public static int execute(String[] args) {
        PrintWriter out;
        PrintWriter err;
        if (hasJsonOption(args)) {
            out = new PrintWriter(new java.io.OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
            err = new PrintWriter(new java.io.OutputStreamWriter(System.err, StandardCharsets.UTF_8), true);
        } else {
            out = new PrintWriter(System.out, true);
            err = new PrintWriter(System.err, true);
        }
        return execute(args, out, err);
    }

    public static int execute(String[] args, PrintWriter out, PrintWriter err) {
        OpenToolsCli root = new OpenToolsCli();
        CommandLine commandLine = new CommandLine(root);
        commandLine.setOut(out);
        commandLine.setErr(err);
        commandLine.setExecutionExceptionHandler((exception, command, parseResult) -> {
            int exitCode = ExitCodes.forException(exception);
            if (hasJsonOption(args) || parseResult.hasMatchedOption("--json")) {
                root.writeJsonFailure(JsonEnvelope.failure(exception));
            } else {
                Throwable current = exception;
                while (current.getCause() != null) {
                    current = current.getCause();
                }
                command.getErr().println(
                        "Error: " + (current.getMessage() == null
                                ? current.getClass().getSimpleName()
                                : current.getMessage())
                );
                if (current instanceof OpenToolException error
                        && error.suggestion() != null
                        && !error.suggestion().isBlank()) {
                    command.getErr().println("Suggestion: " + error.suggestion());
                }
            }
            return exitCode;
        });
        commandLine.setParameterExceptionHandler((exception, args2) -> {
            if (hasJsonOption(args2)) {
                root.writeJsonFailure(JsonEnvelope.failure(
                        "INVALID_ARGUMENTS",
                        exception.getMessage(),
                        "Run with --help to inspect available options.",
                        List.of()
                ));
            } else {
                commandLine.getErr().println(exception.getMessage());
                commandLine.usage(commandLine.getErr());
            }
            return ExitCodes.INPUT;
        });
        int exitCode = commandLine.execute(args);
        try {
            root.close();
        } catch (Exception ignored) {
            // Process teardown should not hide the command result.
        }
        return exitCode;
    }

    public static void main(String[] args) {
        System.exit(execute(args));
    }

    private static ObjectMapper createObjectMapper() {
        SimpleModule module = new SimpleModule();
        module.addSerializer(Path.class, ToStringSerializer.instance);
        ObjectMapper mapper = new ObjectMapper()
                .findAndRegisterModules()
                .registerModule(module)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.INDENT_OUTPUT);
        // Keep automation JSON valid even when a legacy Windows console decodes
        // the UTF-8 bytes with the active ANSI code page.
        mapper.getFactory().configure(com.fasterxml.jackson.core.JsonGenerator.Feature.ESCAPE_NON_ASCII, true);
        return mapper;
    }

    public synchronized CliRuntime runtime() throws Exception {
        if (runtime == null) {
            Path configuredAppHome = appHome == null
                    ? io.opentools.core.runtime.AppPaths.applicationHome()
                    : appHome.toAbsolutePath().normalize();
            runtime = CliRuntime.open(projectPath, configuredAppHome, languageCode);
        }
        return runtime;
    }

    public PrintWriter out() {
        return spec.commandLine().getOut();
    }

    public PrintWriter err() {
        return spec.commandLine().getErr();
    }

    private void writeJsonSuccess(Object data) {
        out().println(writeJson(JsonEnvelope.success(data)));
    }

    private void writeJsonFailure(JsonEnvelope failure) {
        out().println(writeJson(failure));
    }

    private String writeJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception ex) {
            return "{\"ok\":false,\"apiVersion\":\"2.0\",\"data\":null,"
                    + "\"error\":{\"code\":\"JSON_SERIALIZATION_FAILED\","
                    + "\"message\":\"Could not serialize command response.\","
                    + "\"suggestion\":\"Use a non-JSON output mode.\",\"issues\":[]}}";
        }
    }

    private static boolean hasJsonOption(String[] args) {
        for (String argument : args) {
            if ("--json".equals(argument)
                    || argument.startsWith("--json=")
                    || "--schema".equals(argument)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public synchronized void close() throws Exception {
        if (runtime != null) {
            runtime.close();
        }
    }

    @Command(
            mixinStandardHelpOptions = true,
            name = "tools",
            description = "Discover available tools",
            subcommands = {ToolsCommand.ListCommand.class, ToolsCommand.DescribeCommand.class}
    )
    static final class ToolsCommand implements Runnable {
        @ParentCommand
        private OpenToolsCli root;

        @Override
        public void run() {
            root.out().println("Use `tools list` or `tools describe <tool-id>`.");
        }

        @Command(mixinStandardHelpOptions = true, name = "list", description = "List registered tools")
        static final class ListCommand implements Callable<Integer> {
            @ParentCommand
            private ToolsCommand parent;

            @Option(names = "--json", description = "Emit JSON")
            private boolean json;

            @Override
            public Integer call() throws Exception {
                List<ToolDescriptor> tools = parent.root.runtime().registry().list();
                if (json) {
                    parent.root.writeJsonSuccess(tools);
                    return 0;
                }
                parent.root.out().printf("%-22s %-12s %s%n", "ID", "CATEGORY", "NAME");
                var toolText = parent.root.runtime().toolText();
                for (ToolDescriptor tool : tools) {
                    String category = toolText.category(tool.category());
                    String name = toolText.name(tool);
                    parent.root.out().printf("%-22s %-12s %s%n", tool.id(), category, name);
                }
                return 0;
            }
        }

        @Command(mixinStandardHelpOptions = true, name = "describe", description = "Describe one task and its parameters")
        static final class DescribeCommand implements Callable<Integer> {
            @ParentCommand
            private ToolsCommand parent;

            @Parameters(index = "0", paramLabel = "TOOL_ID")
            private String toolId;

            @Option(names = "--json", description = "Emit JSON")
            private boolean json;

            @Option(names = "--schema", description = "Emit the Draft 2020-12 parameter schema")
            private boolean schema;

            @Override
            public Integer call() throws Exception {
                ToolDescriptor tool = parent.root.runtime().registry().find(toolId)
                        .orElseThrow(() -> new OpenToolException("TOOL_NOT_FOUND", "Unknown tool: " + toolId, "Run `tools list`."))
                        .descriptor();
                if (schema) {
                    parent.root.writeJsonSuccess(ToolContracts.contract(tool));
                    return 0;
                }
                if (json) {
                    parent.root.writeJsonSuccess(tool);
                    return 0;
                }
                var toolText = parent.root.runtime().toolText();
                String name = toolText.name(tool);
                String description = toolText.description(tool);
                String category = toolText.category(tool.category());
                parent.root.out().println(name + " (" + tool.id() + ")");
                parent.root.out().println(description);
                parent.root.out().println("Category: " + category);
                parent.root.out().println("Version: " + tool.version());
                parent.root.out().println("Inputs: " + String.join(", ", tool.inputFormats()));
                parent.root.out().println("Outputs: " + String.join(", ", tool.outputFormats()));
                parent.root.out().println("Parameters:");
                for (ToolParameter parameter : tool.parameters()) {
                    String parameterTitle = toolText.parameterTitle(tool, parameter);
                    String parameterDescription = toolText.parameterDescription(tool, parameter);
                    parent.root.out().printf(
                            "  %-14s %-12s %s%s%n",
                            parameter.id(),
                            parameter.type(),
                            parameter.required() ? "required" : "optional",
                            parameterDescription.isBlank() ? "" : " - " + parameterTitle + ": " + parameterDescription
                    );
                }
                return 0;
            }
        }
    }

    @Command(mixinStandardHelpOptions = true, name = "run", description = "Run a task")
    static final class RunCommand implements Callable<Integer> {
        @ParentCommand
        private OpenToolsCli root;

        @Parameters(index = "0", paramLabel = "TOOL_ID")
        private String toolId;

        @Option(names = {"-p", "--param"}, paramLabel = "KEY=VALUE", description = "Tool parameter")
        private List<String> parameterValues = new ArrayList<>();

        @Option(names = "--dry-run", description = "Validate only")
        private boolean dryRun;

        @Option(names = "--json", description = "Emit JSON")
        private boolean json;

        @Override
        public Integer call() throws Exception {
            ToolRequest request = new ToolRequest(toolId, parseParameters(parameterValues));
            ValidationResult validation = root.runtime().execution().validate(request);
            if (!validation.valid()) {
                printValidation(validation, json);
                return ExitCodes.INPUT;
            }
            if (dryRun) {
                if (json) {
                    root.writeJsonSuccess(Map.of(
                            "valid", true,
                            "toolId", toolId,
                            "parameters", request.parameters()
                    ));
                } else {
                    root.out().println("Validation passed for " + toolId);
                }
                return ExitCodes.SUCCESS;
            }

            JobRecord job = root.runtime().execution().runAndWait(root.runtime().project(), request);

            int exitCode = ExitCodes.forJob(job);
            if (json) {
                if (exitCode == ExitCodes.SUCCESS) {
                    root.writeJsonSuccess(job);
                } else {
                    root.writeJsonFailure(JsonEnvelope.forJobFailure(job));
                }
            } else {
                root.out().println("Run: " + job.runId());
                root.out().println("Status: " + job.status());
                root.out().println("Run directory: " + job.runDirectory());
                root.out().println("Result: " + job.runDirectory().resolve("result.json"));
                root.out().println("Manifest: " + job.runDirectory().resolve("manifest.json"));
                if (job.errorMessage() != null) {
                    root.err().println("Error: " + job.errorMessage());
                }
            }
            return exitCode;
        }

        private void printValidation(ValidationResult validation, boolean json) throws Exception {
            if (json) {
                root.writeJsonFailure(JsonEnvelope.validationFailure(validation.issues()));
                return;
            }
            for (ValidationIssue issue : validation.issues()) {
                root.err().println(issue.severity() + " [" + issue.field() + "] " + issue.message());
                if (issue.suggestion() != null && !issue.suggestion().isBlank()) {
                    root.err().println("  " + issue.suggestion());
                }
            }
        }
    }

    @Command(
            mixinStandardHelpOptions = true,
            name = "jobs",
            description = "Inspect recent jobs",
            subcommands = {
                    JobsCommand.ListCommand.class,
                    JobsCommand.StatusCommand.class,
                    JobsCommand.LogsCommand.class,
                    JobsCommand.CancelCommand.class,
                    JobsCommand.RetryCommand.class
            }
    )
    static final class JobsCommand implements Runnable {
        @ParentCommand
        private OpenToolsCli root;

        @Override
        public void run() {
            root.out().println("Use `jobs list`, `jobs status`, `jobs logs`, or `jobs cancel`.");
        }

        @Command(mixinStandardHelpOptions = true, name = "list", description = "List recent jobs")
        static final class ListCommand implements Callable<Integer> {
            @ParentCommand
            private JobsCommand parent;

            @Option(names = "--limit", defaultValue = "20")
            private int limit;

            @Option(names = "--json")
            private boolean json;

            @Override
            public Integer call() throws Exception {
                List<JobRecord> jobs = parent.root.runtime().execution().recentJobs(limit);
                if (json) {
                    parent.root.writeJsonSuccess(jobs);
                    return 0;
                }
                parent.root.out().printf("%-38s %-18s %-10s %s%n", "RUN ID", "TOOL", "STATUS", "CREATED");
                for (JobRecord job : jobs) {
                    parent.root.out().printf(
                            "%-38s %-18s %-10s %s%n",
                            job.runId(),
                            job.toolId(),
                            job.status(),
                            job.createdAt()
                    );
                }
                return 0;
            }
        }

        @Command(mixinStandardHelpOptions = true, name = "status", description = "Show one job")
        static final class StatusCommand implements Callable<Integer> {
            @ParentCommand
            private JobsCommand parent;

            @Parameters(index = "0", paramLabel = "RUN_ID")
            private String runId;

            @Option(names = "--json")
            private boolean json;

            @Override
            public Integer call() throws Exception {
                JobRecord job = parent.root.runtime().execution().find(runId);
                int exitCode = ExitCodes.forJob(job);
                if (json) {
                    if (exitCode == ExitCodes.SUCCESS) {
                        parent.root.writeJsonSuccess(job);
                    } else {
                        parent.root.writeJsonFailure(JsonEnvelope.forJobFailure(job));
                    }
                } else {
                    parent.root.out().println("Run: " + job.runId());
                    parent.root.out().println("Tool: " + job.toolId() + " " + job.toolVersion());
                    parent.root.out().println("Status: " + job.status());
                    parent.root.out().println("Created: " + job.createdAt());
                    parent.root.out().println("Started: " + job.startedAt());
                    parent.root.out().println("Finished: " + job.finishedAt());
                    parent.root.out().println("Directory: " + job.runDirectory());
                    if (job.errorCode() != null) {
                        parent.root.err().println("Error " + job.errorCode() + ": " + job.errorMessage());
                    }
                }
                return exitCode;
            }
        }

        @Command(mixinStandardHelpOptions = true, name = "logs", description = "Print a run log")
        static final class LogsCommand implements Callable<Integer> {
            @ParentCommand
            private JobsCommand parent;

            @Parameters(index = "0", paramLabel = "RUN_ID")
            private String runId;

            @Option(names = "--tail", defaultValue = "200")
            private int tail;

            @Option(names = "--json")
            private boolean json;

            @Override
            public Integer call() throws Exception {
                JobRecord job = parent.root.runtime().execution().find(runId);
                Path log = job.runDirectory().resolve("run.log");
                if (!Files.isRegularFile(log)) {
                    if (json) {
                        parent.root.writeJsonFailure(JsonEnvelope.failure(
                                "RUN_LOG_NOT_FOUND",
                                "No run log found.",
                                "Check the run directory and job status.",
                                List.of()
                        ));
                    } else {
                        parent.root.err().println("No run log found.");
                    }
                    return ExitCodes.INPUT;
                }
                List<String> lines = Files.readAllLines(log, StandardCharsets.UTF_8);
                int from = Math.max(0, lines.size() - Math.max(1, tail));
                List<String> output = lines.subList(from, lines.size());
                if (json) {
                    parent.root.writeJsonSuccess(Map.of(
                            "runId", runId,
                            "lines", output
                    ));
                } else {
                    output.forEach(parent.root.out()::println);
                }
                return ExitCodes.SUCCESS;
            }
        }

        @Command(mixinStandardHelpOptions = true, name = "cancel", description = "Request cancellation")
        static final class CancelCommand implements Callable<Integer> {
            @ParentCommand
            private JobsCommand parent;

            @Parameters(index = "0", paramLabel = "RUN_ID")
            private String runId;

            @Option(names = "--json")
            private boolean json;

            @Override
            public Integer call() throws Exception {
                boolean accepted = parent.root.runtime().execution().cancel(runId);
                if (json) {
                    if (accepted) {
                        parent.root.writeJsonSuccess(Map.of(
                                "runId", runId,
                                "cancellationRequested", true
                        ));
                    } else {
                        parent.root.writeJsonFailure(JsonEnvelope.failure(
                                "JOB_NOT_CANCELLABLE",
                                "Job is not running or was not found.",
                                "Check job status before requesting cancellation.",
                                List.of()
                        ));
                    }
                } else {
                    parent.root.out().println(accepted
                            ? "Cancellation requested."
                            : "Job is not running or was not found.");
                }
                return accepted ? ExitCodes.SUCCESS : ExitCodes.INPUT;
            }
        }

        @Command(mixinStandardHelpOptions = true, name = "retry", description = "Retry a job with the same parameters")
        static final class RetryCommand implements Callable<Integer> {
            @ParentCommand
            private JobsCommand parent;

            @Parameters(index = "0", paramLabel = "RUN_ID")
            private String runId;

            @Option(names = "--json")
            private boolean json;

            @Override
            public Integer call() throws Exception {
                String newRunId = parent.root.runtime().execution().retry(
                        parent.root.runtime().project(),
                        runId
                );
                if (json) {
                    parent.root.writeJsonSuccess(Map.of(
                            "sourceRunId", runId,
                            "runId", newRunId,
                            "status", "QUEUED"
                    ));
                } else {
                    parent.root.out().println("Retry submitted: " + newRunId);
                }
                return 0;
            }
        }
    }

    @Command(
            mixinStandardHelpOptions = true,
            name = "plugins",
            description = "Manage installed plugins",
            subcommands = {
                    PluginsCommand.ListCommand.class,
                    PluginsCommand.InspectCommand.class,
                    PluginsCommand.EnableCommand.class,
                    PluginsCommand.DisableCommand.class
            }
    )
    static final class PluginsCommand implements Runnable {
        @ParentCommand
        private OpenToolsCli root;

        @Override
        public void run() {
            root.out().println("Use `plugins list`, `plugins inspect`, `plugins enable`, or `plugins disable`.");
        }

        @Command(mixinStandardHelpOptions = true, name = "list", description = "List installed plugins")
        static final class ListCommand implements Callable<Integer> {
            @ParentCommand
            private PluginsCommand parent;

            @Option(names = "--json")
            private boolean json;

            @Override
            public Integer call() throws Exception {
                var plugins = parent.root.runtime().plugins().list();
                if (json) {
                    parent.root.writeJsonSuccess(plugins);
                    return 0;
                }
                parent.root.out().printf(
                        "%-30s %-14s %-7s %s%n",
                        "ID",
                        "STATE",
                        "API",
                        "DIAGNOSTIC"
                );
                for (var plugin : plugins) {
                    parent.root.out().printf(
                            "%-30s %-14s %-7s %s%n",
                            plugin.id(),
                            plugin.state(),
                            plugin.apiVersion(),
                            plugin.diagnostic()
                    );
                }
                return 0;
            }
        }

        @Command(mixinStandardHelpOptions = true, name = "inspect", description = "Show one plugin and its dependency metadata")
        static final class InspectCommand implements Callable<Integer> {
            @ParentCommand
            private PluginsCommand parent;

            @Parameters(index = "0", paramLabel = "PLUGIN_ID")
            private String pluginId;

            @Option(names = "--json")
            private boolean json;

            @Override
            public Integer call() throws Exception {
                var plugins = parent.root.runtime().plugins().list();
                var plugin = plugins.stream()
                        .filter(info -> info.id().equals(pluginId))
                        .findFirst()
                        .orElse(null);
                if (plugin == null) {
                    if (json) {
                        parent.root.writeJsonFailure(JsonEnvelope.failure(
                                "PLUGIN_NOT_FOUND",
                                "Unknown plugin: " + pluginId,
                                "Run `plugins list`.",
                                List.of()
                        ));
                    } else {
                        parent.root.err().println("Unknown plugin: " + pluginId);
                    }
                    return ExitCodes.INPUT;
                }
                if (json) {
                    parent.root.writeJsonSuccess(plugin);
                    return 0;
                }
                parent.root.out().println("ID: " + plugin.id());
                parent.root.out().println("Name: " + plugin.name());
                parent.root.out().println("Version: " + plugin.version());
                parent.root.out().println("API: " + plugin.apiVersion());
                parent.root.out().println("State: " + plugin.state());
                parent.root.out().println("Source: " + plugin.source());
                parent.root.out().println("Dependencies: " + String.join(", ", plugin.dependencies()));
                parent.root.out().println("Capabilities: " + String.join(", ", plugin.requestedCapabilities()));
                if (!plugin.diagnostic().isBlank()) {
                    parent.root.out().println("Diagnostic: " + plugin.diagnostic());
                }
                plugin.metadata().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(entry -> parent.root.out().println(entry.getKey() + ": " + entry.getValue()));
                return 0;
            }
        }

        @Command(mixinStandardHelpOptions = true, name = "enable", description = "Enable a plugin for future runs")
        static final class EnableCommand implements Callable<Integer> {
            @ParentCommand
            private PluginsCommand parent;

            @Parameters(index = "0", paramLabel = "PLUGIN_ID")
            private String pluginId;

            @Option(names = "--json")
            private boolean json;

            @Override
            public Integer call() throws Exception {
                return setEnabled(parent, pluginId, true, json);
            }
        }

        @Command(mixinStandardHelpOptions = true, name = "disable", description = "Disable a plugin for future runs")
        static final class DisableCommand implements Callable<Integer> {
            @ParentCommand
            private PluginsCommand parent;

            @Parameters(index = "0", paramLabel = "PLUGIN_ID")
            private String pluginId;

            @Option(names = "--json")
            private boolean json;

            @Override
            public Integer call() throws Exception {
                return setEnabled(parent, pluginId, false, json);
            }
        }

        private static int setEnabled(
                PluginsCommand parent,
                String pluginId,
                boolean enabled,
                boolean json
        ) throws Exception {
            var plugins = enabled
                    ? parent.root.runtime().plugins().enable(pluginId)
                    : parent.root.runtime().plugins().disable(pluginId);
            var plugin = plugins.stream()
                    .filter(info -> info.id().equals(pluginId))
                    .findFirst()
                    .orElse(null);
            if (plugin == null) {
                if (json) {
                    parent.root.writeJsonFailure(JsonEnvelope.failure(
                            "PLUGIN_NOT_FOUND",
                            "Unknown plugin: " + pluginId,
                            "Run `plugins list`.",
                            List.of()
                    ));
                } else {
                    parent.root.err().println("Unknown plugin: " + pluginId);
                }
                return ExitCodes.INPUT;
            }
            boolean reached = enabled
                    ? plugin.state() == io.opentools.plugin.host.PluginState.ENABLED
                    : plugin.state() == io.opentools.plugin.host.PluginState.DISABLED;
            if (!reached) {
                if (json) {
                    parent.root.writeJsonFailure(JsonEnvelope.failure(
                            "PLUGIN_STATE_NOT_ACHIEVED",
                            "Plugin state is " + plugin.state() + ".",
                            plugin.diagnostic(),
                            List.of(),
                            Map.of("plugin", plugin)
                    ));
                } else {
                    parent.root.out().println(plugin.id() + ": " + plugin.state());
                    if (!plugin.diagnostic().isBlank()) {
                        parent.root.out().println(plugin.diagnostic());
                    }
                }
                return plugin.state() == io.opentools.plugin.host.PluginState.INCOMPATIBLE
                        ? ExitCodes.DEPENDENCY
                        : ExitCodes.EXECUTION;
            }
            if (json) {
                parent.root.writeJsonSuccess(plugin);
            } else {
                parent.root.out().println(plugin.id() + ": " + plugin.state());
                if (!plugin.diagnostic().isBlank()) {
                    parent.root.out().println(plugin.diagnostic());
                }
            }
            return ExitCodes.SUCCESS;
        }

        private static String rootMessage(Throwable throwable) {
            Throwable current = throwable;
            while (current.getCause() != null) {
                current = current.getCause();
            }
            return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
        }
    }

    @Command(
            mixinStandardHelpOptions = true,
            name = "format",
            description = "Inspect file formats",
            subcommands = {FormatCommand.DetectCommand.class}
    )
    static final class FormatCommand implements Runnable {
        @ParentCommand
        private OpenToolsCli root;

        @Override
        public void run() {
            root.out().println("Use `format detect <file>`.");
        }

        @Command(mixinStandardHelpOptions = true, name = "detect", description = "Detect a file format")
        static final class DetectCommand implements Callable<Integer> {
            @ParentCommand
            private FormatCommand parent;

            @Parameters(index = "0", paramLabel = "FILE")
            private Path file;

            @Option(names = "--json")
            private boolean json;

            @Override
            public Integer call() throws Exception {
                if (!Files.isRegularFile(file)) {
                    if (json) {
                        parent.root.writeJsonFailure(JsonEnvelope.failure(
                                "FILE_NOT_FOUND",
                                "File does not exist: " + file,
                                "Choose an existing readable file.",
                                List.of()
                        ));
                    } else {
                        parent.root.err().println("File does not exist: " + file);
                    }
                    return ExitCodes.INPUT;
                }
                FormatDetection detection = new FormatDetector().detect(file);
                boolean unknown = detection.type().name().equals("UNKNOWN");
                if (json && unknown) {
                    parent.root.writeJsonFailure(JsonEnvelope.failure(
                            "FORMAT_UNKNOWN",
                            "Could not identify the input format.",
                            "Check the file extension and content.",
                            List.of(),
                            Map.of("detection", detection)
                    ));
                } else if (json) {
                    parent.root.writeJsonSuccess(detection);
                } else {
                    parent.root.out().println("Format: " + detection.type().displayName());
                    parent.root.out().println("Reason: " + detection.reason());
                    parent.root.out().println("Size: " + detection.fileSize() + " bytes");
                }
                return unknown ? ExitCodes.INPUT : ExitCodes.SUCCESS;
            }
        }
    }

    @Command(mixinStandardHelpOptions = true, name = "doctor", description = "Check the local runtime")
    static final class DoctorCommand implements Callable<Integer> {
        @ParentCommand
        private OpenToolsCli root;

        @Option(names = "--json")
        private boolean json;

        @Override
        public Integer call() throws Exception {
            CliRuntime runtime = root.runtime();
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("javaVersion", System.getProperty("java.version"));
            report.put("javaVendor", System.getProperty("java.vendor"));
            report.put("javaHome", System.getProperty("java.home"));
            report.put("userDir", Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize());
            report.put("applicationHome", ExternalRuntime.appHome());
            report.put("processPath", System.getenv("PATH"));
            report.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version"));
            report.put("projectName", runtime.project().name());
            report.put("projectRoot", runtime.project().root().toString());
            report.put("database", runtime.project().databasePath().toString());
            report.put("tasks", runtime.registry().list().size());
            report.put("plugins", runtime.plugins().list().size());
            report.put("language", runtime.language().activeCode());
            var externalTools = ExternalTools.diagnostics();
            report.put("externalTools", externalTools);
            report.put("checkedAt", Instant.now().toString());
            if (json) {
                root.writeJsonSuccess(report);
            } else {
                root.out().println("Java: " + report.get("javaVersion"));
                root.out().println("OS: " + report.get("os"));
                root.out().println("Project: " + report.get("projectName"));
                root.out().println("Project root: " + report.get("projectRoot"));
                root.out().println("Registered tools: " + report.get("tasks"));
                root.out().println("Plugin entries: " + report.get("plugins"));
                root.out().println("Language: " + report.get("language"));
                root.out().println("External tools:");
                for (var tool : externalTools) {
                    root.out().printf(
                            "  %-18s %-8s %s%s%n",
                            tool.id(),
                            tool.available() ? "available" : "missing",
                            tool.executable() == null ? "" : tool.executable(),
                            tool.version() == null ? "" : " (" + tool.version() + ")"
                    );
                }
            }
            return 0;
        }
    }

    @Command(
            mixinStandardHelpOptions = true,
            name = "project",
            description = "Inspect the active project",
            subcommands = {ProjectCommand.InfoCommand.class}
    )
    static final class ProjectCommand implements Runnable {
        @ParentCommand
        private OpenToolsCli root;

        @Override
        public void run() {
            root.out().println("Use `project info`.");
        }

        @Command(mixinStandardHelpOptions = true, name = "info", description = "Show project information")
        static final class InfoCommand implements Callable<Integer> {
            @ParentCommand
            private ProjectCommand parent;

            @Option(names = "--json")
            private boolean json;

            @Override
            public Integer call() throws Exception {
                var project = parent.root.runtime().project();
                if (json) {
                    parent.root.writeJsonSuccess(project);
                } else {
                    parent.root.out().println("Name: " + project.name());
                    parent.root.out().println("ID: " + project.id());
                    parent.root.out().println("Root: " + project.root());
                    parent.root.out().println("Created: " + project.createdAt());
                }
                return 0;
            }
        }
    }

    private static Map<String, Object> parseParameters(List<String> values) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        for (String value : values) {
            int separator = value.indexOf('=');
            if (separator <= 0) {
                throw new CommandLine.ParameterException(
                        new CommandLine(new OpenToolsCli()),
                        "Parameter must use KEY=VALUE: " + value
                );
            }
            parameters.put(value.substring(0, separator), value.substring(separator + 1));
        }
        return parameters;
    }

    @Command(
            mixinStandardHelpOptions = true,
            name = "mcp",
            description = "Run the local OpenTools MCP server",
            subcommands = {McpCommand.ServeCommand.class}
    )
    static final class McpCommand implements Runnable {
        @ParentCommand
        private OpenToolsCli root;

        @Override
        public void run() {
            root.out().println("Use `mcp serve --transport stdio` or `mcp serve --transport http`.");
        }

        @Command(mixinStandardHelpOptions = true, name = "serve", description = "Serve MCP over stdio or authenticated loopback HTTP")
        static final class ServeCommand implements Callable<Integer> {
            @ParentCommand
            private McpCommand parent;

            @Option(names = "--transport", defaultValue = "stdio")
            private String transport;

            @Option(names = "--host", defaultValue = "127.0.0.1")
            private String host;

            @Option(names = "--port", defaultValue = "0")
            private int port;

            @Option(names = "--token", description = "HTTP bearer token; generated when omitted")
            private String token;

            @Option(names = "--workspace", description = "Additional allowed workspace root")
            private List<Path> workspaces = new ArrayList<>();

            @Option(names = "--json")
            private boolean json;

            @Override
            public Integer call() throws Exception {
                Path configuredAppHome = parent.root.appHome == null
                        ? io.opentools.core.runtime.AppPaths.applicationHome()
                        : parent.root.appHome.toAbsolutePath().normalize();
                try (AutomationRuntime runtime = AutomationRuntime.open(
                        parent.root.projectPath,
                        configuredAppHome,
                        workspaces
                )) {
                    McpToolServer protocol = new McpToolServer(runtime.toolAutomation());
                    if (transport.equalsIgnoreCase("stdio")) {
                        parent.root.err().println(
                                "OpenTools MCP stdio ready (protocol "
                                        + McpToolServer.LATEST_PROTOCOL_VERSION + ")"
                        );
                        new McpStdioServer(protocol).run(System.in, System.out);
                        return 0;
                    }
                    if (!transport.equalsIgnoreCase("http")) {
                        if (json) {
                            parent.root.writeJsonFailure(JsonEnvelope.failure(
                                    "UNSUPPORTED_TRANSPORT",
                                    "Unsupported MCP transport: " + transport,
                                    "Use stdio or http.",
                                    List.of()
                            ));
                        } else {
                            parent.root.err().println("Unsupported MCP transport: " + transport);
                        }
                        return ExitCodes.INPUT;
                    }
                    if (port < 0 || port > 65_535) {
                        if (json) {
                            parent.root.writeJsonFailure(JsonEnvelope.failure(
                                    "INVALID_PORT",
                                    "--port must be between 0 and 65535.",
                                    "Choose an available local port.",
                                    List.of()
                            ));
                        } else {
                            parent.root.err().println("--port must be between 0 and 65535.");
                        }
                        return ExitCodes.INPUT;
                    }
                    try (McpHttpServer http = new McpHttpServer(
                            protocol,
                            host,
                            port,
                            token
                    )) {
                        http.start();
                        if (json) {
                            parent.root.writeJsonSuccess(Map.of(
                                    "endpoint", http.endpoint(),
                                    "token", http.token()
                            ));
                            parent.root.out().flush();
                        } else {
                            parent.root.err().println("MCP endpoint: " + http.endpoint());
                            parent.root.err().println("Authorization: Bearer " + http.token());
                        }
                        http.await();
                        return 0;
                    }
                }
            }
        }
    }

    @Command(
            mixinStandardHelpOptions = true,
            name = "batch",
            description = "Run task parameter sets from a JSON file",
            subcommands = {BatchCommand.RunCommand.class}
    )
    static final class BatchCommand implements Runnable {
        @ParentCommand
        private OpenToolsCli root;

        @Override
        public void run() {
            root.out().println("Use `batch run <batch.json>`.");
        }

        @Command(mixinStandardHelpOptions = true, name = "run", description = "Validate and run jobs sequentially")
        static final class RunCommand implements Callable<Integer> {
            @ParentCommand
            private BatchCommand parent;

            @Parameters(index = "0", paramLabel = "BATCH_JSON")
            private Path batchFile;

            @Option(names = "--workspace", description = "Additional allowed workspace root")
            private List<Path> workspaces = new ArrayList<>();

            @Option(names = "--dry-run", description = "Validate every item without submitting jobs")
            private boolean dryRun;

            @Option(names = "--json")
            private boolean json;

            @Override
            public Integer call() throws Exception {
                Path configuredAppHome = parent.root.appHome == null
                        ? io.opentools.core.runtime.AppPaths.applicationHome()
                        : parent.root.appHome.toAbsolutePath().normalize();
                try (AutomationRuntime runtime = AutomationRuntime.open(
                        parent.root.projectPath,
                        configuredAppHome,
                        workspaces
                )) {
                    Path config = runtime.workspacePolicy().resolvePath(batchFile.toString());
                    if (!runtime.workspacePolicy().isAllowed(config)) {
                        if (json) {
                            parent.root.writeJsonFailure(JsonEnvelope.failure(
                                    "PATH_OUTSIDE_WORKSPACE",
                                    "Batch file is outside the allowed workspace: " + config,
                                    "Place the batch file under the project or an allowed workspace root.",
                                    List.of()
                            ));
                        } else {
                            parent.root.err().println("Batch file is outside the allowed workspace: " + config);
                        }
                        return ExitCodes.INPUT;
                    }
                    if (Files.size(config) > 10L * 1024 * 1024) {
                        if (json) {
                            parent.root.writeJsonFailure(JsonEnvelope.failure(
                                    "BATCH_FILE_TOO_LARGE",
                                    "Batch file exceeds the 10 MB limit.",
                                    "Split the batch or reduce embedded text parameters.",
                                    List.of()
                            ));
                        } else {
                            parent.root.err().println("Batch file exceeds the 10 MB limit.");
                        }
                        return ExitCodes.INPUT;
                    }
                    io.opentools.mcp.ToolAutomationService.BatchSpec loaded = JSON.readValue(
                            config.toFile(),
                            io.opentools.mcp.ToolAutomationService.BatchSpec.class
                    );
                    var items = loaded.jobs();
                    if (dryRun) {
                        items = items.stream().map(item ->
                                new io.opentools.mcp.ToolAutomationService.BatchItem(
                                        item.toolId(),
                                        item.parameters(),
                                        true
                                )).toList();
                    }
                    var effective = new io.opentools.mcp.ToolAutomationService.BatchSpec(
                            loaded.continueOnError(),
                            items
                    );
                    Map<String, Object> result = runtime.toolAutomation().runBatch(effective);
                    boolean failed = ((List<?>) result.get("jobs")).stream()
                            .map(item -> ((Map<?, ?>) item).get("status"))
                            .anyMatch(status -> !"SUCCEEDED".equals(status) && !"VALID".equals(status));
                    if (json) {
                        if (failed) {
                            parent.root.writeJsonFailure(JsonEnvelope.failure(
                                    "BATCH_FAILED",
                                    "One or more batch jobs failed.",
                                    "Inspect the job results and validation issues.",
                                    List.of(),
                                    Map.of("batch", result)
                            ));
                        } else {
                            parent.root.writeJsonSuccess(result);
                        }
                    } else {
                        result.forEach((key, value) -> parent.root.out().println(key + ": " + value));
                    }
                    return failed ? ExitCodes.EXECUTION : ExitCodes.SUCCESS;
                }
            }
        }
    }
}
