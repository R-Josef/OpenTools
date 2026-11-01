package io.opentools.external.process;

import java.util.ArrayList;
import java.util.List;

public final class ExternalArguments {
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
}
