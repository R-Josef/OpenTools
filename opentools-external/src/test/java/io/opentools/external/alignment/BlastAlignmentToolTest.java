package io.opentools.external.alignment;

import io.opentools.api.tool.CancellationToken;
import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolRequest;
import io.opentools.core.tool.ToolRegistry;
import io.opentools.external.ExternalTools;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BlastAlignmentToolTest {
    @TempDir
    Path tempDir;

    @Test
    void extractsPairedAlignmentsFromBlastXml() throws Exception {
        Path input = tempDir.resolve("blast.xml");
        Files.writeString(input, """
                <?xml version="1.0"?>
                <BlastOutput>
                  <BlastOutput_iterations>
                    <Iteration>
                      <Iteration_query-def>q1 description</Iteration_query-def>
                      <Iteration_query-ID>Query_1</Iteration_query-ID>
                      <Iteration_hits>
                        <Hit>
                          <Hit_id>Subject_1</Hit_id>
                          <Hit_accession>s1</Hit_accession>
                          <Hit_hsps>
                            <Hsp>
                              <Hsp_query-from>1</Hsp_query-from>
                              <Hsp_query-to>8</Hsp_query-to>
                              <Hsp_hit-from>10</Hsp_hit-from>
                              <Hsp_hit-to>17</Hsp_hit-to>
                              <Hsp_evalue>1e-12</Hsp_evalue>
                              <Hsp_bit-score>50</Hsp_bit-score>
                              <Hsp_align-len>8</Hsp_align-len>
                              <Hsp_qseq>ACGTACGT</Hsp_qseq>
                              <Hsp_hseq>ACGTACGT</Hsp_hseq>
                              <Hsp_midline>||||||||</Hsp_midline>
                            </Hsp>
                          </Hit_hsps>
                        </Hit>
                      </Iteration_hits>
                    </Iteration>
                  </BlastOutput_iterations>
                </BlastOutput>
                """);
        Path output = tempDir.resolve("alignments.tsv");
        ToolRegistry registry = new ToolRegistry();
        ExternalTools.register(registry);
        Path out = Files.createDirectories(tempDir.resolve("out"));
        ToolExecutionContext context = new ToolExecutionContext("blast-align", tempDir, out, out,
                ignored -> { }, ignored -> { }, new CancellationToken());
        var tool = registry.find("alignment.blast.alignments").orElseThrow();
        ToolRequest request = new ToolRequest("alignment.blast.alignments", Map.of(
                "input", input,
                "output", output
        ));
        var validation = tool.validate(request);
        assertTrue(validation.valid(), validation.issues().toString());
        var result = tool.execute(request, context);

        assertEquals(1L, ((Number) result.metrics().get("hspRecords")).longValue());
        String text = Files.readString(output);
        assertTrue(text.contains("q1\ts1\t1\t8\t10\t17\t1e-12\t50\t8\tACGTACGT\tACGTACGT\t||||||||"));
    }
}
