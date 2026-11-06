package io.opentools.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Projects reference, consensus, assembly, and reads onto one alignment grid.
 *
 * <p>The expanded consensus contains insertion bases and omits deleted
 * positions, so its index is not a reference coordinate after the first indel.
 * This class assigns every base to an explicit display column instead.</p>
 */
final class SangerDisplayAlignment {
    static final int GAP = -1;

    record Indel(int position, String type, String sequence, int length, int support) {
        boolean insertion() {
            return "INSERTION".equalsIgnoreCase(type);
        }

        boolean deletion() {
            return "DELETION".equalsIgnoreCase(type);
        }
    }

    record Column(
            int referenceIndex,
            int consensusIndex,
            int assemblyIndex,
            boolean insertion,
            boolean assemblyOnly
    ) {
    }

    private final String reference;
    private final String consensus;
    private final String assembly;
    private final List<Column> columns;
    private final int[] referenceToColumn;
    private final int[] consensusToColumn;
    private final int[] assemblyToColumn;
    private final int[] referenceToAssembly;
    private final int[] columnToReference;
    private final int prefixColumns;
    private final int coreColumns;
    private final int fallbackOffset;
    private final boolean assemblyMapped;

    private SangerDisplayAlignment(
            String reference,
            String consensus,
            String assembly,
            List<Column> columns,
            int prefixColumns,
            int coreColumns,
            int fallbackOffset,
            boolean assemblyMapped
    ) {
        this.reference = reference;
        this.consensus = consensus;
        this.assembly = assembly;
        this.columns = List.copyOf(columns);
        this.prefixColumns = prefixColumns;
        this.coreColumns = coreColumns;
        this.fallbackOffset = Math.max(0, fallbackOffset);
        this.assemblyMapped = assemblyMapped;
        this.referenceToColumn = new int[reference.length()];
        this.consensusToColumn = new int[consensus.length()];
        this.assemblyToColumn = new int[assembly.length()];
        this.referenceToAssembly = new int[reference.length()];
        Arrays.fill(referenceToColumn, GAP);
        Arrays.fill(consensusToColumn, GAP);
        Arrays.fill(assemblyToColumn, GAP);
        Arrays.fill(referenceToAssembly, GAP);
        this.columnToReference = new int[this.columns.size()];
        Arrays.fill(columnToReference, GAP);
        for (int column = 0; column < this.columns.size(); column++) {
            Column item = this.columns.get(column);
            if (item.referenceIndex() >= 0) referenceToColumn[item.referenceIndex()] = column;
            if (item.consensusIndex() >= 0) consensusToColumn[item.consensusIndex()] = column;
            if (item.assemblyIndex() >= 0) assemblyToColumn[item.assemblyIndex()] = column;
            if (item.referenceIndex() >= 0 && item.assemblyIndex() >= 0) {
                referenceToAssembly[item.referenceIndex()] = item.assemblyIndex();
            }
        }
        fillReferenceAssemblyGaps();
        fillColumnReferences();
    }

