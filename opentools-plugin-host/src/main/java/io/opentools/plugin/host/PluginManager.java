package io.opentools.plugin.host;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentools.api.tool.OpenTool;
import io.opentools.core.tool.ToolRegistry;
import io.opentools.api.tool.CancellationToken;
import io.opentools.plugin.OpenToolsPlugin;
import io.opentools.plugin.PageContribution;
import io.opentools.plugin.PluginApi;
import io.opentools.plugin.PluginContext;
import io.opentools.plugin.PluginDescriptor;

import javax.swing.JPanel;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

public final class PluginManager implements AutoCloseable {
    private static final String DESCRIPTOR_ENTRY = "META-INF/opentools-plugin.json";
    private static final long MAX_PLUGIN_JAR_BYTES = 128L * 1024 * 1024;
    private static final long MAX_PLUGIN_ENTRY_BYTES = 64L * 1024 * 1024;
    private static final long MAX_PLUGIN_UNCOMPRESSED_BYTES = 512L * 1024 * 1024;
    private static final int MAX_PLUGIN_ENTRIES = 20_000;

    private final Path appRoot;
    private final Path pluginsDirectory;
    private final Path pluginDataDirectory;
    private final ToolRegistry toolRegistry;
    private final PluginStateStore stateStore;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final List<PluginInfo> discovered = new ArrayList<>();
    private final List<LoadedPlugin> loaded = new ArrayList<>();
    private final List<PageContribution> pages = new ArrayList<>();
    private final Map<String, Candidate> candidates = new LinkedHashMap<>();

    public PluginManager(Path appRoot, ToolRegistry toolRegistry) {
        this.appRoot = appRoot.toAbsolutePath().normalize();
        this.pluginsDirectory = this.appRoot.resolve("plugins");
        this.pluginDataDirectory = pluginsDirectory.resolve("data");
        this.toolRegistry = toolRegistry;
        this.stateStore = new PluginStateStore(pluginsDirectory);
    }

    public synchronized List<PluginInfo> scan() {
        shutdownLoaded();
        discovered.clear();
        candidates.clear();
        pages.clear();
        try {
            Files.createDirectories(pluginsDirectory);
            Files.createDirectories(pluginDataDirectory);
        } catch (IOException ex) {
            discovered.add(systemInfo("plugin-system", "Plugin System", PluginState.FAILED,
                    "Could not create plugin directories: " + ex.getMessage()));
            return list();
        }
        if (!stateStore.diagnostic().isBlank()) {
            discovered.add(systemInfo("plugin-state", "Plugin State", PluginState.FAILED,
                    stateStore.diagnostic()));
        }

        discoverPlugins();
        resolveAndLoadPlugins();
        return list();
    }

    public synchronized List<PluginInfo> enable(String id) {
        Candidate candidate = ensureScanned(id);
        if (candidate == null) {
            return list();
        }
        enableDependencies(candidate.descriptor());
        stateStore.setEnabled(id, true);
        return scan();
    }

    public synchronized List<PluginInfo> disable(String id) {
        if (ensureScanned(id) == null) {
            return list();
        }
        stateStore.setEnabled(id, false);
        return scan();
    }

