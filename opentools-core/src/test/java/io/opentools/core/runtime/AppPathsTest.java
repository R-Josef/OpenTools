package io.opentools.core.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class AppPathsTest {
    @TempDir
    Path tempDir;

    @Test
    void explicitHomeWins() {
        String previous = System.getProperty("opentools.home");
        try {
            System.setProperty("opentools.home", tempDir.toString());
            assertEquals(tempDir.toAbsolutePath().normalize(), AppPaths.applicationHome());
        } finally {
            if (previous == null) {
                System.clearProperty("opentools.home");
            } else {
                System.setProperty("opentools.home", previous);
            }
        }
    }

    @Test
    void jpackageAppDirectoryResolvesToPackageRoot() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("package"));
        Path app = Files.createDirectories(root.resolve("app"));
        Files.createDirectories(root.resolve("runtime"));
        Path jar = Files.createFile(app.resolve("opentools.jar"));
        String previousHome = System.getProperty("opentools.home");
        String previousClassPath = System.getProperty("java.class.path");
        try {
            System.clearProperty("opentools.home");
            System.setProperty("java.class.path", jar.toString());
            assertEquals(root.toAbsolutePath().normalize(), AppPaths.applicationHome());
        } finally {
            if (previousHome == null) {
                System.clearProperty("opentools.home");
            } else {
                System.setProperty("opentools.home", previousHome);
            }
            if (previousClassPath == null) {
                System.clearProperty("java.class.path");
            } else {
                System.setProperty("java.class.path", previousClassPath);
            }
        }
    }

    @Test
    void distributionJarResolvesToItsDistDirectory() throws Exception {
        Path dist = Files.createDirectories(tempDir.resolve("opentools-app/target/dist"));
        Files.createDirectories(dist.resolve("lang"));
        Path jar = Files.createFile(dist.resolve("opentools.jar"));
        String previousHome = System.getProperty("opentools.home");
        String previousClassPath = System.getProperty("java.class.path");
        try {
            System.clearProperty("opentools.home");
            System.setProperty("java.class.path", jar.toString());
            assertEquals(dist.toAbsolutePath().normalize(), AppPaths.applicationHome());
        } finally {
            if (previousHome == null) {
                System.clearProperty("opentools.home");
            } else {
                System.setProperty("opentools.home", previousHome);
            }
            if (previousClassPath == null) {
                System.clearProperty("java.class.path");
            } else {
                System.setProperty("java.class.path", previousClassPath);
            }
        }
    }
}
