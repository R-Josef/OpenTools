package io.opentools.external.process;

import java.time.Duration;

public record ProcessResult(int exitCode, String output, Duration elapsed, boolean truncated) {
    public boolean succeeded() {
        return exitCode == 0;
    }
}
