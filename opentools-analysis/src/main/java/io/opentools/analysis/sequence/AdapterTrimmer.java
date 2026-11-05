package io.opentools.analysis.sequence;

import io.opentools.formats.fasta.FastaReader;
import io.opentools.formats.fasta.FastaRecord;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Trims common sequencing primers and user-supplied vector/primer sequences. */
final class AdapterTrimmer {
    private static final List<Adapter> BUILT_INS = List.of(
            new Adapter("M13-F", "GTAAAACGACGGCCAGT"),
            new Adapter("M13-R", "CAGGAAACAGCTATGAC"),
            new Adapter("T7", "TAATACGACTCACTATAGGG"),
            new Adapter("SP6", "ATTTAGGTGACACTATAG"),
            new Adapter("T3", "AATTAACCCTCACTAAAGGG")
    );

    private final List<Adapter> adapters = new ArrayList<>();

    private AdapterTrimmer() {
    }

    static AdapterTrimmer load(boolean includeBuiltIns, Path file) throws Exception {
        AdapterTrimmer trimmer = new AdapterTrimmer();
        if (includeBuiltIns) trimmer.adapters.addAll(BUILT_INS);
        if (file != null) {
            try (FastaReader reader = new FastaReader(file)) {
                FastaRecord record;
                while ((record = reader.next()) != null) {
                    String sequence = record.sequence().toUpperCase(Locale.ROOT)
                            .replaceAll("[^ACGTN]", "");
                    if (sequence.length() >= 8) {
                        trimmer.adapters.add(new Adapter(record.id(), sequence));
                    }
                }
            }
        }
        return trimmer;
    }

    TrimResult trim(String sequence, int[] qualities, int minimumMatch) {
        if (sequence == null || sequence.isEmpty()) return new TrimResult(sequence, qualities, 0, 0, null, null);
        String current = sequence;
        int[] currentQualities = qualities;
        int leftBases = 0;
        int rightBases = 0;
        String leftName = null;
        String rightName = null;
        for (int pass = 0; pass < 8; pass++) {
            Match left = best(current, minimumMatch, true);
            if (left != null) {
                current = current.substring(left.length());
                currentQualities = slice(currentQualities, left.length(), currentQualities == null
                        ? 0 : currentQualities.length);
                leftBases += left.length();
                leftName = left.name();
                continue;
            }
            Match right = best(current, minimumMatch, false);
            if (right != null) {
                current = current.substring(0, current.length() - right.length());
                currentQualities = slice(currentQualities, 0, currentQualities == null
                        ? 0 : currentQualities.length - right.length());
                rightBases += right.length();
                rightName = right.name();
                continue;
            }
            break;
        }
        return new TrimResult(current, currentQualities, leftBases, rightBases, leftName, rightName);
    }

    private Match best(String sequence, int minimumMatch, boolean left) {
        Match best = null;
        for (Adapter adapter : adapters) {
            for (String oriented : List.of(adapter.sequence(), reverseComplement(adapter.sequence()))) {
                int maximum = Math.min(adapter.sequence().length(), sequence.length());
                for (int length = maximum; length >= minimumMatch; length--) {
                    String probe = oriented.substring(0, length);
                    String target = left ? sequence.substring(0, length)
                            : sequence.substring(sequence.length() - length);
                    int mismatches = mismatches(probe, target);
                    if (mismatches > Math.max(1, length / 10)) continue;
                    if (best == null || length > best.length()) {
                        best = new Match(adapter.name(), length);
                    }
                    break;
                }
            }
        }
        return best;
    }

    private static int mismatches(String a, String b) {
        int count = 0;
        for (int index = 0; index < Math.min(a.length(), b.length()); index++) {
            if (a.charAt(index) != b.charAt(index)) count++;
        }
        return count;
    }

    private static int[] slice(int[] values, int start, int end) {
        if (values == null) return null;
        int from = Math.max(0, Math.min(values.length, start));
        int to = Math.max(from, Math.min(values.length, end));
        return java.util.Arrays.copyOfRange(values, from, to);
    }

    static String reverseComplement(String sequence) {
        StringBuilder result = new StringBuilder(sequence.length());
        for (int index = sequence.length() - 1; index >= 0; index--) {
            result.append(switch (sequence.charAt(index)) {
                case 'A' -> 'T';
                case 'C' -> 'G';
                case 'G' -> 'C';
                case 'T' -> 'A';
                default -> 'N';
            });
        }
        return result.toString();
    }

    private record Adapter(String name, String sequence) {
    }

    private record Match(String name, int length) {
    }

    record TrimResult(
            String sequence,
            int[] qualities,
            int leftBases,
            int rightBases,
            String leftAdapter,
            String rightAdapter
    ) {
    }
}
