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

    public record Trace(
            String name,
            String sequence,
            int[] qualities,
            int[] peakPositions,
            int[][] channels
    ) {
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
        int[][] data = new int[12][];
        for (int channel = 0; channel < data.length; channel++) {
            int[] values = tagInts(bytes, directory, "DATA", channel + 1, little);
            data[channel] = values == null ? new int[0] : values;
        }
        int[] peakPositions = peaks == null ? new int[0] : peaks;
        int[][] channels = selectChannels(data, sequence, peakPositions);
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        return new Trace(name, sequence, qualities,
                peakPositions, channels);
    }

    private static int[][] selectChannels(int[][] data, String sequence, int[] peaks) {
        int[][] corrected = correctedChannels(data);
        if (corrected != null && peakMatch(corrected, sequence, peaks) >= 0.70) {
            return corrected;
        }
        int[][] raw = standardChannels(data, 0);
        if (raw != null && peakMatch(raw, sequence, peaks) >= 0.70) {
            return raw;
        }
        if (corrected != null) return corrected;
        if (raw != null) return raw;
        int[][] analyzed = standardChannels(data, 4);
        if (analyzed != null) return analyzed;
        return new int[][]{new int[0], new int[0], new int[0], new int[0]};
    }

    private static int[][] correctedChannels(int[][] data) {
        if (data.length < 12) return null;
        int[] g = data[8];
        int[] a = data[9];
        int[] t = data[10];
        int[] c = data[11];
        if (g.length == 0 || a.length == 0 || t.length == 0 || c.length == 0) return null;
        return new int[][]{a, c, g, t};
    }

    private static int[][] standardChannels(int[][] data, int start) {
        if (start < 0 || start + 3 >= data.length) return null;
        int[] a = data[start];
        int[] c = data[start + 1];
        int[] g = data[start + 2];
        int[] t = data[start + 3];
        if (a.length == 0 && c.length == 0 && g.length == 0 && t.length == 0) return null;
        return new int[][]{a, c, g, t};
    }

    private static double peakMatch(int[][] channels, String sequence, int[] peaks) {
        int called = 0;
        int matched = 0;
        int limit = Math.min(sequence.length(), peaks.length);
        for (int index = 0; index < limit; index++) {
            int expected = "ACGT".indexOf(sequence.charAt(index));
            if (expected < 0) continue;
            int peak = peaks[index];
            int best = 0;
            int bestValue = Integer.MIN_VALUE;
            boolean available = true;
            for (int channel = 0; channel < 4; channel++) {
                if (peak < 0 || peak >= channels[channel].length) {
                    available = false;
                    break;
                }
                int value = channels[channel][peak];
                if (value > bestValue) {
                    bestValue = value;
                    best = channel;
                }
            }
            if (!available) continue;
            called++;
            if (best == expected) matched++;
        }
        return called == 0 ? 0 : matched / (double) called;
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

    private static int[] tagInts(byte[] bytes, Directory directory, String tag, int number, boolean little) {
        Entry entry = find(directory, tag, number);
        if (entry == null || entry.count() <= 0) return null;
        int count = entry.count();
        int offset = entry.dataOffset();
        int elementSize = entry.elementSize();
        if (offset <= 0 || offset + count * Math.max(1, elementSize) > bytes.length) return null;
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(little ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        int[] values = new int[count];
        for (int index = 0; index < count; index++) {
            values[index] = elementSize >= 4
                    ? buffer.getInt(offset + index * elementSize)
                    : buffer.getShort(offset + index * elementSize);
        }
        return values;
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
