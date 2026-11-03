package io.opentools.mcp;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Explicit opt-in directory watcher for workflow runs.
 *
 * <p>The service blocks on a {@link WatchService}, matches newly created or
 * modified files, waits for a stable size, and invokes the handler once per
 * file. It is never started automatically by the GUI or MCP server.</p>
 */
public final class WorkflowWatchService {
    @FunctionalInterface
    public interface FileHandler {
        void handle(Path file) throws Exception;
    }

    private final Path directory;
    private final PathMatcher matcher;
    private final Duration debounce;
    private final int maxRuns;

    public WorkflowWatchService(Path directory, String glob, Duration debounce, int maxRuns) {
        this.directory = directory.toAbsolutePath().normalize();
        this.matcher = FileSystems.getDefault().getPathMatcher("glob:" + glob);
        this.debounce = debounce == null || debounce.isNegative() ? Duration.ofSeconds(1) : debounce;
        this.maxRuns = Math.max(0, maxRuns);
    }

    public int watch(FileHandler handler) throws Exception {
        if (!Files.isDirectory(directory)) {
            throw new AutomationException(
                    "WATCH_DIRECTORY_MISSING",
                    "Watch directory does not exist: " + directory,
                    "Create the directory or choose another path."
            );
        }
        int runs = 0;
        Map<Path, Long> recent = new HashMap<>();
        try (WatchService service = FileSystems.getDefault().newWatchService()) {
            directory.register(service, StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY);
            while (maxRuns == 0 || runs < maxRuns) {
                WatchKey key = service.take();
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }
                    Path file = directory.resolve(event.context().toString()).toAbsolutePath().normalize();
                    if (!matcher.matches(file.getFileName()) || !Files.isRegularFile(file)) {
                        continue;
                    }
                    long now = System.nanoTime();
                    Long previous = recent.get(file);
                    long guard = debounce.toNanos() * 2;
                    if (previous != null && now - previous < guard) {
                        continue;
                    }
                    if (!stable(file)) {
                        continue;
                    }
                    recent.put(file, now);
                    handler.handle(file);
                    runs++;
                    if (maxRuns > 0 && runs >= maxRuns) {
                        key.reset();
                        return runs;
                    }
                }
                key.reset();
            }
        }
        return runs;
    }

    private boolean stable(Path file) throws InterruptedException {
        try {
            long size = Files.size(file);
            Thread.sleep(Math.min(500, debounce.toMillis()));
            return Files.isRegularFile(file) && Files.size(file) == size;
        } catch (IOException ex) {
            return false;
        }
    }
}
