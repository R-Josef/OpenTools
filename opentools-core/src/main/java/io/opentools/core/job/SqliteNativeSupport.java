package io.opentools.core.job;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.Locale;

/**
 * Works around sqlite-jdbc's classpath resource lookup failing when the
 * application JAR is installed below a non-ASCII Windows path. The Windows
 * package places sqlitejdbc.dll next to opentools.jar.
 */
public final class SqliteNativeSupport {
    private static final String LIBRARY_NAME = "sqlitejdbc.dll";
    private static boolean prepared;

    private SqliteNativeSupport() {
    }

    public static synchronized void prepare() {
        if (prepared || System.getProperty("org.sqlite.lib.path") != null
                || !System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows")) {
            prepared = true;
            return;
        }
        Path directory = applicationDirectory();
        if (directory == null || isAscii(directory.toString())) {
            prepared = true;
            return;
        }
        if (Files.isRegularFile(directory.resolve(LIBRARY_NAME))) {
            useDirectory(directory);
            return;
        }
        String libraryPath = System.getProperty("java.library.path", "");
        for (String entry : libraryPath.split(File.pathSeparator)) {
            if (entry.isBlank()) {
                continue;
            }
            Path candidate = Path.of(entry);
            if (Files.isRegularFile(candidate.resolve(LIBRARY_NAME))) {
                useDirectory(candidate);
                return;
            }
        }
        prepared = true;
    }

    private static void useDirectory(Path directory) {
        System.setProperty("org.sqlite.lib.path", directory.toString());
        System.setProperty("org.sqlite.lib.name", LIBRARY_NAME);
        prepared = true;
    }

    private static Path applicationDirectory() {
        try {
            CodeSource source = SqliteNativeSupport.class.getProtectionDomain().getCodeSource();
            if (source == null) {
                return null;
            }
            URI location = source.getLocation().toURI();
            Path path = Path.of(location);
            return Files.isDirectory(path) ? path : path.getParent();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean isAscii(String value) {
        return value.codePoints().allMatch(codePoint -> codePoint < 128);
    }
}
