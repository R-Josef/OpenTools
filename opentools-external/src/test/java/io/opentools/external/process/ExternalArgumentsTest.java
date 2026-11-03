package io.opentools.external.process;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ExternalArgumentsTest {
    @Test
    void parsesQuotedArguments() {
        assertEquals(
                List.of("-word_size", "11", "--masking", "none", "value with spaces"),
                ExternalArguments.parse("-word_size 11 --masking none \"value with spaces\"")
        );
    }

    @Test
    void rejectsUnclosedQuote() {
        assertThrows(IllegalArgumentException.class, () -> ExternalArguments.parse("--x \"broken"));
    }

    @Test
    void allowsAlgorithmTuningArguments() {
        assertEquals(
                List.of("-dust", "no", "-max_hsps", "5"),
                ExternalArguments.parseRestricted("-dust no -max_hsps 5", ExternalArguments.RESERVED_OPTIONS)
        );
    }

    @Test
    void rejectsOutputOverride() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ExternalArguments.parseRestricted("-out elsewhere.tsv", ExternalArguments.RESERVED_OPTIONS));
        assertEquals(true, error.getMessage().contains("-out"));
    }

    @Test
    void rejectsRemoteMode() {
        assertThrows(IllegalArgumentException.class,
                () -> ExternalArguments.parseRestricted("-remote", ExternalArguments.RESERVED_OPTIONS));
    }

    @Test
    void rejectsEqualsFormAndShortOutputOption() {
        assertThrows(IllegalArgumentException.class,
                () -> ExternalArguments.parseRestricted("--out=elsewhere.tsv", ExternalArguments.RESERVED_OPTIONS));
        assertThrows(IllegalArgumentException.class,
                () -> ExternalArguments.parseRestricted("-o elsewhere.tsv", ExternalArguments.RESERVED_OPTIONS));
    }

    @Test
    void rejectsResponseFilesAndOptionTerminator() {
        assertThrows(IllegalArgumentException.class,
                () -> ExternalArguments.parseRestricted("@args.txt", ExternalArguments.RESERVED_OPTIONS));
        assertThrows(IllegalArgumentException.class,
                () -> ExternalArguments.parseRestricted("-- -out x", ExternalArguments.RESERVED_OPTIONS));
    }

    @Test
    void negativeNumbersAreValues() {
        assertEquals(
                List.of("-gapopen", "-1"),
                ExternalArguments.parseRestricted("-gapopen -1", ExternalArguments.RESERVED_OPTIONS)
        );
    }

    @Test
    void customReservedSetIsHonored() {
        assertEquals(
                List.of("-max", "10"),
                ExternalArguments.parseRestricted("-max 10", Set.of("out"))
        );
        assertThrows(IllegalArgumentException.class,
                () -> ExternalArguments.parseRestricted("-max 10", Set.of("max")));
    }
}
