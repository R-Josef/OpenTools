package io.opentools.core.util;

/**
 * Minimal internal logging facade backed by {@link System.Logger}.
 *
 * <p>OpenTools never collects telemetry; messages go to the local console or
 * the platform logging handler and are only meant for diagnostics.</p>
 */
public final class Log {
    private static final System.Logger LOGGER = System.getLogger("io.opentools");

    private Log() {
    }

    /**
     * Records a warning.
     *
     * @param message diagnostic text
     * @param error optional failure
     */
    public static void warn(String message, Throwable error) {
        if (error == null) {
            LOGGER.log(System.Logger.Level.WARNING, message);
        } else {
            LOGGER.log(System.Logger.Level.WARNING, message, error);
        }
    }

    /**
     * Records an informational message.
     *
     * @param message diagnostic text
     */
    public static void info(String message) {
        LOGGER.log(System.Logger.Level.INFO, message);
    }
}
