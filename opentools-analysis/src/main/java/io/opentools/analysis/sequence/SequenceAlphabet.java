package io.opentools.analysis.sequence;

final class SequenceAlphabet {
    private SequenceAlphabet() {
    }

    static String reverseComplement(String sequence) {
        StringBuilder result = new StringBuilder(sequence.length());
        for (int index = sequence.length() - 1; index >= 0; index--) {
            char source = sequence.charAt(index);
            char upper = Character.toUpperCase(source);
            char mapped = switch (upper) {
                case 'A' -> 'T';
                case 'T' -> 'A';
                case 'U' -> 'A';
                case 'C' -> 'G';
                case 'G' -> 'C';
                case 'R' -> 'Y';
                case 'Y' -> 'R';
                case 'S' -> 'S';
                case 'W' -> 'W';
                case 'K' -> 'M';
                case 'M' -> 'K';
                case 'B' -> 'V';
                case 'V' -> 'B';
                case 'D' -> 'H';
                case 'H' -> 'D';
                case 'N' -> 'N';
                case 'X' -> 'X';
                default -> upper;
            };
            result.append(Character.isLowerCase(source) ? Character.toLowerCase(mapped) : mapped);
        }
        return result.toString();
    }
}
