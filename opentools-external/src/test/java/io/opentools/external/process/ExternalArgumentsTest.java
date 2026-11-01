package io.opentools.external.process;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ExternalArgumentsTest {
    @Test
    void parsesQuotedArguments() {
        assertEquals(
                java.util.List.of("-word_size", "11", "--masking", "none", "value with spaces"),
                ExternalArguments.parse("-word_size 11 --masking none \"value with spaces\"")
        );
    }

    @Test
    void rejectsUnclosedQuote() {
        assertThrows(IllegalArgumentException.class, () -> ExternalArguments.parse("--x \"broken"));
    }
}