    static SangerDisplayAlignment build(
            String reference,
            String corrected,
            String expanded,
            List<Indel> indels,
            int minimumSupport,
            String assembly,
            String insert,
            int fallbackOffset
    ) {
        String ref = normalize(reference);
        String correctedSequence = normalize(corrected);
        String expandedSequence = normalize(expanded);
        String assemblySequence = normalize(assembly);
        if (ref.isEmpty()) ref = correctedSequence;
        if (ref.isEmpty()) ref = expandedSequence;
        if (correctedSequence.isEmpty() || correctedSequence.length() != ref.length()) {
            correctedSequence = ref;
        }
        if (assemblySequence.isEmpty()) assemblySequence = expandedSequence;
        if (assemblySequence.isEmpty()) assemblySequence = correctedSequence;

        Map<Integer, String> insertions = new LinkedHashMap<>();
        boolean[] deleted = new boolean[ref.length()];
        int minimum = Math.max(1, minimumSupport);
        for (Indel indel : indels == null ? List.<Indel>of() : indels) {
            if (indel == null || indel.support() < minimum) continue;
            int position = indel.position() - 1;
            if (indel.insertion()) {
                if (position < 0 || position > ref.length()) continue;
                String sequence = normalize(indel.sequence());
                if (!sequence.isEmpty()) insertions.merge(position, sequence, String::concat);
            } else if (indel.deletion()) {
                int start = Math.max(0, position);
                int end = Math.min(ref.length(), start + Math.max(1, indel.length()));
                for (int index = start; index < end; index++) deleted[index] = true;
            }
        }

        String rebuilt = rebuild(correctedSequence, insertions, deleted);
        boolean verified = !expandedSequence.isEmpty() && rebuilt.equals(expandedSequence);
        if (!verified) {
            insertions.clear();
            Arrays.fill(deleted, false);
        }
        String consensusSequence = verified ? expandedSequence : correctedSequence;

        List<Column> core = coreColumns(ref.length(), insertions, deleted);
        int consensusBases = 0;
        for (Column column : core) {
            if (column.consensusIndex() >= 0) consensusBases++;
        }
        if (consensusBases != consensusSequence.length()) {
            core.clear();
            consensusSequence = correctedSequence;
            for (int index = 0; index < ref.length(); index++) {
                core.add(new Column(index, index, GAP, false, false));
            }
        }

        AlignmentMapping mapping = locateAssembly(
                assemblySequence, consensusSequence, correctedSequence, ref, normalize(insert));
        List<Column> columns = new ArrayList<>();
        int prefix = 0;
        if (mapping != null) {
            prefix = mapping.offset();
            for (int index = 0; index < prefix; index++) {
                columns.add(new Column(GAP, GAP, index, false, true));
            }
            for (Column column : core) {
                int assemblyIndex = GAP;
                if (mapping.consensus() && column.consensusIndex() >= 0) {
                    assemblyIndex = mapping.offset() + column.consensusIndex();
                } else if (!mapping.consensus() && column.referenceIndex() >= 0) {
                    assemblyIndex = mapping.offset() + column.referenceIndex();
                }
                columns.add(new Column(
                        column.referenceIndex(),
                        column.consensusIndex(),
                        assemblyIndex,
                        column.insertion(),
                        false
                ));
            }
            int alignedLength = mapping.consensus() ? consensusSequence.length() : ref.length();
            int end = Math.min(assemblySequence.length(), mapping.offset() + alignedLength);
            for (int index = end; index < assemblySequence.length(); index++) {
                columns.add(new Column(GAP, GAP, index, false, true));
            }
        } else {
            columns.addAll(core);
        }
        return new SangerDisplayAlignment(
                ref, consensusSequence, assemblySequence, columns,
                prefix, core.size(), fallbackOffset, mapping != null
        );
    }

    String reference() {
        return reference;
    }

    String consensus() {
        return consensus;
    }

    String assembly() {
        return assembly;
    }

    int referenceLength() {
        return reference.length();
    }

    int consensusLength() {
        return consensus.length();
    }

    int assemblyLength() {
        return assembly.length();
    }

    int columnCount() {
        return columns.size();
    }

    int coreColumns() {
        return coreColumns;
    }

    int prefixColumns() {
        return prefixColumns;
    }

    boolean assemblyMapped() {
        return assemblyMapped;
    }

    Column column(int index) {
        return columns.get(index);
    }

    int columnForReference(int referenceIndex) {
        if (referenceIndex < 0 || referenceIndex >= referenceToColumn.length) return GAP;
        return referenceToColumn[referenceIndex];
    }

    int columnForConsensus(int consensusIndex) {
        if (consensusIndex < 0 || consensusIndex >= consensusToColumn.length) return GAP;
        return consensusToColumn[consensusIndex];
    }

    int columnForAssembly(int assemblyIndex) {
        if (assemblyIndex < 0 || assemblyIndex >= assemblyToColumn.length) return GAP;
        return assemblyToColumn[assemblyIndex];
    }

    int referenceAtColumn(int column) {
        if (columnToReference.length == 0) return 0;
        int index = Math.max(0, Math.min(columnToReference.length - 1, column));
        return Math.max(0, columnToReference[index]);
    }

    int referenceSpanStart(int referenceStart) {
        int start = Math.max(0, Math.min(reference.length() - 1, referenceStart));
        int column = columnForReference(start);
        return Math.max(0, column);
    }

