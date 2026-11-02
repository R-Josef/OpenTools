package io.opentools.api.tool;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thread-safe cooperative cancellation signal passed to a running tool.
 */
public final class CancellationToken {
    private final AtomicBoolean cancelled = new AtomicBoolean();

    /**
     * Returns whether cancellation was requested.
     *
     * @return cancellation state
     */
    public boolean isCancelled() {
        return cancelled.get();
    }

    /**
     * Requests cooperative tool cancellation.
     */
    public void cancel() {
        cancelled.set(true);
    }

    /**
     * Throws when cancellation was requested.
     *
     * @throws java.util.concurrent.CancellationException if cancelled
     */
    public void throwIfCancelled() {
        if (isCancelled()) {
            throw new java.util.concurrent.CancellationException("Tool cancelled");
        }
    }
}
