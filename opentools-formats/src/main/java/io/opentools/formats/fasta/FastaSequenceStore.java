package io.opentools.formats.fasta;

import io.opentools.api.tool.OpenToolException;
import io.opentools.formats.Compression;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Random-access FASTA store. Uncompressed, uniformly wrapped FASTA is indexed
 * without retaining chromosome strings; gzip or non-standard wrapping falls
 * back to an in-memory store.
 */
public final class FastaSequenceStore {
    private final Path path;
    private final Map<String, String> sequences;
    private final Map<String, SequenceIndex> indexes;
    private final String storageMode;

    private FastaSequenceStore(
            Path path,
            Map<String, String> sequences,
            Map<String, SequenceIndex> indexes,
            String storageMode
    ) {
        this.path = path;
        this.sequences = sequences == null ? null : Map.copyOf(sequences);
        this.indexes = indexes == null ? null : Map.copyOf(indexes);
        this.storageMode = storageMode;
    }

    public static FastaSequenceStore load(Path path) throws IOException {
        if (!Compression.isGzip(path) && Files.isRegularFile(path)) {
            Map<String, SequenceIndex> indexes = buildIndex(path);
            if (indexes != null) {
                if (indexes.isEmpty()) {
                    throw new OpenToolException(
                            "EMPTY_FASTA", "No genome sequences were found.", "Check the FASTA input."
                    );
                }
                return new FastaSequenceStore(path, null, indexes, "indexed");
            }
        }
        return loadInMemory(path);
    }

    public boolean contains(String seqId) {
        return indexes != null ? indexes.containsKey(seqId) : sequences.containsKey(seqId);
    }

    public long length(String seqId) {
        if (indexes != null) {
            SequenceIndex index = indexes.get(seqId);
            return index == null ? -1 : index.length();
        }
        String sequence = sequences.get(seqId);
        return sequence == null ? -1 : sequence.length();
    }

    public String subsequence(String seqId, long start, long end) {
        long length = length(seqId);
        if (length < 0) {
            throw new OpenToolException(
                    "SEQUENCE_NOT_FOUND",
                    "Genome sequence is missing: " + seqId,
                    "Check the FASTA sequence names."
            );
        }
        if (start < 1 || end < start || end > length) {
            throw new OpenToolException(
                    "COORDINATE_OUT_OF_RANGE",
                    "Coordinates " + start + ".." + end + " are outside " + seqId
                            + " length " + length + ".",
                    "Check the genome assembly and annotation versions."
            );
        }
        if (end - start + 1 > Integer.MAX_VALUE) {
            throw new OpenToolException(
                    "SEQUENCE_TOO_LARGE",
                    "A single requested subsequence exceeds the Java string size limit.",
                    "Extract a smaller region."
            );
        }
        if (indexes != null) {
            return readIndexed(seqId, start, end);
        }
        return sequences.get(seqId).substring((int) (start - 1), (int) end);
    }

    public int sequenceCount() {
        return indexes != null ? indexes.size() : sequences.size();
    }

    public String storageMode() {
        return storageMode;
    }