    int referenceSpanEndExclusive(int referenceEnd) {
        if (referenceEnd <= 0) return 0;
        int end = Math.min(reference.length(), referenceEnd) - 1;
        int column = columnForReference(end);
        return Math.max(0, column) + 1;
    }

    int referenceX(int referenceIndex, int left, double pixelsPerBase) {
        int column = columnForReference(referenceIndex);
        if (column == GAP) return referenceLeft(left, pixelsPerBase);
        if (assemblyMapped) {
            return left + (int) Math.round(column * pixelsPerBase);
        }
        return left + (int) Math.round((referenceOriginColumn() + column) * pixelsPerBase);
    }

    int consensusX(int consensusIndex, int left, double pixelsPerBase) {
        int column = columnForConsensus(consensusIndex);
        if (column == GAP) return referenceLeft(left, pixelsPerBase);
        return referenceColumnX(column, left, pixelsPerBase);
    }

    int assemblyX(int assemblyIndex, int left, double pixelsPerBase) {
        int column = columnForAssembly(assemblyIndex);
        if (column == GAP || !assemblyMapped) {
            return left + (int) Math.round(assemblyIndex * pixelsPerBase);
        }
        return left + (int) Math.round(column * pixelsPerBase);
    }

    int assemblyIndexForReference(int referenceIndex) {
        if (referenceIndex < 0 || referenceIndex >= referenceToAssembly.length) return 0;
        int column = columnForReference(referenceIndex);
        if (column == GAP) return 0;
        if (!assemblyMapped) return referenceOriginColumn() + column;
        int assemblyIndex = referenceToAssembly[referenceIndex];
        return assemblyIndex == GAP ? 0 : assemblyIndex;
    }

    int assemblyBoundaryColumn(int assemblyIndex) {
        if (assemblyIndex <= 0) return 0;
        if (assemblyIndex >= assembly.length()) {
            return assemblyMapped ? columnCount() : assembly.length();
        }
        int column = columnForAssembly(assemblyIndex);
        return column == GAP ? (assemblyMapped ? columnCount() : assemblyIndex) : column;
    }

    int assemblyBoundaryX(int assemblyIndex, int left, double pixelsPerBase) {
        return left + (int) Math.round(assemblyBoundaryColumn(assemblyIndex) * pixelsPerBase);
    }

    int referenceColumnX(int column, int left, double pixelsPerBase) {
        if (assemblyMapped) {
            return left + (int) Math.round(column * pixelsPerBase);
        }
        return left + (int) Math.round((referenceOriginColumn() + column) * pixelsPerBase);
    }

    int referenceLeft(int left, double pixelsPerBase) {
        return left + (int) Math.round(referenceOriginColumn() * pixelsPerBase);
    }

    int columnAtX(double x, int left, double pixelsPerBase) {
        if (coreColumns == 0) return 0;
        double pixels = Math.max(0.001, pixelsPerBase);
        int raw = (int) Math.round((x - left) / pixels);
        if (assemblyMapped) {
            return Math.max(prefixColumns, Math.min(prefixColumns + coreColumns - 1, raw));
        }
        return Math.max(0, Math.min(coreColumns - 1, raw - referenceOriginColumn()));
    }

    int referenceAtX(double x, int left, double pixelsPerBase) {
        return referenceAtColumn(columnAtX(x, left, pixelsPerBase));
    }

    int spanColumns() {
        if (assemblyMapped) return columnCount();
        return Math.max(assembly.length(), referenceOriginColumn() + coreColumns);
    }

    private int referenceOriginColumn() {
        return assemblyMapped ? prefixColumns : fallbackOffset;
    }

    private void fillReferenceAssemblyGaps() {
        int last = GAP;
        for (int index = 0; index < referenceToAssembly.length; index++) {
            if (referenceToAssembly[index] >= 0) {
                last = referenceToAssembly[index];
            } else if (last >= 0) {
                referenceToAssembly[index] = last;
            }
        }
        int next = GAP;
        for (int index = referenceToAssembly.length - 1; index >= 0; index--) {
            if (referenceToAssembly[index] >= 0) {
                next = referenceToAssembly[index];
            } else if (next >= 0) {
                referenceToAssembly[index] = next;
            }
        }
        for (int index = 0; index < referenceToAssembly.length; index++) {
            if (referenceToAssembly[index] < 0) referenceToAssembly[index] = 0;
        }
    }

