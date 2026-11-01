package io.opentools.core.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AppVersionTest {
    @Test
    void resolvesVersionFromMavenFilteredResource() {
        String version = AppVersion.current();

        assertFalse(version.isBlank());
        assertFalse(version.contains("${"));
        assertTrue(version.matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+].*)?"), version);
    }
}