    public synchronized List<PluginInfo> list() {
        return discovered.stream()
                .sorted(Comparator.comparing(PluginInfo::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(PluginInfo::id))
                .toList();
    }

    public synchronized List<PageContribution> pages() {
        return List.copyOf(pages);
    }

    public synchronized void disabledAll() {
        shutdownLoaded();
        discovered.replaceAll(info -> loadedState(info, PluginState.DISABLED, "Plugin disabled."));
    }

    public Path pluginsDirectory() {
        return pluginsDirectory;
    }

    public synchronized boolean isKnown(String id) {
        return discovered.stream().anyMatch(info -> info.id().equals(id));
    }

    @Override
    public synchronized void close() {
        shutdownLoaded();
    }

    private void discoverPlugins() {
        try (Stream<Path> files = Files.list(pluginsDirectory)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(this::discoverPlugin);
        } catch (IOException ex) {
            discovered.add(systemInfo("plugin-scan", "Plugin Scan", PluginState.FAILED,
                    "Could not scan plugins: " + ex.getMessage()));
        }
    }

    private void discoverPlugin(Path jarPath) {
        try {
            validatePluginJar(jarPath);
            PluginDescriptor descriptor = readDescriptor(jarPath);
            if (candidates.containsKey(descriptor.id())) {
                discovered.add(new PluginInfo(
                        descriptor.id() + ".duplicate-" + safeId(stripExtension(jarPath.getFileName().toString())),
                        descriptor.name() + " [duplicate]",
                        descriptor.version(),
                        descriptor.apiVersion(),
                        jarPath.toString(),
                        PluginState.FAILED,
                        "Duplicate plugin id; using " + candidates.get(descriptor.id()).source(),
                        descriptor.dependencies(),
                        descriptor.requestedCapabilities(),
                        Map.of()
                ));
                return;
            }
            Candidate candidate = new Candidate(descriptor, jarPath.toAbsolutePath().normalize());
            candidates.put(descriptor.id(), candidate);
        } catch (Exception ex) {
            String fileName = jarPath.getFileName().toString();
            discovered.add(new PluginInfo(
                    fileName,
                    fileName,
                    "unknown",
                    "",
                    jarPath.toString(),
                    PluginState.FAILED,
                    rootMessage(ex),
                    List.of(),
                    List.of(),
                    Map.of()
            ));
        }
    }

    private void resolveAndLoadPlugins() {
        Map<String, PluginInfo> resolved = new LinkedHashMap<>();
        for (Candidate candidate : candidates.values()) {
            PluginInfo info = info(candidate.descriptor(), candidate.source(), PluginState.DISCOVERED, "", Map.of());
            PluginDescriptor descriptor = candidate.descriptor();
            if (!PluginApi.isCompatible(descriptor.apiVersion())) {
                info = loadedState(info, PluginState.INCOMPATIBLE,
                        "Plugin requires API " + descriptor.apiVersion() + "; host API is " + PluginApi.VERSION);
            } else if (!PluginApi.KNOWN_CAPABILITIES.containsAll(descriptor.requestedCapabilities())) {
                info = loadedState(info, PluginState.INCOMPATIBLE,
                        "Unsupported requested capabilities: " + unsupportedCapabilities(descriptor));
            } else if (!stateStore.isEnabled(descriptor.id())) {
                info = loadedState(info, PluginState.DISABLED,
                        "Disabled by user. Dependencies of this plugin will not be enabled.");
            } else {
                List<String> missing = descriptor.dependencies().stream()
                        .filter(id -> !candidates.containsKey(id))
                        .toList();
                if (!missing.isEmpty()) {
                    info = loadedState(info, PluginState.INCOMPATIBLE,
                            "Missing plugin dependencies: " + String.join(", ", missing));
                }
            }
            resolved.put(descriptor.id(), info);
        }

        boolean progressed;
        do {
            progressed = false;
            for (Candidate candidate : candidates.values()) {
                PluginInfo info = resolved.get(candidate.descriptor().id());
                if (!isPending(info)) {
                    continue;
                }
                List<String> unavailable = candidate.descriptor().dependencies().stream()
                        .filter(id -> !resolved.get(id).state().equals(PluginState.ENABLED))
                        .toList();
                if (!unavailable.isEmpty()) {
                    long enabledDependencies = unavailable.stream()
                            .filter(id -> resolved.get(id).state() == PluginState.DISCOVERED)
                            .count();
                    if (enabledDependencies > 0) {
                        continue;
                    }
                    info = loadedState(info, PluginState.INCOMPATIBLE,
                            "Required plugin dependencies are unavailable: " + String.join(", ", unavailable));
                    resolved.put(candidate.descriptor().id(), info);
                    progressed = true;
                    continue;
                }
                try {
                    LoadedPlugin plugin = loadPlugin(candidate);
                    loaded.add(plugin);
                    pages.addAll(plugin.registrations.pages);
                    info = loadedState(info, PluginState.ENABLED, "");
                    progressed = true;
                } catch (Exception ex) {
                    info = loadedState(info, PluginState.FAILED, rootMessage(ex));
                    progressed = true;
                }
                resolved.put(candidate.descriptor().id(), info);
            }
        } while (progressed);

        for (Candidate candidate : candidates.values()) {
            PluginInfo info = resolved.get(candidate.descriptor().id());
            if (isPending(info)) {
                info = loadedState(info, PluginState.INCOMPATIBLE,
                        "Plugin dependency cycle detected.");
            }
            discovered.add(info);
        }
    }

    private LoadedPlugin loadPlugin(Candidate candidate) throws Exception {
        PluginDescriptor descriptor = candidate.descriptor();
        URL[] urls = classpath(candidate);
        PluginClassLoader classLoader = new PluginClassLoader(urls, PluginManager.class.getClassLoader());
        PluginRegistrations registrations = null;
        try {
            Class<?> rawClass = Class.forName(descriptor.mainClass(), true, classLoader);
            if (!OpenToolsPlugin.class.isAssignableFrom(rawClass)) {
                throw new IllegalArgumentException("Main class does not implement OpenToolsPlugin");
            }
            OpenToolsPlugin plugin = (OpenToolsPlugin) rawClass.getDeclaredConstructor().newInstance();
            Path dataDirectory = pluginDataDirectory.resolve(descriptor.id());
            Files.createDirectories(dataDirectory);
            registrations = new PluginRegistrations(
                    descriptor.id(),
                    dataDirectory,
                    descriptor.requestedCapabilities()
            );
            registrations.log("Enabling " + descriptor.id() + " " + descriptor.version());
            Thread thread = Thread.currentThread();
            ClassLoader previous = thread.getContextClassLoader();
            try {
                thread.setContextClassLoader(classLoader);
                plugin.onEnable(registrations);
            } finally {
                thread.setContextClassLoader(previous);
            }
            return new LoadedPlugin(descriptor, candidate.source(), classLoader, plugin, registrations);
        } catch (Exception ex) {
            if (registrations != null) {
                registrations.rollback();
            }
            classLoader.close();
            throw ex;
        }
    }

    private URL[] classpath(Candidate root) throws Exception {
        LinkedHashSet<Path> jars = new LinkedHashSet<>();
        collectDependencyJars(root.descriptor().id(), jars, new LinkedHashSet<>());
        URL[] urls = new URL[jars.size()];
        int index = 0;
        for (Path jar : jars) {
            urls[index++] = jar.toUri().toURL();
        }
        return urls;
    }

    private void collectDependencyJars(String id, LinkedHashSet<Path> jars, Set<String> visiting) {
        if (!visiting.add(id)) {
            return;
        }
        Candidate candidate = candidates.get(id);
        if (candidate == null) {
            return;
        }
        jars.add(candidate.source());
        for (String dependency : candidate.descriptor().dependencies()) {
            collectDependencyJars(dependency, jars, visiting);
        }
    }

    private PluginDescriptor readDescriptor(Path jarPath) throws IOException {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            JarEntry entry = jar.getJarEntry(DESCRIPTOR_ENTRY);
            if (entry == null) {
                throw new IllegalArgumentException("Missing " + DESCRIPTOR_ENTRY);
            }
            try (var input = jar.getInputStream(entry)) {
                return objectMapper.readValue(input, PluginDescriptor.class);
            }
        }
    }

