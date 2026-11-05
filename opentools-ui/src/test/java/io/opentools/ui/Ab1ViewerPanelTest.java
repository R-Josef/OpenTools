package io.opentools.ui;

import io.opentools.core.i18n.LanguageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.SwingUtilities;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class Ab1ViewerPanelTest {
    @TempDir
    Path tempDir;

    @Test
    void loadsTraceAndSelectsRead() throws Exception {
        Path file = tempDir.resolve("trace.ab1");
        Files.write(file, ab1("ACGTACGT"));
        LanguageManager language = new LanguageManager(Path.of("..").toAbsolutePath(), "en_US");
        AtomicReference<Ab1ViewerPanel> reference = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> reference.set(
                new Ab1ViewerPanel(language, List.of(file))));
        Ab1ViewerPanel panel = reference.get();
        assertEquals(1, panel.readCount());
        assertEquals(0, panel.selectedRead());
        assertEquals(8, panel.baseCount());
        double initialZoom = panel.zoomLevel();
        panel.zoomIn();
        assertEquals(initialZoom * 1.5, panel.zoomLevel(), 0.001);
        panel.zoomOut();
        assertEquals(initialZoom, panel.zoomLevel(), 0.001);
    }

    private static byte[] ab1(String sequence) {
        byte[] bases = sequence.getBytes(StandardCharsets.US_ASCII);
        byte[] quality = new byte[bases.length];
        java.util.Arrays.fill(quality, (byte) 40);
        int directory = 128;
        int entries = 6;
        int data = directory + entries * 28;
        int baseData = data;
        int qualityData = baseData + bases.length;
        int dataStart = qualityData + quality.length;
        int channelBytes = 8;
        ByteBuffer buffer = ByteBuffer.allocate(dataStart + channelBytes * 4).order(ByteOrder.BIG_ENDIAN);
        buffer.put(0, (byte) 'A');
        buffer.put(1, (byte) 'B');
        buffer.put(2, (byte) 'I');
        buffer.put(3, (byte) 'F');
        buffer.putInt(22, entries * 28);
        buffer.putInt(26, directory);
        entry(buffer, directory, "PBAS", 1, 2, 1, bases.length, bases.length, baseData);
        entry(buffer, directory + 28, "PCON", 1, 2, 1, quality.length, quality.length, qualityData);
        for (int channel = 0; channel < 4; channel++) {
            int offset = dataStart + channel * channelBytes;
            entry(buffer, directory + (channel + 2) * 28, "DATA", channel + 1,
                    4, 2, 4, channelBytes, offset);
            for (int sample = 0; sample < 4; sample++) {
                buffer.putShort(offset + sample * 2, (short) (100 + sample * 50 + channel * 10));
            }
        }
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
