package io.opentools.external.annotation;

import io.opentools.api.tool.OpenToolException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class MemeMastParserTest {
    @TempDir
    Path tempDir;

    @Test
    void readsMemeMotifs() throws Exception {
        Path xml = tempDir.resolve("meme.xml");
        Files.writeString(xml, """
                <?xml version="1.0"?>
                <MEME version="5.5.5">
                  <motifs>
                    <motif id="MEME-1" name="M1" width="8" sites="12" evalue="1.2e-10"/>
                    <motif id="MEME-2" width="6" sites="4" evalue="0.01"/>
                  </motifs>
                </MEME>
                """);
        var motifs = MemeResults.readMotifs(xml);
        assertEquals(2, motifs.size());
        assertEquals("MEME-1", motifs.get(0).id());
        assertEquals(8, motifs.get(0).width());
        assertEquals(12, motifs.get(0).sites());
        assertEquals(1.2e-10, motifs.get(0).evalue(), 1e-15);
    }

    @Test
    void readsMastHitsWithOneBasedCoordinates() throws Exception {
        Path xml = tempDir.resolve("mast.xml");
        Files.writeString(xml, """
                <?xml version="1.0"?>
                <MAST version="5.5.5">
                  <sequences>
                    <sequence id="seq1" length="100">
                      <seg start="1" stop="100">
                        <hit motiff="MEME-1" strand="+" position="9" pvalue="2.5e-6" match="ACGTACGT"/>
                        <hit motif="MEME-2" strand="-" position="49" pvalue="0.001" match="TTTT"/>
                      </seg>
                    </sequence>
                  </sequences>
                </MAST>
                """);
        var hits = MemeResults.readMastHits(xml);
        assertEquals(2, hits.size());
        assertEquals("seq1", hits.get(0).sequence());
        assertEquals("MEME-1", hits.get(0).motif());
        assertEquals(10, hits.get(0).start());
        assertEquals(17, hits.get(0).end());
        assertEquals("-", hits.get(1).strand());
    }

    @Test
    void rejectsMalformedXml() throws Exception {
        Path xml = tempDir.resolve("bad.xml");
        Files.writeString(xml, "<MEME><motifs>");
        assertThrows(OpenToolException.class, () -> MemeResults.readMotifs(xml));
    }
}
