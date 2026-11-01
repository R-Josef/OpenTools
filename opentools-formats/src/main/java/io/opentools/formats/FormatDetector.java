package io.opentools.formats;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class FormatDetector {
    public FormatDetection detect(Path path) throws IOException {
        long size = Files.size(path);
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Compression.open(path),
                StandardCharsets.UTF_8
        ))) {
            String line;
            while (lines.size() < 200 && (line = reader.readLine()) != null) {
                if (lines.isEmpty() && !line.isEmpty() && line.charAt(0) == '\ufeff') {
                    line = line.substring(1);
                }
                lines.add(line);
            }
        }

        String first = firstNonBlank(lines);
        if (first == null) {
            return new FormatDetection(FormatType.UNKNOWN, "File is empty", size);
        }
        if (first.startsWith(">")) {
            return new FormatDetection(FormatType.FASTA, "First record starts with >", size);
        }
        if (first.startsWith("##gff-version")) {
            return new FormatDetection(FormatType.GFF3, "GFF3 version directive found", size);
        }
        if (first.startsWith("##fileformat=VCF")) {
            return new FormatDetection(FormatType.VCF, "VCF format directive found", size);
        }
        if (looksLikeFastq(lines)) {
            return new FormatDetection(FormatType.FASTQ, "FASTQ header and separator detected", size);
        }

        String extension = extension(path);
        if (extension.equals("csv")) {
            return new FormatDetection(FormatType.CSV, "CSV extension", size);
        }
        if (extension.equals("tsv") || extension.equals("tab")) {
            return new FormatDetection(FormatType.TSV, "TSV extension", size);
        }
        if (extension.equals("nwk") || extension.equals("newick") || extension.equals("tree")) {
            return new FormatDetection(FormatType.NEWICK, "Newick extension", size);
        }
        if (looksLikeGffOrGtf(first)) {
            String attributes = ninthColumn(first).toLowerCase(Locale.ROOT);
            FormatType type = attributes.contains("gene_id") || attributes.contains("transcript_id")
                    ? FormatType.GTF
                    : FormatType.GFF3;
            return new FormatDetection(type, "Nine-column annotation row detected", size);
        }
        if (looksLikeBed(first)) {
            return new FormatDetection(FormatType.BED, "Chromosome, start, and end columns detected", size);
        }
        if (first.stripLeading().startsWith("(") && first.stripTrailing().endsWith(";")) {
            return new FormatDetection(FormatType.NEWICK, "Newick tree shape detected", size);
        }
        if (first.contains("\t")) {
            return new FormatDetection(FormatType.TSV, "Tab-delimited content detected", size);
        }
        return new FormatDetection(FormatType.UNKNOWN, "No supported format signature matched", size);
    }

    private static boolean looksLikeFastq(List<String> lines) {
        int header = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).isBlank()) {
                header = i;
                break;
            }
        }
        return header >= 0
                && header + 2 < lines.size()
                && lines.get(header).startsWith("@")
                && lines.get(header + 2).startsWith("+");
    }

    private static boolean looksLikeGffOrGtf(String first) {
        String[] columns = first.split("\t", -1);
        if (columns.length < 9) {
            return false;
        }
        try {
            Long.parseLong(columns[3]);
            Long.parseLong(columns[4]);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static boolean looksLikeBed(String first) {
        String[] columns = first.split("\t", -1);
        if (columns.length < 3) {
            return false;
        }
        try {
            return Long.parseLong(columns[1]) >= 0 && Long.parseLong(columns[2]) >= 0;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static String ninthColumn(String line) {
        String[] columns = line.split("\t", -1);
        return columns.length >= 9 ? columns[8] : "";
    }

    private static String firstNonBlank(List<String> lines) {
        return lines.stream()
                .filter(line -> !line.isBlank())
                .filter(line -> !line.startsWith("#"))
                .findFirst()
                .orElse(null);
    }

    private static String extension(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".gz")) {
            name = name.substring(0, name.length() - 3);
        }
        int separator = name.lastIndexOf('.');
        return separator < 0 ? "" : name.substring(separator + 1);
    }
}
