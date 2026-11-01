package io.opentools.api.tool;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

/**
 * Run-scoped services available while a task executes.
 */
public final class TaskContext {
    private final String runId;
    private final Path projectRoot;
    private final Path runDirectory;
    private final Path outputDirectory;
    private final Consumer<String> logger;
    private final DoubleConsumer progressListener;
    private final CancellationToken cancellationToken;

    /**
     * Creates a task context.
     *
     * @param runId unique run identifier
     * @param projectRoot project root
     * @param runDirectory run working directory
     * @param outputDirectory preferred directory for result files
     * @param logger diagnostic logger
     * @param progressListener progress callback
     * @param cancellationToken cooperative cancellation signal
     */
    public TaskContext(
            String runId,
            Path projectRoot,
            Path runDirectory,
            Path outputDirectory,
            Consumer<String> logger,
            DoubleConsumer progressListener,
            CancellationToken cancellationToken
    ) {
        this.runId = Objects.requireNonNull(runId, "runId");
        this.projectRoot = Objects.requireNonNull(projectRoot, "projectRoot");
        this.runDirectory = Objects.requireNonNull(runDirectory, "runDirectory");
        this.outputDirectory = Objects.requireNonNull(outputDirectory, "outputDirectory");
        this.logger = logger == null ? ignored -> { } : logger;
        this.progressListener = progressListener == null ? ignored -> { } : progressListener;
        this.cancellationToken = cancellationToken == null ? new CancellationToken() : cancellationToken;
    }

    /**
     * Returns the unique run identifier.
     *
     * @return run identifier
     */
    public String runId() {
        return runId;
    }

    /**
     * Returns the project root.
     *
     * @return project root
     */
    public Path projectRoot() {
        return projectRoot;
    }

    /**
     * Returns the run working directory.
     *
     * @return run directory
     */
    public Path runDirectory() {
        return runDirectory;
    }

    /**
     * Returns the preferred output directory.
     *
     * @return output directory
     */
    public Path outputDirectory() {
        return outputDirectory;
    }

    /**
     * Returns the run cancellation signal.
     *
     * @return cancellation token
     */
    public CancellationToken cancellationToken() {
        return cancellationToken;
    }

    /**
     * Writes a diagnostic message to the run log.
     *
     * @param message diagnostic text
     */
    public void log(String message) {
        logger.accept(message);
    }

    /**
     * Reports progress and an optional message.
     *
     * @param fraction progress from {@code 0.0} through {@code 1.0}
     * @param message optional progress text
     */
    public void progress(double fraction, String message) {
        progressListener.accept(Math.max(0.0, Math.min(1.0, fraction)));
        if (message != null && !message.isBlank()) {
            log(message);
        }
    }

    /**
     * Resolves a file name in the output directory.
     *
     * @param fileName output file name
     * @return output path
     */
    public Path defaultOutputPath(String fileName) {
        return outputDirectory.resolve(fileName);
    }
}
