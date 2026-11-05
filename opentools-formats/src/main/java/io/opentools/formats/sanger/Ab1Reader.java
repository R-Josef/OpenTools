package io.opentools.formats.sanger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Minimal ABIF/AB1 reader for basecalls and Phred qualities. */
public final class Ab1Reader {
    private Ab1Reader() {
    }

    public record Trace(String name, String sequence, int[] qualities, int[] peakPositions) {
        public char base(int index) {
            return sequence.charAt(index);
        }
    }

    public static Trace read(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        boolean little = bytes.length >= 4 && bytes[0] == 'A' && bytes[1] == 'B'
                && bytes[2] == 'I' && bytes[3] == 'F' ? false : isLittleEndian(bytes);
        Directory directory = directory(bytes, little);
        if (directory == null) {
            directory = directory(bytes, !little);
            little = !little;
        }
        if (directory == null) {
            throw new IOException("Not an ABIF/AB1 trace file: " + path);
        }
        String sequence = tagString(bytes, directory, "BASE", 1, little);
        if (sequence == null || sequence.isBlank()) {
            sequence = tagString(bytes, directory, "PBAS", 1, little);
        }
        if (sequence == null || sequence.isBlank()) {
            sequence = tagString(bytes, directory, "PBAS", 2, little);
        }
        if (sequence == null || sequence.isBlank()) {
            throw new IOException("AB1 file has no basecall sequence: " + path);
        }
        sequence = clean(sequence);
        byte[] quality = tagBytes(bytes, directory, "PCON", 1, little);
        int[] qualities = new int[sequence.length()];
        if (quality != null) {
            for (int index = 0; index < qualities.length; index++) {
                qualities[index] = index < quality.length ? Byte.toUnsignedInt(quality[index]) : 0;
            }
        }
        int[] peaks = tagShorts(bytes, directory, "PLOC", 1, little);
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        return new Trace(name, sequence, qualities, peaks == null ? new int[0] : peaks);
    }

    private static String clean(String sequence) {
        StringBuilder clean = new StringBuilder(sequence.length());
        for (int index = 0; index < sequence.length(); index++) {
            char value = Character.toUpperCase(sequence.charAt(index));
            if (value == 'A' || value == 'C' || value == 'G' || value == 'T' || value == 'N') {
                clean.append(value);
            }
        }
        return clean.toString();
    }

    private static Directory directory(byte[] bytes, boolean little) {
        if (bytes.length < 128) return null;
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(little ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        int size = buffer.getInt(22);
        int offset = buffer.getInt(26);
        if (size <= 0 || offset <= 0 || offset >= bytes.length) return null;
        int count = size / 28;
        if (count <= 0 || count > 100_000) return null;
        List<Entry> entries = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            int at = offset + index * 28;
            if (at + 28 > bytes.length) break;
            String tag = new String(bytes, at, 4, StandardCharsets.US_ASCII);
            if (tag.isBlank() || tag.charAt(0) == 0) continue;
            int number = buffer.getInt(at + 4);
            int type = Short.toUnsignedInt(buffer.getShort(at + 8));
            int elementSize = Short.toUnsignedInt(buffer.getShort(at + 10));
            int countElements = buffer.getInt(at + 12);
            int dataSize = buffer.getInt(at + 16);
            int dataOffset = buffer.getInt(at + 20);
            entries.add(new Entry(tag, number, type, elementSize, countElements, dataSize, dataOffset));
        }
        return new Directory(entries);
    }

    private static boolean isLittleEndian(byte[] bytes) {
        if (bytes.length < 4) return false;
        // Most ABI files start with ABIF; a reversed magic is not standard, but
        // retain a conservative probe for uncommon little-endian exports.
        return false;
    }

    private static Entry find(Directory directory, String tag, int number) {
        for (Entry entry : directory.entries()) {
            if (entry.tag().equals(tag) && entry.number() == number) return entry;
        }
        return null;
    }

    private static String tagString(byte[] bytes, Directory directory, String tag, int number, boolean little) {
        byte[] data = tagBytes(bytes, directory, tag, number, little);
        return data == null ? null : new String(data, StandardCharsets.US_ASCII).trim();
    }

    private static byte[] tagBytes(byte[] bytes, Directory directory, String tag, int number, boolean little) {
        Entry entry = find(directory, tag, number);
        if (entry == null) return null;
        int size = entry.dataSize() > 0
                ? entry.dataSize() : entry.elementSize() * Math.max(1, entry.count());
        int offset = entry.dataOffset();
        if (size <= 4 && (offset <= 0 || offset >= bytes.length)) {
            offset = entryOffsetInline(entry, little);
        }
        if (offset <= 0 || offset + size > bytes.length) return null;
        byte[] result = new byte[size];
        System.arraycopy(bytes, offset, result, 0, size);
        return result;
    }

    private static int entryOffsetInline(Entry entry, boolean little) {
        // ABIF stores values of four bytes or fewer in the offset field itself.
        // The caller only reaches this path for very small tags.
        return entry.dataOffset();
    }

    private static int[] tagShorts(byte[] bytes, Directory directory, String tag, int number, boolean little) {
        Entry entry = find(directory, tag, number);
        if (entry == null) return null;
        int count = entry.count();
        int offset = entry.dataOffset();
        if (offset <= 0 || offset + count * 2 > bytes.length) return null;
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(little ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        int[] values = new int[count];
        for (int index = 0; index < count; index++) values[index] = buffer.getShort(offset + index * 2);
        return values;
    }

    private record Directory(List<Entry> entries) {
    }

    private record Entry(
            String tag,
            int number,
            int type,
            int elementSize,
            int count,
            int dataSize,
            int dataOffset
    ) {
    }
}
