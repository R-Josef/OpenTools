package io.opentools.formats.newick;

import io.opentools.api.tool.OpenToolException;

public final class NewickParser {
    private String text;
    private int position;

    public NewickNode parse(String input) {
        text = input == null ? "" : input.trim();
        position = 0;
        if (text.endsWith(";")) {
            text = text.substring(0, text.length() - 1);
        }
        NewickNode root = node();
        whitespace();
        if (position != text.length()) {
            throw error("Unexpected trailing Newick content.");
        }
        return root;
    }

    public String write(NewickNode root) {
        StringBuilder output = new StringBuilder();
        write(root, output);
        return output.append(';').toString();
    }

    private NewickNode node() {
        whitespace();
        NewickNode result = new NewickNode("", null, null);
        if (peek('(')) {
            position++;
            while (true) {
                result.addChild(node());
                whitespace();
                if (peek(',')) {
                    position++;
                    continue;
                }
                expect(')');
                break;
            }
        }
        result = new NewickNode(label(), null, result.children());
        whitespace();
        if (peek(':')) {
            position++;
            whitespace();
            result = new NewickNode(result.name(), length(), result.children());
        }
        return result;
    }

    private String label() {
        whitespace();
        if (peek('\'')) {
            position++;
            StringBuilder value = new StringBuilder();
            while (position < text.length()) {
                char current = text.charAt(position++);
                if (current == '\'') {
                    if (position < text.length() && text.charAt(position) == '\'') {
                        value.append('\'');
                        position++;
                    } else {
                        break;
                    }
                } else {
                    value.append(current);
                }
            }
            return value.toString();
        }
        int start = position;
        while (position < text.length()
                && ",():;".indexOf(text.charAt(position)) < 0
                && !Character.isWhitespace(text.charAt(position))) {
            position++;
        }
        return text.substring(start, position);
    }

    private double length() {
        int start = position;
        while (position < text.length()) {
            char value = text.charAt(position);
            if (value == ',' || value == ')' || Character.isWhitespace(value)) {
                break;
            }
            position++;
        }
        try {
            return Double.parseDouble(text.substring(start, position));
        } catch (NumberFormatException ex) {
            throw error("Invalid branch length near position " + start + ".");
        }
    }

    private void write(NewickNode node, StringBuilder output) {
        if (!node.children().isEmpty()) {
            output.append('(');
            for (int index = 0; index < node.children().size(); index++) {
                if (index > 0) {
                    output.append(',');
                }
                write(node.children().get(index), output);
            }
            output.append(')');
        }
        if (!node.name().isBlank()) {
            if (node.name().matches("[A-Za-z0-9_.-]+")) {
                output.append(node.name());
            } else {
                output.append('\'').append(node.name().replace("'", "''")).append('\'');
            }
        }
        if (node.length() != null) {
            output.append(':').append(node.length());
        }
    }

    private void whitespace() {
        while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
            position++;
        }
    }

    private boolean peek(char value) {
        return position < text.length() && text.charAt(position) == value;
    }

    private void expect(char value) {
        whitespace();
        if (!peek(value)) {
            throw error("Expected '" + value + "'.");
        }
        position++;
    }

    private OpenToolException error(String message) {
        return new OpenToolException("NEWICK_FORMAT_ERROR",
                message + " Near position " + position + ".",
                "Provide a valid Newick tree ending with ';'.");
    }
}
