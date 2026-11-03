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
    void acceptsMemeInternalDoctype() throws Exception {
        Path xml = tempDir.resolve("meme-doctype.xml");
        Files.writeString(xml, """
                <?xml version="1.0"?>
                <!DOCTYPE MEME [
                <!ELEMENT MEME (motifs)>
                <!ELEMENT motifs (motif*)>
                <!ELEMENT motif EMPTY>
                <!ATTLIST motif id CDATA #REQUIRED width CDATA #REQUIRED sites CDATA #REQUIRED evalue CDATA #REQUIRED>
                ]>
                <MEME version="5.5.9">
                  <motifs>
                    <motif id="MEME-1" width="8" sites="12" evalue="1.2e-10"/>
                  </motifs>
                </MEME>
                """);
        assertEquals(1, MemeResults.readMotifs(xml).size());
    }

    @Test
    void readsMastExplicitStartAndEnd() throws Exception {
        Path xml = tempDir.resolve("mast-explicit.xml");
        Files.writeString(xml, """
                <?xml version="1.0"?>
                <MAST>
                  <sequences>
                    <sequence id="seq1">
                      <seg start="5" end="16" strand="-1">
                        <hit motiff="MEME-1" position="4" start="5" end="16" strand="-1" pvalue="5.5e-8" match="MEME-1"/>
                      </seg>
                    </sequence>
                  </sequences>
                </MAST>
                """);
        var hits = MemeResults.readMastHits(xml);
        assertEquals(1, hits.size());
        assertEquals(5, hits.get(0).start());
        assertEquals(16, hits.get(0).end());
        assertEquals("-1", hits.get(0).strand());
    }

    @Test
    void convertsMastHitListToXml() throws Exception {
        var hits = MastHitList.parse("""
                # sequence_name (strand+/-)motif id alt_id hit_start hit_end score hit_p-value
                seq0 -1 TACGTACGTACG MEME-1 5 16 2450.10 5.55e-08
                seq1 +1 TACGTACGTACG MEME-1 25 36 2450.10 5.55e-08
                """);
        assertEquals(2, hits.size());
        assertEquals("seq0", hits.get(0).sequence());
        assertEquals(5, hits.get(0).start());
        Path xml = tempDir.resolve("mast-fallback.xml");
        MastHitList.writeXml(xml, hits);
        var parsed = MemeResults.readMastHits(xml);
        assertEquals(2, parsed.size());
        assertEquals(5, parsed.get(0).start());
        assertEquals(16, parsed.get(0).end());
    }

    @Test
    void rejectsMalformedXml() throws Exception {
        Path xml = tempDir.resolve("bad.xml");
        Files.writeString(xml, "<MEME><motifs>");
        assertThrows(OpenToolException.class, () -> MemeResults.readMotifs(xml));
    }
}
