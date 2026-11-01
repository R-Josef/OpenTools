package io.opentools.core.i18n;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class LanguageManagerTest {
    @TempDir
    Path tempDir;

    @Test
    void loadsExternalLanguageAndFallsBackToEnglish() throws Exception {
        Path app = Files.createDirectories(tempDir.resolve("app"));
        Path lang = Files.createDirectories(app.resolve("lang"));
        Files.writeString(lang.resolve("en_US.properties"), "language.name=English\nhello=Hello\nonly.english=Fallback\n");
        Files.writeString(lang.resolve("zh_CN.properties"), "language.name=简体中文\nhello=你好\n");
        Path user = Files.createDirectories(tempDir.resolve("user"));

        LanguageManager manager = new LanguageManager(app, user, "zh_CN");

        assertEquals("你好", manager.text("hello"));
        assertEquals("Fallback", manager.text("only.english"));
        assertEquals("zh_CN", manager.activeCode());
    }

    @Test
    void persistsManualSelection() throws Exception {
        Path app = Files.createDirectories(tempDir.resolve("app"));
        Path lang = Files.createDirectories(app.resolve("lang"));
        Files.writeString(lang.resolve("en_US.properties"), "language.name=English\nhello=Hello\n");
        Files.writeString(lang.resolve("zh_CN.properties"), "language.name=简体中文\nhello=你好\n");
        Path user = Files.createDirectories(tempDir.resolve("user"));

        LanguageManager first = new LanguageManager(app, user, null);
        first.setLanguage("en_US");
        LanguageManager second = new LanguageManager(app, user, null);

        assertEquals("en_US", second.activeCode());
        assertEquals("Hello", second.text("hello"));
    }

    @Test
    void mapsSystemChineseLocalesToSimplifiedChinese() {
        assertEquals("zh_CN", LanguageManager.languageFor(Locale.SIMPLIFIED_CHINESE));
        assertEquals("en_US", LanguageManager.languageFor(Locale.US));
    }
}
