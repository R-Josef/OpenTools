package io.opentools.analysis.expression;

import io.opentools.api.tool.OpenToolException;
import io.opentools.formats.Compression;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ExpressionMatrixParser {
    public ExpressionMatrix parse(
            Path input,
            String delimiter,
            String missingPolicy,
            boolean log2Transform
    ) throws Exception {
        List<List<String>> rows = new ArrayList<>();
        char separator = detectDelimiter(input, delimiter);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Compression.open(input), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                rows.add(split(line, separator));
            }
        }
        if (rows.size() < 2) {
            throw error("Expression matrix must contain a header and at least one row.");
        }
        List<String> columns = rows.getFirst().subList(1, rows.getFirst().size());
        if (columns.isEmpty()) {
            throw error("Expression matrix has no sample columns.");
        }
        List<String> rowNames = new ArrayList<>();
        List<List<Double>> values = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
            List<String> fields = rows.get(rowIndex);
            if (fields.size() != columns.size() + 1) {
                throw error("Matrix row " + (rowIndex + 1) + " has " + fields.size()
                        + " fields; expected " + (columns.size() + 1) + ".");
            }
            String name = fields.getFirst().trim();
            if (name.isEmpty() || !seen.add(name)) {
                throw error("Matrix row name is empty or duplicated: " + name);
            }
            List<Double> parsed = new ArrayList<>();
            for (int column = 1; column < fields.size(); column++) {
                String text = fields.get(column).trim();
                if (text.isEmpty() || text.equals("NA") || text.equals("NaN")) {
                    if (!missingPolicy.equals("ROW_MEAN") && !missingPolicy.equals("ZERO")) {
                        throw error("Missing value at row " + name + ", column " + columns.get(column - 1) + ".");
                    }
                    parsed.add(Double.NaN);
                    continue;
                }
                try {
                    double raw = Double.parseDouble(text);
                    if (log2Transform) {
                        raw = Math.log(raw + 1) / Math.log(2);
                    }
                    parsed.add(raw);
                } catch (NumberFormatException ex) {
                    throw error("Invalid numeric value at row " + name + ", column " + columns.get(column - 1) + ".");
                }
            }
            if (missingPolicy.equals("ROW_MEAN")) {
                double mean = parsed.stream().filter(Double::isFinite).mapToDouble(Double::doubleValue).average().orElse(0);
                for (int column = 0; column < parsed.size(); column++) {
                    if (!Double.isFinite(parsed.get(column))) {
                        parsed.set(column, mean);
                    }
                }
            } else if (missingPolicy.equals("ZERO")) {
                for (int column = 0; column < parsed.size(); column++) {
                    if (!Double.isFinite(parsed.get(column))) {
                        parsed.set(column, 0.0);
                    }
                }
            }
            rowNames.add(name);
            values.add(List.copyOf(parsed));
        }
        return new ExpressionMatrix(rowNames, columns, values);
    }

    private static char detectDelimiter(Path input, String configured) throws Exception {
        if (!configured.equals("AUTO")) {
            return switch (configured) {
                case "COMMA" -> ',';
                case "SEMICOLON" -> ';';
                default -> '\t';
            };
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Compression.open(input), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                int tabs = count(line, '\t');
                int commas = count(line, ',');
                int semicolons = count(line, ';');
                if (tabs >= commas && tabs >= semicolons) {
                    return '\t';
                }
                return commas >= semicolons ? ',' : ';';
            }
        }
        return '\t';
    }

    private static int count(String value, char character) {
        int count = 0;
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) == character) {
                count++;
            }
        }
        return count;
    }

    private static List<String> split(String line, char delimiter) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int index = 0; index < line.length(); index++) {
            char value = line.charAt(index);
            if (value == delimiter) {
                result.add(current.toString());
                current.setLength(0);
            } else {
                current.append(value);
            }
        }
        result.add(current.toString());
        return result;
    }

    private static OpenToolException error(String message) {
        return new OpenToolException(
                "EXPRESSION_MATRIX_ERROR",
                message,
                "Use a delimited numeric matrix with one row-name column and a header of sample names."
        );
    }
}
