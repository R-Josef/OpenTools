package io.opentools.analysis.sequence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Small local aligner used to project Sanger reads onto a reference. */
final class SangerAligner {
    private static final int MATCH = 2;
    private static final int MISMATCH = -3;
    private static final int GAP = -4;

    private SangerAligner() {
    }

    static Alignment best(String reference, String read) {
        Alignment forward = align(reference, read, false);
        Alignment reverse = align(reference, reverseComplement(read), true);
        if (forward == null) return reverse;
        if (reverse == null) return forward;
        if (forward.score() != reverse.score()) {
            return forward.score() > reverse.score() ? forward : reverse;
        }
        return reverse.identity() > forward.identity() ? reverse : forward;
    }

    private static Alignment align(String reference, String read, boolean reverse) {
        if (read == null || read.isEmpty() || reference == null || reference.isEmpty()) return null;
        int n = read.length();
        int m = reference.length();
        int[][] score = new int[n + 1][m + 1];
        byte[][] trace = new byte[n + 1][m + 1];
        int bestScore = 0;
        int bestI = 0;
        int bestJ = 0;
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                int diagonal = score[i - 1][j - 1]
                        + (read.charAt(i - 1) == reference.charAt(j - 1) ? MATCH : MISMATCH);
                int up = score[i - 1][j] + GAP;
                int left = score[i][j - 1] + GAP;
                int value = Math.max(0, Math.max(diagonal, Math.max(up, left)));
                score[i][j] = value;
                trace[i][j] = (byte) (value == 0 ? 0 : value == diagonal ? 1 : value == up ? 2 : 3);
                if (value > bestScore) {
                    bestScore = value;
                    bestI = i;
                    bestJ = j;
                }
            }
        }
        if (bestScore == 0) return null;
        int i = bestI;
        int j = bestJ;
        List<Character> operations = new ArrayList<>();
        int matches = 0;
        int aligned = 0;
        while (i > 0 && j > 0 && score[i][j] > 0) {
            byte direction = trace[i][j];
            if (direction == 1) {
                operations.add('M');
                if (read.charAt(i - 1) == reference.charAt(j - 1)) matches++;
                aligned++;
                i--;
                j--;
            } else if (direction == 2) {
                operations.add('I');
                i--;
            } else {
                operations.add('D');
                j--;
            }
        }
        Collections.reverse(operations);
        return new Alignment(
                read, reverse, score[bestI][bestJ],
                aligned == 0 ? 0 : matches / (double) aligned,
                i, bestI, j, bestJ, List.copyOf(operations)
        );
    }

    private static String reverseComplement(String sequence) {
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

    record Alignment(
            String orientedRead,
            boolean reverse,
            int score,
            double identity,
            int readStart,
            int readEnd,
            int referenceStart,
            int referenceEnd,
            List<Character> operations
    ) {
    }
}
