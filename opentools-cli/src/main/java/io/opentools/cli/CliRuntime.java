package io.opentools.cli;

import io.opentools.analysis.BuiltInAnalysis;
import io.opentools.core.job.SqliteJobStore;
import io.opentools.core.job.ToolExecutionService;
import io.opentools.core.i18n.LanguageManager;
import io.opentools.core.tool.ToolRegistry;
import io.opentools.core.workspace.Project;
import io.opentools.core.workspace.ProjectService;
import io.opentools.formats.BuiltInTools;
import io.opentools.external.ExternalTools;
import io.opentools.plugin.host.PluginManager;
import io.opentools.plugin.host.ToolTextResolver;
import io.opentools.visualization.BuiltInVisualizations;

import java.nio.file.Files;
import java.nio.file.Path;

public final class CliRuntime implements AutoCloseable {
    private final ToolRegistry registry;
    private final Project project;
    private final SqliteJobStore jobStore;
    private final PluginManager pluginManager;
    private final LanguageManager language;
    private ToolExecutionService executionService;

    private CliRuntime(
            ToolRegistry registry,
            Project project,
            SqliteJobStore jobStore,
            PluginManager pluginManager,
            LanguageManager language
    ) {
        this.registry = registry;
        this.project = project;
        this.jobStore = jobStore;
        this.pluginManager = pluginManager;
        this.language = language;
    }

    public static CliRuntime open(Path projectPath, Path appHome, String forcedLanguage) throws Exception {
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
        LanguageManager language = new LanguageManager(appHome, forcedLanguage);
        PluginManager pluginManager = new PluginManager(appHome, registry, language::activeCode);
        pluginManager.scan();
        return new CliRuntime(
                registry,
                project,
                new SqliteJobStore(project.databasePath()),
                pluginManager,
                language
        );
    }

    public ToolRegistry registry() {
        return registry;
    }

    public Project project() {
        return project;
    }

    public PluginManager plugins() {
        return pluginManager;
    }

    public LanguageManager language() {
        return language;
    }

    public ToolTextResolver toolText() {
        return new ToolTextResolver(pluginManager, language);
    }

    public synchronized ToolExecutionService execution() {
        if (executionService == null) {
            executionService = new ToolExecutionService(registry, jobStore);
        }
        return executionService;
    }

    @Override
    public synchronized void close() throws Exception {
        try {
            pluginManager.close();
        } finally {
            if (executionService != null) {
                executionService.close();
            }
        }
    }
}
