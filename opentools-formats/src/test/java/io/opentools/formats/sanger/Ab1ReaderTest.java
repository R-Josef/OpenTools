package io.opentools.formats.sanger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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
