package io.opentools.external.process;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Parses user-supplied extra arguments for external tools.
 *
 * <p>Extra arguments may tune algorithms, but must never redirect inputs,
 * outputs, databases, or network mode. Those remain controlled by the task
 * parameters so that workspace policies and reproducibility guarantees hold.</p>
 */
public final class ExternalArguments {
    /**
     * Option names that extra arguments may not override.
     */
    public static final Set<String> RESERVED_OPTIONS = Set.of(
            "remote", "out", "outfmt", "db", "query", "subject",
            "in", "input", "output", "o", "q", "d", "s",
            "hmm", "hmmdb", "tblout", "domtblout", "htmlout", "phylipout",
            "aln", "treefile", "seqdb", "seqfile", "cpu"
    );

    private ExternalArguments() {
    }

    public static List<String> parse(String text) {
        List<String> arguments = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return arguments;
        }
        StringBuilder current = new StringBuilder();
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (escaped) {
                current.append(ch);
                escaped = false;
            } else if (ch == '\\') {
                escaped = true;
            } else if (quote != 0) {
                if (ch == quote) {
                    quote = 0;
                } else {
                    current.append(ch);
                }
            } else if (ch == '"' || ch == '\'') {
                quote = ch;
            } else if (Character.isWhitespace(ch)) {
                if (!current.isEmpty()) {
                    arguments.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(ch);
            }
        }
        if (escaped) {
            current.append('\\');
        }
        if (quote != 0) {
            throw new IllegalArgumentException("Unclosed quote in extra arguments");
        }
        if (!current.isEmpty()) {
            arguments.add(current.toString());
        }
        return List.copyOf(arguments);
    }

    /**
     * Parses extra arguments and rejects options that would bypass task inputs,
     * outputs, databases, or remote execution.
     *
     * @param text quoted extra argument text
     * @param reservedOptions option names that may not appear
     * @return validated argument tokens
     * @throws IllegalArgumentException when a reserved or unsafe token appears
     */
    public static List<String> parseRestricted(String text, Set<String> reservedOptions) {
        List<String> arguments = parse(text);
        Set<String> reserved = reservedOptions == null ? RESERVED_OPTIONS : reservedOptions;
        for (String argument : arguments) {
            if (argument.startsWith("@")) {
                throw new IllegalArgumentException(
                        "Response files are not allowed in extra arguments: " + argument);
            }
            if (argument.equals("--") || argument.equals("-")) {
                throw new IllegalArgumentException(
                        "Extra arguments may not end option parsing: " + argument);
            }
            if (argument.startsWith("-") && argument.length() > 1 && !isNumber(argument)) {
                String key = argument.replaceFirst("^-+", "");
                int equals = key.indexOf('=');
                if (equals >= 0) {
                    key = key.substring(0, equals);
                }
                key = key.toLowerCase(Locale.ROOT);
                if (reserved.contains(key)) {
                    throw new IllegalArgumentException(
                            "Extra argument is not allowed: " + argument
                                    + ". Inputs, outputs, databases, and remote mode come from task parameters.");
                }
            }
        }
        return arguments;
    }

    private static boolean isNumber(String value) {
        if (value.length() < 2) {
            return false;
        }
        for (int index = 1; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (!Character.isDigit(ch) && ch != '.' && ch != 'e' && ch != 'E' && ch != '-' && ch != '+') {
                return false;
            }
        }
        return Character.isDigit(value.charAt(1));
    }
}
