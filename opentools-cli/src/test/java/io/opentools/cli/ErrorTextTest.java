package io.opentools.cli;

import io.opentools.core.i18n.ErrorText;
import io.opentools.core.i18n.LanguageManager;
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
