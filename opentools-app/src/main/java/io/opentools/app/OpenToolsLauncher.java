package io.opentools.app;

import io.opentools.cli.OpenToolsCli;
import io.opentools.ui.OpenToolsGui;

import java.util.Arrays;

public final class OpenToolsLauncher {
    private OpenToolsLauncher() {
    }

    public static void main(String[] args) {
        if (args.length == 0 || "gui".equalsIgnoreCase(args[0])) {
            String[] uiArgs = args.length == 0 ? new String[0] : Arrays.copyOfRange(args, 1, args.length);
            if (uiArgs.length > 0) {
                System.err.println("GUI arguments are not supported yet: " + Arrays.toString(uiArgs));
            }
            OpenToolsGui.launchDefault();
            return;
        }
        int exitCode = OpenToolsCli.execute(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }
}
