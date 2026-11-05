package io.opentools.ui;

import io.opentools.core.i18n.LanguageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.SwingUtilities;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BlastViewerPanelTest {
    @TempDir
    Path tempDir;

    @Test
    void selectsOverviewRowsAndRendersAlignedBases() throws Exception {
        Path input = tempDir.resolve("alignments.tsv");
        Files.writeString(input, """
                query\tsubject\tq_start\tq_end\ts_start\ts_end\tevalue\tbit_score\talignment_length\tquery_alignment\tsubject_alignment\tmidline
                q1\ts1\t1\t8\t10\t17\t1e-12\t50\t8\tACGTACGT\tACGTACGT\t||||||||
                q1\ts2\t3\t8\t20\t25\t1e-5\t30\t6\tGTACGT\tGT-CGT\t|| |||
                """);
        LanguageManager language = new LanguageManager(Path.of("..").toAbsolutePath(), "en_US");
        AtomicReference<BlastViewerPanel> reference = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                reference.set(new BlastViewerPanel(language, input));
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
        BlastViewerPanel panel = reference.get();
        assertEquals(2, panel.alignmentCount());
        assertEquals(0, panel.selectedAlignment());
        SwingUtilities.invokeAndWait(() -> panel.select(1));
        assertEquals(1, panel.selectedAlignment());
        assertTrue(panel.detailText().contains("q1"));
        assertTrue(panel.detailText().contains("s2"));
        assertTrue(panel.detailText().contains("GT-CGT"));
    }
}
