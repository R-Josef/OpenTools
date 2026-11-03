package io.opentools.cli;

import io.opentools.api.tool.ValidationIssue;
import io.opentools.core.i18n.ErrorText;
import io.opentools.core.i18n.LanguageManager;
import io.opentools.core.i18n.ValidationText;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ErrorTextTest {
    @Test
    void localizesKnownCodesAndPreservesUnknownDetail() throws Exception {
        Path root = locateProjectRoot();
        LanguageManager chinese = new LanguageManager(root, "zh_CN");
        String localized = ErrorText.localize(chinese, "JOB_INTERRUPTED", "run.log detail");
        assertTrue(localized.contains("应用"), localized);
        assertTrue(localized.contains("run.log detail"), localized);

        LanguageManager english = new LanguageManager(root, "en_US");
        assertTrue(ErrorText.localize(english, "JOB_INTERRUPTED", "")
                .contains("application"));
        assertEquals("custom detail", ErrorText.localize(english, "UNKNOWN_CODE", "custom detail"));
    }

    @Test
    void localizesCentralValidationMessages() throws Exception {
        LanguageManager chinese = new LanguageManager(locateProjectRoot(), "zh_CN");
        ValidationIssue issue = ValidationText.localize(
                chinese,
                java.util.Map.of("input", "输入文件"),
                ValidationIssue.error("input", "Input file is required.", "Choose or enter a value.")
        );
        assertTrue(issue.message().contains("输入文件"), issue.message());
        assertTrue(issue.message().contains("必填"), issue.message());
        assertTrue(issue.suggestion().contains("请选择"), issue.suggestion());
    }

    private static Path locateProjectRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        if (Files.isDirectory(current.resolve("lang"))) {
            return current;
        }
        if (Files.isDirectory(current.getParent().resolve("lang"))) {
            return current.getParent();
        }
        throw new IllegalStateException("Could not locate lang/");
    }
}