    static void validatePluginJar(Path jarPath) throws IOException {
        if (Files.size(jarPath) > MAX_PLUGIN_JAR_BYTES) {
            throw new IOException("Plugin JAR exceeds size limit: " + jarPath.getFileName());
        }
        int entries = 0;
        long total = 0;
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var enumeration = jar.entries();
            while (enumeration.hasMoreElements()) {
                JarEntry entry = enumeration.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                entries++;
                if (entries > MAX_PLUGIN_ENTRIES) {
                    throw new IOException("Plugin JAR contains too many entries");
                }
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("/")
                        || name.matches("^[A-Za-z]:.*")
                        || name.startsWith("../")
                        || name.contains("/../")) {
                    throw new IOException("Unsafe JAR entry: " + entry.getName());
                }
                long size = entry.getSize();
                if (size < 0 || size > MAX_PLUGIN_ENTRY_BYTES) {
                    throw new IOException("Plugin JAR entry exceeds size limit: " + entry.getName());
                }
                total += size;
                if (total > MAX_PLUGIN_UNCOMPRESSED_BYTES) {
                    throw new IOException("Plugin JAR exceeds uncompressed size limit");
                }
            }
        }
    }

    private void shutdownLoaded() {
        for (LoadedPlugin plugin : new ArrayList<>(loaded)) {
            try {
                plugin.registrations.log("Disabling " + plugin.descriptor.id());
                Thread thread = Thread.currentThread();
                ClassLoader previous = thread.getContextClassLoader();
                try {
                    thread.setContextClassLoader(plugin.classLoader);
                    plugin.instance.onDisable();
                } finally {
                    thread.setContextClassLoader(previous);
                }
            } catch (Exception ex) {
                plugin.registrations.log("Disable callback failed: " + rootMessage(ex));
            }
            List<PageContribution> contributedPages = List.copyOf(plugin.registrations.pages);
            plugin.registrations.rollback();
            pages.removeAll(contributedPages);
            try {
                plugin.classLoader.close();
            } catch (IOException ignored) {
            }
        }
        loaded.clear();
    }

    private Candidate ensureScanned(String id) {
        if (!isKnown(id)) {
            scan();
        }
        return candidates.get(id);
    }

    private void enableDependencies(PluginDescriptor descriptor) {
        enableDependencies(descriptor, new LinkedHashSet<>());
    }

    private void enableDependencies(PluginDescriptor descriptor, Set<String> visited) {
        if (!visited.add(descriptor.id())) {
            return;
        }
        for (String dependency : descriptor.dependencies()) {
            Candidate candidate = candidates.get(dependency);
            if (candidate != null) {
                stateStore.setEnabled(dependency, true);
                enableDependencies(candidate.descriptor(), visited);
            }
        }
    }

    private String unsupportedCapabilities(PluginDescriptor descriptor) {
        return descriptor.requestedCapabilities().stream()
                .filter(capability -> !PluginApi.KNOWN_CAPABILITIES.contains(capability))
                .toList()
                .toString();
    }

    private static boolean isPending(PluginInfo info) {
        return info.state() == PluginState.DISCOVERED;
    }

    private static PluginInfo loadedState(PluginInfo info, PluginState state, String diagnostic) {
        return new PluginInfo(
                info.id(),
                info.name(),
                info.version(),
                info.apiVersion(),
                info.source(),
                state,
                diagnostic,
                info.dependencies(),
                info.requestedCapabilities(),
                info.metadata()
        );
    }

    private static PluginInfo info(
            PluginDescriptor descriptor,
            Path source,
            PluginState state,
            String diagnostic,
            Map<String, String> metadata
    ) {
        Map<String, String> details = new LinkedHashMap<>(metadata);
        details.put("author", descriptor.author());
        details.put("description", descriptor.description());
        return new PluginInfo(
                descriptor.id(),
                descriptor.name(),
                descriptor.version(),
                descriptor.apiVersion(),
                source.toString(),
                state,
                diagnostic,
                descriptor.dependencies(),
                descriptor.requestedCapabilities(),
                details
        );
    }

    private static PluginInfo systemInfo(String id, String name, PluginState state, String diagnostic) {
        return new PluginInfo(
                id,
                name,
                "system",
                PluginApi.VERSION,
                "",
                state,
                diagnostic,
                List.of(),
                List.of(),
                Map.of()
        );
    }

    private static String stripExtension(String name) {
        int separator = name.lastIndexOf('.');
        return separator < 0 ? name : name.substring(0, separator);
    }

    private static String safeId(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "-");
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private record Candidate(PluginDescriptor descriptor, Path source) {
    }

    private record LoadedPlugin(
            PluginDescriptor descriptor,
            Path source,
            PluginClassLoader classLoader,
            OpenToolsPlugin instance,
            PluginRegistrations registrations
    ) {
    }

    private final class PluginRegistrations implements PluginContext {
        private final String pluginId;
        private final Path dataDirectory;
        private final Path logFile;
        private final Set<String> capabilities;
        private final List<OpenTool> tools = new ArrayList<>();
        private final List<PageContribution> pages = new ArrayList<>();
        private boolean rolledBack;

        private PluginRegistrations(String pluginId, Path dataDirectory, List<String> capabilities) {
            this.pluginId = pluginId;
            this.dataDirectory = dataDirectory;
            this.logFile = dataDirectory.resolve("plugin.log");
            this.capabilities = Set.copyOf(capabilities);
        }

        @Override
        public String pluginId() {
            return pluginId;
        }

        @Override
        public Path dataDirectory() {
            requireCapability("data");
            return dataDirectory;
        }

        @Override
        public void log(String message) {
            try {
                Files.writeString(
                        logFile,
                        "[" + Instant.now() + "] " + message + System.lineSeparator(),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND
                );
            } catch (IOException ignored) {
                // Plugin diagnostics must not take down the host.
            }
        }

        @Override
        public void registerTool(OpenTool tool) {
            if (rolledBack) {
                throw new IllegalStateException("Plugin context is closed");
            }
            requireCapability("tool");
            toolRegistry.register(tool);
            tools.add(tool);
        }

        @Override
        public void registerPage(String id, String title, Supplier<JPanel> panelFactory) {
            if (rolledBack) {
                throw new IllegalStateException("Plugin context is closed");
            }
            requireCapability("page");
            pages.add(new PageContribution(pluginId + "." + id, title, panelFactory));
        }

        private void requireCapability(String capability) {
            if (!capabilities.contains(capability)) {
                throw new SecurityException(
                        "Plugin " + pluginId + " did not request capability: " + capability
                );
            }
        }

        private void rollback() {
            rolledBack = true;
            tools.forEach(tool -> toolRegistry.unregister(tool.descriptor().id()));
            tools.clear();
            pages.clear();
        }
    }
}
