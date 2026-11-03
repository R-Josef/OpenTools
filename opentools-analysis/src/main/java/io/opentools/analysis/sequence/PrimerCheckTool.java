package io.opentools.analysis.sequence;

import io.opentools.api.tool.AbstractOpenTool;
import io.opentools.api.tool.OpenToolException;
import io.opentools.api.tool.OutputArtifact;
import io.opentools.api.tool.ResultTable;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolParameter;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ToolResult;
import io.opentools.api.tool.ValidationResult;
import io.opentools.formats.FormatToolSupport;
import io.opentools.formats.fasta.FastaReader;
import io.opentools.formats.fasta.FastaRecord;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Electronic PCR: locates primer pairs on a genome and reports predicted amplicons.
 */
public final class PrimerCheckTool extends AbstractOpenTool {
    private static final int MAX_MATCHES_PER_PRIMER = 200_000;
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "sequence.primer-check",
            "Primer Check (e-PCR)",
            "Locate primer pairs on a genome and report predicted amplicons.",
            "Sequence",
            List.of("primer check", "e-PCR", "in silico PCR", "引物检查", "电子PCR"),
            List.of("FASTA", "TSV"),
            List.of("TSV"),
            "1.0.0",
            List.of(
                    ToolParameter.requiredFile("genome", "Genome FASTA",
                            "Nucleotide genome or contigs; both strands are searched."),
                    ToolParameter.requiredFile("primers", "Primer table",
                            "TSV with name, forward, and reverse columns; IUPAC bases are accepted."),
                    ToolParameter.integer("maxMismatches", "Maximum mismatches",
                            "Allowed mismatches per primer; 0 requires exact matches.", 0, 0, 3),
                    ToolParameter.integer("minProductSize", "Minimum product size",
                            "Minimum amplicon length in bp.", 50, 1, 100000),
                    ToolParameter.integer("maxProductSize", "Maximum product size",
                            "Maximum amplicon length in bp.", 3000, 1, 1000000),
                    ToolParameter.integer("maxAmplicons", "Maximum amplicons",
                            "Stop after this many products and mark the result truncated.", 100000, 1, 1000000),
                    ToolParameter.optionalOutputFile("output", "Output TSV",
                            "Leave blank to save in the run directory."),
                    ToolParameter.bool("overwrite", "Overwrite output", "Allow replacing an existing output.", false)
            )
    );

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ValidationResult validate(ToolRequest request) {
        ValidationResult result = validateParameters(request)
                .merge(FormatToolSupport.validateOutput(request, request.path("genome")));
        if (request.integer("minProductSize") > request.integer("maxProductSize")) {
            result = result.merge(ValidationResult.of(io.opentools.api.tool.ValidationIssue.error(
                    "maxProductSize", "Maximum product size must be at least the minimum product size.",
                    "Increase maxProductSize or lower minProductSize.")));
        }
        return result;
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        List<Primer> primers = readPrimers(request.path("primers"));
        int maxMismatches = Math.max(0, Math.min(3, request.integer("maxMismatches")));
        int minProduct = Math.max(1, request.integer("minProductSize"));
        int maxProduct = Math.max(minProduct, request.integer("maxProductSize"));
        int maxAmplicons = Math.max(1, request.integer("maxAmplicons"));
        long[] stats = new long[2];
        boolean[] capped = new boolean[2]; // [0] amplicon cap, [1] match cap

        Path output = FormatToolSupport.outputPath(request, context, "primer-amplicons.tsv");
        FormatToolSupport.writeAtomically(output, writer -> {
            writer.write("primer\tsequence\tforward_start\tforward_end\treverse_start\treverse_end"
                    + "\tproduct_size\tforward_mismatches\treverse_mismatches\n");
            try (FastaReader reader = new FastaReader(request.path("genome"))) {
                FastaRecord record;
                while (!capped[0] && !capped[1] && (record = reader.next()) != null) {
                    context.cancellationToken().throwIfCancelled();
                    stats[0]++;
                    String sequence = record.sequence().toUpperCase(Locale.ROOT);
                    for (Primer primer : primers) {
                        if (capped[0] || capped[1]) {
                            break;
                        }
                        context.cancellationToken().throwIfCancelled();
                        List<Match> forward = findMatches(sequence, primer.forward(), maxMismatches, capped);
                        if (capped[1]) {
                            break;
                        }
                        List<Match> reverse = findMatches(sequence, reverseComplement(primer.reverse()),
                                maxMismatches, capped);
                        if (capped[1]) {
                            break;
                        }
                        for (Match f : forward) {
                            Match r = nearestProduct(f, reverse, primer.forward().length(),
                                    primer.reverse().length(), minProduct, maxProduct);
                            if (r == null) {
                                continue;
                            }
                            int forwardStart = f.position() + 1;
                            int forwardEnd = f.position() + primer.forward().length();
                            int reverseStart = r.position() + 1;
                            int reverseEnd = r.position() + primer.reverse().length();
                            int product = reverseEnd - forwardStart + 1;
                            writer.write(primer.name());
                            writer.write('\t'); writer.write(record.id());
                            writer.write('\t'); writer.write(Integer.toString(forwardStart));
                            writer.write('\t'); writer.write(Integer.toString(forwardEnd));
                            writer.write('\t'); writer.write(Integer.toString(reverseStart));
                            writer.write('\t'); writer.write(Integer.toString(reverseEnd));
                            writer.write('\t'); writer.write(Integer.toString(product));
                            writer.write('\t'); writer.write(Integer.toString(f.mismatches()));
                            writer.write('\t'); writer.write(Integer.toString(r.mismatches()));
                            writer.newLine();
                            stats[1]++;
                            if (stats[1] >= maxAmplicons) {
                                capped[0] = true;
                                break;
                            }
                        }
                    }
                }
            }
            if (capped[0]) {
                writer.write("# truncated at " + maxAmplicons + " amplicons\n");
            } else if (capped[1]) {
                writer.write("# truncated at " + MAX_MATCHES_PER_PRIMER + " matches for a primer\n");
            }
        });

        context.progress(1.0, "Primer check completed");
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("primers", primers.size());
        metrics.put("sequences", stats[0]);
        metrics.put("amplicons", stats[1]);
        metrics.put("truncated", capped[0] || capped[1]);
        metrics.put("maxMismatches", maxMismatches);
        metrics.put("minProductSize", minProduct);
        metrics.put("maxProductSize", maxProduct);
        return new ToolResult(
                "Predicted " + stats[1] + " amplicons",
                List.of(stats[1]),
                primers.size() + " primer pairs across " + stats[0] + " sequences"
                        + (capped[0] || capped[1] ? "; result truncated" : ""),
                List.of(primers.size()),
                new ResultTable(List.of("Metric", "Value"), List.of(
                        List.of("Primers", Integer.toString(primers.size())),
                        List.of("Sequences", Long.toString(stats[0])),
                        List.of("Amplicons", Long.toString(stats[1])),
                        List.of("Truncated", Boolean.toString(capped[0] || capped[1]))
                )),
                List.of(new OutputArtifact(output, "Predicted amplicons", "text/tab-separated-values")),
                metrics
        );
    }

    private static Match nearestProduct(
            Match forward,
            List<Match> reverse,
            int forwardLength,
            int reverseLength,
            int minProduct,
            int maxProduct
    ) {
        int index = lowerBound(reverse, forward.position() + forwardLength);
        while (index < reverse.size()) {
            Match candidate = reverse.get(index);
            int product = candidate.position() + reverseLength - forward.position();
            if (product > maxProduct) {
                return null;
            }
            if (product >= minProduct) {
                return candidate;
            }
            index++;
        }
        return null;
    }

    private static int lowerBound(List<Match> matches, int position) {
        int low = 0;
        int high = matches.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (matches.get(middle).position() < position) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    private static List<Match> findMatches(
            String sequence,
            String primer,
            int maxMismatches,
            boolean[] capped
    ) {
        List<Match> matches = new ArrayList<>();
        if (maxMismatches == 0 && isPlainAcgt(primer)) {
            int position = 0;
            while ((position = sequence.indexOf(primer, position)) >= 0) {
                matches.add(new Match(position, 0));
                if (matches.size() >= MAX_MATCHES_PER_PRIMER) {
                    capped[1] = true;
                    return matches;
                }
                position++;
            }
            return matches;
        }
        int last = sequence.length() - primer.length();
        for (int position = 0; position <= last; position++) {
            int mismatches = 0;
            for (int offset = 0; offset < primer.length(); offset++) {
                if (!matchesBase(primer.charAt(offset), sequence.charAt(position + offset))) {
                    mismatches++;
                    if (mismatches > maxMismatches) {
                        break;
                    }
                }
            }
            if (mismatches <= maxMismatches) {
                matches.add(new Match(position, mismatches));
                if (matches.size() >= MAX_MATCHES_PER_PRIMER) {
                    capped[1] = true;
                    return matches;
                }
            }
        }
        return matches;
    }

    private static boolean isPlainAcgt(String primer) {
        for (int i = 0; i < primer.length(); i++) {
            char base = primer.charAt(i);
            if (base != 'A' && base != 'C' && base != 'G' && base != 'T') {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesBase(char primer, char sequence) {
        return switch (primer) {
            case 'A' -> sequence == 'A';
            case 'C' -> sequence == 'C';
            case 'G' -> sequence == 'G';
            case 'T' -> sequence == 'T';
            case 'R' -> sequence == 'A' || sequence == 'G';
            case 'Y' -> sequence == 'C' || sequence == 'T';
            case 'S' -> sequence == 'C' || sequence == 'G';
            case 'W' -> sequence == 'A' || sequence == 'T';
            case 'K' -> sequence == 'G' || sequence == 'T';
            case 'M' -> sequence == 'A' || sequence == 'C';
            case 'B' -> sequence == 'C' || sequence == 'G' || sequence == 'T';
            case 'D' -> sequence == 'A' || sequence == 'G' || sequence == 'T';
            case 'H' -> sequence == 'A' || sequence == 'C' || sequence == 'T';
            case 'V' -> sequence == 'A' || sequence == 'C' || sequence == 'G';
            case 'N' -> true;
            default -> false;
        };
    }

    private static String reverseComplement(String primer) {
        StringBuilder result = new StringBuilder(primer.length());
        for (int i = primer.length() - 1; i >= 0; i--) {
            result.append(switch (primer.charAt(i)) {
                case 'A' -> 'T';
                case 'C' -> 'G';
                case 'G' -> 'C';
                case 'T' -> 'A';
                case 'R' -> 'Y';
                case 'Y' -> 'R';
                case 'S' -> 'S';
                case 'W' -> 'W';
                case 'K' -> 'M';
                case 'M' -> 'K';
                case 'B' -> 'V';
                case 'D' -> 'H';
                case 'H' -> 'D';
                case 'V' -> 'B';
                case 'N' -> 'N';
                default -> 'N';
            });
        }
        return result.toString();
    }

    private static List<Primer> readPrimers(Path path) throws Exception {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                io.opentools.formats.Compression.open(path), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank() && !line.startsWith("#")) {
                    lines.add(line);
                }
            }
        }
        if (lines.isEmpty()) {
            throw new OpenToolException("PRIMER_TABLE_EMPTY", "The primer table is empty.",
                    "Provide a header and at least one primer row.");
        }
        String[] header = lines.getFirst().split("\t", -1);
        int nameColumn = findColumn(header, "name");
        int forwardColumn = findColumn(header, "forward");
        int reverseColumn = findColumn(header, "reverse");
        if (nameColumn < 0 || forwardColumn < 0 || reverseColumn < 0) {
            throw new OpenToolException("PRIMER_HEADER_INVALID",
                    "Primer table needs name, forward, and reverse columns.",
                    "Use a tab-separated header such as name<TAB>forward<TAB>reverse.");
        }
        List<Primer> primers = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String[] fields = lines.get(i).split("\t", -1);
            if (fields.length <= Math.max(nameColumn, Math.max(forwardColumn, reverseColumn))) {
                throw new OpenToolException("PRIMER_ROW_INVALID",
                        "Primer row " + (i + 1) + " has too few columns.",
                        "Check the tab-separated primer table.");
            }
            String name = fields[nameColumn].trim();
            String forward = fields[forwardColumn].trim().toUpperCase(Locale.ROOT);
            String reverse = fields[reverseColumn].trim().toUpperCase(Locale.ROOT);
            if (name.isEmpty() || forward.isEmpty() || reverse.isEmpty()) {
                continue;
            }
            validatePrimer(forward, "forward", name);
            validatePrimer(reverse, "reverse", name);
            primers.add(new Primer(name, forward, reverse));
        }
        if (primers.isEmpty()) {
            throw new OpenToolException("PRIMER_TABLE_EMPTY", "No primer rows were found.",
                    "Provide at least one name/forward/reverse row.");
        }
        if (primers.size() > 1000) {
            throw new OpenToolException("PRIMER_TABLE_TOO_LARGE",
                    "The primer table contains more than 1000 pairs.", "Split the table into smaller runs.");
        }
        return primers;
    }

    private static int findColumn(String[] header, String name) {
        for (int i = 0; i < header.length; i++) {
            if (header[i].trim().equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    private static void validatePrimer(String primer, String label, String name) {
        if (primer.length() < 8 || primer.length() > 60) {
            throw new OpenToolException("PRIMER_LENGTH_INVALID",
                    "Primer " + name + " " + label + " length must be 8-60 nt.",
                    "Check the primer sequence.");
        }
        for (int i = 0; i < primer.length(); i++) {
            if ("ACGTRYSWKMBDHVN".indexOf(primer.charAt(i)) < 0) {
                throw new OpenToolException("PRIMER_BASE_INVALID",
                        "Primer " + name + " " + label + " contains an invalid base.",
                        "Use A/C/G/T or IUPAC ambiguity codes.");
            }
        }
    }

    private record Primer(String name, String forward, String reverse) {
    }

    private record Match(int position, int mismatches) {
    }
}