    private String readIndexed(String seqId, long start, long end) {
        SequenceIndex index = indexes.get(seqId);
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            StringBuilder sequence = new StringBuilder((int) (end - start + 1));
            long position = start - 1;
            long endExclusive = end;
            byte[] buffer = new byte[8192];
            while (position < endExclusive) {
                long line = position / index.lineBases();
                int column = (int) (position % index.lineBases());
                int length = (int) Math.min(
                        Math.min(index.lineBases() - column, endExclusive - position),
                        buffer.length
                );
                long offset = index.firstBaseOffset() + line * index.lineWidth() + column;
                file.seek(offset);
                file.readFully(buffer, 0, length);
                for (int cell = 0; cell < length; cell++) {
                    sequence.append(Character.toUpperCase((char) (buffer[cell] & 0xff)));
                }
                position += length;
            }
            return sequence.toString();
        } catch (IOException ex) {
            throw new OpenToolException(
                    "FASTA_INDEX_READ_FAILED",
                    "Could not read indexed FASTA sequence " + seqId + ".",
                    "Check that the FASTA file was not modified after it was opened.",
                    ex
            );
        }
    }

    private static Map<String, SequenceIndex> buildIndex(Path path) throws IOException {
        Map<String, SequenceIndex> indexes = new LinkedHashMap<>();
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            String id = null;
            long firstBaseOffset = -1;
            long lineBases = -1;
            long lineWidth = -1;
            long length = 0;
            long previousLength = -1;
            long previousWidth = -1;

            while (true) {
                long lineStart = file.getFilePointer();
                String line = file.readLine();
                if (line == null) {
                    if (id != null) {
                        indexes.put(id, new SequenceIndex(
                                firstBaseOffset, lineBases, lineWidth, length));
                    }
                    break;
                }
                long lineBytes = file.getFilePointer() - lineStart;
                if (lineStart == 0 && !line.isEmpty() && line.charAt(0) == '\ufeff') {
                    line = line.substring(1);
                }
                if (line.isBlank()) {
                    if (id != null && length > 0) {
                        return null;
                    }
                    continue;
                }
                if (line.startsWith(">")) {
                    if (id != null) {
                        indexes.put(id, new SequenceIndex(
                                firstBaseOffset, lineBases, lineWidth, length));
                    }
                    String header = line.substring(1).trim();
                    id = header.isBlank() ? "" : header.split("\\s+", 2)[0];
                    if (id.isBlank()) {
                        throw new OpenToolException(
                                "FASTA_EMPTY_ID",
                                "A FASTA record has no identifier.",
                                "Add a non-empty identifier after '>'."
                        );
                    }
                    if (indexes.containsKey(id)) {
                        throw duplicate(id);
                    }
                    firstBaseOffset = -1;
                    lineBases = -1;
                    lineWidth = -1;
                    length = 0;
                    previousLength = -1;
                    previousWidth = -1;
                    continue;
                }
                if (id == null || line.startsWith(";")) {
                    return null;
                }
                if (line.chars().anyMatch(Character::isWhitespace)) {
                    return null;
                }
                long newlineBytes = lineBytes - line.length();
                if (newlineBytes < 0 || newlineBytes > 2) {
                    return null;
                }
                if (firstBaseOffset < 0) {
                    firstBaseOffset = lineStart;
                    lineBases = line.length();
                    lineWidth = lineBytes;
                } else if (previousLength != lineBases || previousWidth != lineWidth) {
                    return null;
                }
                previousLength = line.length();
                previousWidth = lineBytes;
                length += line.length();
            }
        }
        return indexes;
    }

    private static FastaSequenceStore loadInMemory(Path path) throws IOException {
        Map<String, String> sequences = new LinkedHashMap<>();
        try (FastaReader reader = new FastaReader(path)) {
            FastaRecord record;
            while ((record = reader.next()) != null) {
                if (record.id().isBlank()) {
                    throw new OpenToolException(
                            "FASTA_EMPTY_ID",
                            "A FASTA record has no identifier.",
                            "Add a non-empty identifier after '>'."
                    );
                }
                if (sequences.putIfAbsent(
                        record.id(),
                        record.sequence().toUpperCase(Locale.ROOT)
                ) != null) {
                    throw duplicate(record.id());
                }
            }
        }
        if (sequences.isEmpty()) {
            throw new OpenToolException(
                    "EMPTY_FASTA", "No genome sequences were found.", "Check the FASTA input."
            );
        }
        return new FastaSequenceStore(path, sequences, null, "in-memory");
    }

    private static OpenToolException duplicate(String id) {
        return new OpenToolException(
                "DUPLICATE_SEQUENCE_ID",
                "Duplicate FASTA identifier: " + id,
                "Make sequence identifiers unique before using the genome for extraction."
        );
    }

    public static String reverseComplement(String sequence) {
        StringBuilder result = new StringBuilder(sequence.length());
        for (int i = sequence.length() - 1; i >= 0; i--) {
            char base = Character.toUpperCase(sequence.charAt(i));
            result.append(switch (base) {
                case 'A' -> 'T';
                case 'T', 'U' -> 'A';
                case 'G' -> 'C';
                case 'C' -> 'G';
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
                default -> 'N';
            });
        }
        return result.toString();
    }

    private record SequenceIndex(
            long firstBaseOffset,
            long lineBases,
            long lineWidth,
            long length
    ) {
    }
}
