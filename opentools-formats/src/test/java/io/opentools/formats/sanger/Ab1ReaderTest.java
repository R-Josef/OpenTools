package io.opentools.formats.sanger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class Ab1ReaderTest {
    @TempDir
    Path tempDir;

    @Test
    void readsBasecallsAndQualitiesFromAbifDirectory() throws Exception {
        String sequence = "ACGTACGT";
        int[] qualities = {10, 20, 30, 40, 50, 60, 30, 20};
        Path path = tempDir.resolve("trace.ab1");
        Files.write(path, ab1(sequence, qualities));

        Ab1Reader.Trace trace = Ab1Reader.read(path);

        assertEquals(sequence, trace.sequence());
        assertEquals(qualities.length, trace.qualities().length);
        assertEquals(10, trace.qualities()[0]);
        assertEquals(60, trace.qualities()[5]);
        assertEquals("trace", trace.name());
    }

    @Test
    void prefersMobilityCorrectedChannelsInAcgtOrder() throws Exception {
        String sequence = "ACGT";
        int[] peaks = {10, 20, 30, 40};
        int[][] data = new int[12][];
        data[0] = values(50, 10, 1);
        data[1] = values(50, 20, 1);
        data[2] = values(50, 30, 1);
        data[3] = values(50, 40, 1);
        data[8] = values(50, 30, 1000);
        data[9] = values(50, 10, 1000);
        data[10] = values(50, 40, 1000);
        data[11] = values(50, 20, 1000);
        Path path = tempDir.resolve("multi-channel.ab1");
        Files.write(path, ab1WithTrace(sequence, new int[]{20, 20, 20, 20}, peaks, data));

        Ab1Reader.Trace trace = Ab1Reader.read(path);

        assertEquals(4, trace.channels().length);
        assertEquals(1000, trace.channels()[0][10]);
        assertEquals(1000, trace.channels()[1][20]);
        assertEquals(1000, trace.channels()[2][30]);
        assertEquals(1000, trace.channels()[3][40]);
    }

    @Test
    void fallsBackToRawChannelsWhenCorrectedDataIsAbsent() throws Exception {
        String sequence = "ACGT";
        int[] peaks = {10, 20, 30, 40};
        int[][] data = new int[12][];
        data[0] = values(50, 10, 1000);
        data[1] = values(50, 20, 1000);
        data[2] = values(50, 30, 1000);
        data[3] = values(50, 40, 1000);
        Path path = tempDir.resolve("raw-channel.ab1");
        Files.write(path, ab1WithTrace(sequence, new int[]{20, 20, 20, 20}, peaks, data));

        Ab1Reader.Trace trace = Ab1Reader.read(path);

        assertEquals(1000, trace.channels()[0][10]);
        assertEquals(1000, trace.channels()[1][20]);
        assertEquals(1000, trace.channels()[2][30]);
        assertEquals(1000, trace.channels()[3][40]);
    }

    private static int[] values(int length, int position, int value) {
        int[] values = new int[length];
        values[position] = value;
        return values;
    }

    private static byte[] ab1WithTrace(
            String sequence,
            int[] qualities,
            int[] peaks,
            int[][] data
    ) {
        List<int[]> channelData = new ArrayList<>();
        List<Integer> channelNumbers = new ArrayList<>();
        for (int index = 0; index < data.length; index++) {
            if (data[index] == null) continue;
            channelData.add(data[index]);
            channelNumbers.add(index + 1);
        }
        int entries = 3 + channelData.size();
        int directorySize = entries * 28;
        int dataStart = 128 + directorySize;
        List<Block> blocks = new ArrayList<>();
        blocks.add(new Block("PBAS", 1, 2, 1, sequence.length()));
        blocks.add(new Block("PCON", 1, 2, 1, qualities.length));
        blocks.add(new Block("PLOC", 1, 4, 2, peaks.length));
        for (int index = 0; index < channelData.size(); index++) {
            blocks.add(new Block("DATA", channelNumbers.get(index), 4, 2, channelData.get(index).length));
        }
        int offset = dataStart;
        for (Block block : blocks) {
            block.offset = offset;
            offset += block.size();
        }
        ByteBuffer buffer = ByteBuffer.allocate(offset).order(ByteOrder.BIG_ENDIAN);
        buffer.put(0, (byte) 'A');
        buffer.put(1, (byte) 'B');
        buffer.put(2, (byte) 'I');
        buffer.put(3, (byte) 'F');
        buffer.putInt(22, directorySize);
        buffer.putInt(26, 128);
        int entryOffset = 128;
        for (Block block : blocks) {
            entry(buffer, entryOffset, block.tag, block.number, block.type,
                    block.elementSize, block.count, block.size(), block.offset);
            entryOffset += 28;
        }
        buffer.position(blocks.get(0).offset);
        buffer.put(sequence.getBytes(StandardCharsets.US_ASCII));
        buffer.position(blocks.get(1).offset);
        for (int quality : qualities) buffer.put((byte) quality);
        buffer.position(blocks.get(2).offset);
        for (int peak : peaks) buffer.putShort((short) peak);
        for (int index = 0; index < channelData.size(); index++) {
            buffer.position(blocks.get(3 + index).offset);
            for (int value : channelData.get(index)) buffer.putShort((short) value);
        }
        return buffer.array();
    }

    private static final class Block {
        private final String tag;
        private final int number;
        private final int type;
        private final int elementSize;
        private final int count;
        private int offset;

        private Block(String tag, int number, int type, int elementSize, int count) {
            this.tag = tag;
            this.number = number;
            this.type = type;
            this.elementSize = elementSize;
            this.count = count;
        }

        private int size() {
            return count * elementSize;
        }
    }

    private static byte[] ab1(String sequence, int[] qualities) {
        byte[] bases = sequence.getBytes(StandardCharsets.US_ASCII);
        byte[] quality = new byte[qualities.length];
        for (int index = 0; index < qualities.length; index++) quality[index] = (byte) qualities[index];
        int directory = 128;
        int directorySize = 2 * 28;
        int data = directory + directorySize;
        int baseData = data;
        int qualityData = baseData + bases.length;
        ByteBuffer buffer = ByteBuffer.allocate(qualityData + quality.length).order(ByteOrder.BIG_ENDIAN);
        buffer.put(0, (byte) 'A');
        buffer.put(1, (byte) 'B');
        buffer.put(2, (byte) 'I');
        buffer.put(3, (byte) 'F');
        buffer.putInt(22, directorySize);
        buffer.putInt(26, directory);
        entry(buffer, directory, "PBAS", 1, 2, 1, bases.length, bases.length, baseData);
        entry(buffer, directory + 28, "PCON", 1, 2, 1, quality.length, quality.length, qualityData);
        buffer.position(baseData);
        buffer.put(bases);
        buffer.position(qualityData);
        buffer.put(quality);
        return buffer.array();
    }

    private static void entry(
            ByteBuffer buffer,
            int offset,
            String tag,
            int number,
            int type,
            int elementSize,
            int count,
            int dataSize,
            int dataOffset
    ) {
        buffer.position(offset);
        buffer.put(tag.getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(number);
        buffer.putShort((short) type);
        buffer.putShort((short) elementSize);
        buffer.putInt(count);
        buffer.putInt(dataSize);
        buffer.putInt(dataOffset);
        buffer.putInt(0);
    }
}
