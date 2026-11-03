package io.opentools.core.i18n;

import java.util.Locale;

/**
 * Resolves a stable error code to a localized message, preserving the original
 * detail when the code is unknown.
 */
public final class ErrorText {
    private static final String MISSING = "\u0000";

    private ErrorText() {
    }

    public static String localize(LanguageManager language, String code, String detail) {
        String safeDetail = detail == null ? "" : detail;
        if (language == null || code == null || code.isBlank()) {
            return safeDetail;
        }
        String key = "error." + code.toLowerCase(Locale.ROOT);
        String localized = language.text(key, MISSING);
        if (MISSING.equals(localized)) {
            return safeDetail;
        }
        return safeDetail.isBlank() ? localized : localized + " " + safeDetail;
    }
}
