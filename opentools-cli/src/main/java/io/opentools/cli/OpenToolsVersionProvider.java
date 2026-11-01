package io.opentools.cli;

import io.opentools.core.runtime.AppVersion;
import picocli.CommandLine.IVersionProvider;

public final class OpenToolsVersionProvider implements IVersionProvider {
    @Override
    public String[] getVersion() {
        return new String[]{"OpenTools " + AppVersion.current()};
    }
}
