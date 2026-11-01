package io.opentools.app;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PublicExamplesTest {
    private static final Set<String> PUBLIC_DIRECTORIES = Set.of(
            "alignment",
            "annotation",
            "batch",
            "comparative-genomics",
            "enrichment",
            "expression",
            "external-tools",
            "hts",
            "sequence",
            "tables",
            "visualization"
    );

    @Test
    void usesDomainOrientedExampleNames() throws Exception {
        Path examples = findProjectRoot().resolve("examples");
        Set<String> directories;
        try (var paths = Files.list(examples)) {
            directories = paths.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toSet());
        }

        assertTrue(Files.isRegularFile(examples.resolve("README.md")));
        assertTrue(directories.containsAll(PUBLIC_DIRECTORIES));
        directories.forEach(name -> assertFalse(
                name.matches("(?i)^(f[1-9]|p[4-9])$"),
                "Internal development directory leaked into public examples: " + name
        ));
    }

    @Test
    void containsNoLocalEnvironmentOrDevelopmentStageText() throws Exception {
        Path examples = findProjectRoot().resolve("examples");
        try (var files = Files.walk(examples)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String text = Files.readString(file);
                assertFalse(text.contains("C:\\Users\\"), "Local user path in " + file);
                assertFalse(text.contains("C:\\Program Files\\OpenToolsDeps"), "Local tool path in " + file);
                assertFalse(text.contains(".verification"), "Local verification path in " + file);
                if (file.getFileName().toString().equals("README.md")) {
                    assertFalse(text.matches("(?s).*\\bF[1-9]\\b.*"), "Stage label in " + file);
                    assertFalse(text.matches("(?s).*\\bP[4-9]\\b.*"), "Stage label in " + file);
                }
            }
        }
    }

    @Test
    void publicTextContainsNoUnsharedToolDirectory() throws Exception {
        Path root = findProjectRoot();
        List<Path> publicFiles = new ArrayList<>(List.of(
                root.resolve("README.md"),
                root.resolve("README.zh-CN.md")
        ));
        for (String directory : List.of("docs", "examples", "packaging")) {
            try (var files = Files.walk(root.resolve(directory))) {
                files.filter(Files::isRegularFile)
                        .filter(PublicExamplesTest::isPublicTextFile)
                        .forEach(publicFiles::add);
            }
        }

        String localToolDirectory = "OpenTools" + "Deps";
        for (Path file : publicFiles) {
            assertFalse(
                    Files.readString(file).contains(localToolDirectory),
                    "Unshared tool directory in " + file
            );
        }
    }

    private static boolean isPublicTextFile(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        return name.endsWith(".md")
                || name.endsWith(".txt")
                || name.endsWith(".json")
                || name.endsWith(".yaml")
                || name.endsWith(".yml");
    }

    private static Path findProjectRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("packaging/windows/tools.json"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not find project root");
    }
}
