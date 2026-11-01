package io.opentools.external;

import io.opentools.core.runtime.AppPaths;

import java.nio.file.Path;

public final class ExternalRuntime {
    private ExternalRuntime() {
    }

    public static Path appHome() {
        return AppPaths.applicationHome();
    }
}
