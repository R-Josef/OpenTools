package io.opentools.mcp;

import io.opentools.analysis.BuiltInAnalysis;
import io.opentools.core.job.SqliteJobStore;
import io.opentools.core.job.TaskExecutionService;
import io.opentools.core.tool.ToolRegistry;
import io.opentools.core.workspace.Project;
import io.opentools.core.workspace.ProjectService;
import io.opentools.external.ExternalTools;
import io.opentools.formats.BuiltInTools;
import io.opentools.plugin.host.PluginManager;
import io.opentools.visualization.BuiltInVisualizations;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class AutomationRuntime implements AutoCloseable {
    private final ToolRegistry registry;
    private final Project project;
    private final PluginManager plugins;
    private final TaskExecutionService execution;
    private final WorkspacePolicy workspacePolicy;
    private final TaskAutomationService tasks;

    private AutomationRuntime(
            ToolRegistry registry,
            Project project,
            PluginManager plugins,
            TaskExecutionService execution,
            WorkspacePolicy workspacePolicy
    ) {
        this.registry = registry;
        this.project = project;
        this.plugins = plugins;
        this.execution = execution;
        this.workspacePolicy = workspacePolicy;
        this.tasks = new TaskAutomationService(this);
    }

    public static AutomationRuntime open(
            Path projectPath,
            Path appHome,
            List<Path> workspaceRoots
    ) throws Exception {
        ProjectService projectService = new ProjectService();
        Project project;
        if (projectPath == null) {
            project = projectService.defaultProject();
        } else {
            Path normalized = projectPath.toAbsolutePath().normalize();
            project = Files.exists(normalized.resolve("project.json"))
                    ? projectService.open(normalized)
                    : projectService.create(normalized, normalized.getFileName().toString());
        }
        ToolRegistry registry = new ToolRegistry();
        BuiltInTools.register(registry);
        BuiltInAnalysis.register(registry);
        ExternalTools.register(registry);
        BuiltInVisualizations.register(registry);
        Path configuredAppHome = appHome == null
                ? Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
                : appHome.toAbsolutePath().normalize();
        PluginManager plugins = new PluginManager(configuredAppHome, registry);
        plugins.scan();
        SqliteJobStore jobStore = new SqliteJobStore(project.databasePath());
        jobStore.initialize();
        TaskExecutionService execution = new TaskExecutionService(registry, jobStore);
        WorkspacePolicy workspacePolicy = new WorkspacePolicy(project.root(), workspaceRoots);
        return new AutomationRuntime(registry, project, plugins, execution, workspacePolicy);
    }

    public ToolRegistry registry() {
        return registry;
    }

    public Project project() {
        return project;
    }

    public PluginManager plugins() {
        return plugins;
    }

    public TaskExecutionService execution() {
        return execution;
    }

    public WorkspacePolicy workspacePolicy() {
        return workspacePolicy;
    }

    public TaskAutomationService tasks() {
        return tasks;
    }

    @Override
    public void close() throws Exception {
        try {
            plugins.close();
        } finally {
            execution.close();
        }
    }
}