    private void fillColumnReferences() {
        int last = -1;
        for (int column = 0; column < columns.size(); column++) {
            Column item = columns.get(column);
            if (item.referenceIndex() >= 0) last = item.referenceIndex();
            if (item.referenceIndex() >= 0) {
                columnToReference[column] = item.referenceIndex();
            } else if (item.assemblyOnly() && column < prefixColumns) {
                columnToReference[column] = 0;
            } else if (item.assemblyOnly()) {
                columnToReference[column] = -1;
            } else {
                columnToReference[column] = -1;
            }
        }
        int next = -1;
        for (int column = columns.size() - 1; column >= 0; column--) {
            Column item = columns.get(column);
            if (item.referenceIndex() >= 0) {
                next = item.referenceIndex();
            } else if (item.assemblyOnly()) {
                if (column >= prefixColumns) columnToReference[column] = last;
            } else if (next >= 0) {
                columnToReference[column] = next;
            } else if (last >= 0) {
                columnToReference[column] = last;
            }
        }
        if (!columns.isEmpty() && reference.length() > 0) {
            for (int column = 0; column < columns.size(); column++) {
                if (columnToReference[column] < 0) columnToReference[column] = 0;
            }
        }
    }

    private static List<Column> coreColumns(
            int referenceLength,
            Map<Integer, String> insertions,
            boolean[] deleted
    ) {
        List<Column> core = new ArrayList<>();
        int consensusIndex = 0;
        for (int referenceIndex = 0; referenceIndex <= referenceLength; referenceIndex++) {
            String insertion = insertions.get(referenceIndex);
            if (insertion != null) {
                for (int index = 0; index < insertion.length(); index++) {
                    core.add(new Column(GAP, consensusIndex++, GAP, true, false));
                }
            }
            if (referenceIndex == referenceLength) break;
            int consensusBase = deleted[referenceIndex] ? GAP : consensusIndex++;
            core.add(new Column(referenceIndex, consensusBase, GAP, false, false));
        }
        return core;
    }

    private static String rebuild(
            String corrected,
            Map<Integer, String> insertions,
            boolean[] deleted
    ) {
        StringBuilder result = new StringBuilder(corrected.length());
        for (int index = 0; index <= corrected.length(); index++) {
            String insertion = insertions.get(index);
            if (insertion != null) result.append(insertion);
            if (index == corrected.length()) break;
            if (!deleted[index]) result.append(corrected.charAt(index));
        }
        return result.toString();
    }

    private static AlignmentMapping locateAssembly(
            String assembly,
            String consensus,
            String corrected,
            String reference,
            String insert
    ) {
        if (assembly.isEmpty()) return null;
        int position = consensus.isEmpty() ? -1 : assembly.indexOf(consensus);
        if (position >= 0) return new AlignmentMapping(position, true);
        position = corrected.isEmpty() ? -1 : assembly.indexOf(corrected);
        if (position >= 0) return new AlignmentMapping(position, false);
        position = reference.isEmpty() ? -1 : assembly.indexOf(reference);
        if (position >= 0) return new AlignmentMapping(position, false);
        if (!insert.isEmpty()) {
            int insertPosition = assembly.indexOf(insert);
            if (insertPosition >= 0) {
                position = consensus.isEmpty() ? -1 : insert.indexOf(consensus);
                if (position >= 0) return new AlignmentMapping(insertPosition + position, true);
                position = corrected.isEmpty() ? -1 : insert.indexOf(corrected);
                if (position >= 0) return new AlignmentMapping(insertPosition + position, false);
                position = reference.isEmpty() ? -1 : insert.indexOf(reference);
                if (position >= 0) return new AlignmentMapping(insertPosition + position, false);
            }
        }
        return null;
    }

    private static String normalize(String sequence) {
        return sequence == null ? "" : sequence.toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private record AlignmentMapping(int offset, boolean consensus) {
    }
}
