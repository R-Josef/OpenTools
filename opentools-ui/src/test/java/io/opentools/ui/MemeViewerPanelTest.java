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

final class MemeViewerPanelTest {
    @TempDir
    Path tempDir;

    @Test
    void readsMotifMatrixAndBuildsConsensus() throws Exception {
        Path xml = tempDir.resolve("meme.xml");
        Files.writeString(xml, """
                <?xml version="1.0"?>
                <MEME version="5.5.5">
                  <motifs>
                    <motif id="motif_1" name="M1" alt="MEME-1" width="2" sites="5" e_value="1e-8">
                      <probabilities><alphabet_matrix>
                        <alphabet_array>
                          <value letter_id="A">0.8</value><value letter_id="C">0.1</value>
                          <value letter_id="G">0.05</value><value letter_id="T">0.05</value>
                        </alphabet_array>
                        <alphabet_array>
                          <value letter_id="A">0.05</value><value letter_id="C">0.1</value>
                          <value letter_id="G">0.8</value><value letter_id="T">0.05</value>
                        </alphabet_array>
                      </alphabet_matrix></probabilities>
                      <contributing_sites>
                        <contributing_site sequence_id="seq1" position="10" strand="plus" pvalue="1e-6"/>
                      </contributing_sites>
                    </motif>
                  </motifs>
                </MEME>
                """);
        LanguageManager language = new LanguageManager(Path.of("..").toAbsolutePath(), "en_US");
        AtomicReference<MemeViewerPanel> reference = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                reference.set(new MemeViewerPanel(language, xml));
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
        MemeViewerPanel panel = reference.get();
        assertEquals(1, panel.motifCount());
        assertEquals(0, panel.selectedMotif());
        assertEquals("AG", panel.consensusText());
        assertEquals(1, panel.siteCount());
        assertTrue(panel.consensusText().contains("A"));
    }
}
