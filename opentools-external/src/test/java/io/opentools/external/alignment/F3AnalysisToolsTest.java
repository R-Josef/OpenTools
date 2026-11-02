package io.opentools.external.alignment;

import io.opentools.api.tool.CancellationToken;
import io.opentools.core.automation.ToolContracts;
import io.opentools.api.tool.OpenTool;
import io.opentools.api.tool.OpenToolException;
import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolResult;
import io.opentools.api.tool.ToolParameter;
import io.opentools.core.tool.ToolRegistry;
import io.opentools.api.tool.ToolRequest;
import io.opentools.external.ExternalTools;
import io.opentools.external.annotation.EggnogImportTool;
import io.opentools.external.annotation.HmmerDomTableParser;
import io.opentools.external.annotation.HmmerSearchTool;
import io.opentools.external.annotation.HmmsearchTool;
import io.opentools.external.annotation.PfamImportTool;
import io.opentools.external.tool.ExternalToolSpecs;
import io.opentools.formats.fastq.FastqFastaConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class F3AnalysisToolsTest {
    @TempDir
    Path tempDir;

    @Test
    void convertsBlastXmlToTwelveColumnTable() throws Exception {
        Path xml = tempDir.resolve("blast.xml");
        Files.writeString(xml, """
                <?xml version="1.0"?>
                <BlastOutput>
                  <BlastOutput_iterations>
                    <Iteration>
                      <Iteration_query-def>q1 query</Iteration_query-def>
                      <Iteration_hits>
                        <Hit>
                          <Hit_id>lcl|s1</Hit_id>
                          <Hit_accession>s1</Hit_accession>
                          <Hit_hsps>
                            <Hsp>
                              <Hsp_bit-score>88.5</Hsp_bit-score>
                              <Hsp_evalue>1e-10</Hsp_evalue>
                              <Hsp_query-from>1</Hsp_query-from>
                              <Hsp_query-to>10</Hsp_query-to>
                              <Hsp_hit-from>4</Hsp_hit-from>
                              <Hsp_hit-to>13</Hsp_hit-to>
                              <Hsp_identity>9</Hsp_identity>
                              <Hsp_align-len>10</Hsp_align-len>
                              <Hsp_gaps>1</Hsp_gaps>
                            </Hsp>
                          </Hit_hsps>
                        </Hit>
                      </Iteration_hits>
                    </Iteration>
                  </BlastOutput_iterations>
                </BlastOutput>
                """);
        Path output = tempDir.resolve("table.tsv");
        ToolResult result = run("alignment.blast.xml-to-table", Map.of(
                "input", xml,
                "output", output
        ));
        assertEquals(1, result.metrics().get("hits"));
        String line = Files.readAllLines(output).getFirst();
        assertTrue(line.startsWith("q1\ts1\t90.0\t10\t1\t1\t1\t10\t4\t13"));
    }

    @Test
    void rejectsBlastXmlDoctypeAndExternalEntities() throws Exception {
        Path xml = tempDir.resolve("malicious.xml");
        Files.writeString(xml, """
                <?xml version="1.0"?>
                <!DOCTYPE BlastOutput [<!ENTITY xxe SYSTEM "file:///does-not-exist">]>
                <BlastOutput><BlastOutput_iterations><Iteration>
                  <Iteration_query-def>&xxe;</Iteration_query-def>
                </Iteration></BlastOutput_iterations></BlastOutput>
                """);

        OpenToolException exception = assertThrows(OpenToolException.class, () -> run(
                "alignment.blast.xml-to-table",
                Map.of("input", xml, "output", tempDir.resolve("blocked.tsv"))
        ));

        assertEquals("BLAST_XML_INVALID", exception.code());
    }

    @Test
    void retainsMutualBestBlastHits() throws Exception {
        Path forward = tempDir.resolve("forward.tsv");
        Path reverse = tempDir.resolve("reverse.tsv");
        Files.writeString(forward, """
                q1\ts1\t90\t10\t1\t0\t1\t10\t1\t10\t1e-20\t100
                q1\ts2\t80\t10\t2\t0\t1\t10\t1\t10\t1e-10\t50
                q2\ts2\t95\t10\t0\t0\t1\t10\t1\t10\t1e-30\t150
                """);
        Files.writeString(reverse, """
                s1\tq1\t90\t10\t1\t0\t1\t10\t1\t10\t1e-20\t100
                s2\tq2\t95\t10\t0\t0\t1\t10\t1\t10\t1e-30\t150
                s2\tq1\t80\t10\t2\t0\t1\t10\t1\t10\t1e-10\t50
                """);
        Path output = tempDir.resolve("rbh.tsv");
        ToolResult result = run("alignment.blast.reciprocal", Map.of(
                "forward", forward,
                "reverse", reverse,
                "output", output
        ));
        assertEquals(2, result.metrics().get("reciprocalPairs"));
        assertTrue(Files.readString(output).contains("q1\ts1"));
        assertTrue(Files.readString(output).contains("q2\ts2"));
    }

    @Test
    void importsPfamAndEggnogAnnotations() throws Exception {
        Path domtbl = tempDir.resolve("hits.domtbl");
        Files.writeString(domtbl,
                "PF00001  PF00001.20  100  protein1  -  250  1e-20  80.2  0.0  1  1  1e-23  1e-23  70.1  0.0  1  100  5  50  5  50  0.98  Kinase domain\\n");
        assertEquals(1, new HmmerDomTableParser().parse(domtbl).size());
        Path pfam = tempDir.resolve("pfam.tsv");
        ToolResult pfamResult = run("annotation.pfam.import", Map.of(
                "input", domtbl,
                "output", pfam
        ));
        assertEquals(1, pfamResult.metrics().get("domainHits"));
        assertTrue(Files.readString(pfam).contains("PF00001"));

        Path eggnog = tempDir.resolve("eggnog.tsv");
        Files.writeString(eggnog, """
                query\tseed_ortholog\tPreferred_name\tGOs\tKEGG_ko\tCOG_category\tevalue\tscore\tDescription
                gene1\t1ABC\tKIN1\tGO:000001;GO:000002\tko:K00001\tT\t1e-50\t900\tKinase
                gene2\t2DEF\t-\t-\t-\tS\t1e-20\t300\tUnknown
                """);
        Path normalized = tempDir.resolve("eggnog-normalized.tsv");
        ToolResult eggnogResult = run("annotation.eggnog.import", Map.of(
                "input", eggnog,
                "output", normalized
        ));
        assertEquals(2L, eggnogResult.metrics().get("rows"));
        assertEquals(1L, eggnogResult.metrics().get("goRows"));
        assertTrue(Files.readString(normalized).contains("gene1\t1ABC\tKIN1"));
    }

    @Test
    void preparesFastqAndStrandedRegionQueries() throws Exception {
        Path fastq = tempDir.resolve("reads.fastq.gz");
        try (var writer = io.opentools.formats.Compression.createWriter(fastq)) {
            writer.write("@read1 description\nACGT\n+\nIIII\n");
        }
        Path fasta = tempDir.resolve("reads.fa");
        assertEquals(1, FastqFastaConverter.convert(fastq, fasta));
        assertTrue(Files.readString(fasta).contains(">read1 description"));

        Path genome = tempDir.resolve("genome.fa");
        Files.writeString(genome, ">chr1\nAACCGGTT\n");
        Path regions = tempDir.resolve("regions.bed");
        Files.writeString(regions, "chr1\t1\t4\tminus\t.\t-\n");
        Path queries = tempDir.resolve("queries.fa");
        BlastRegionsTool.ExtractionResult extraction = BlastRegionsTool.extractQueries(
                genome, regions, queries, context());
        assertEquals(1, extraction.regions());
        assertEquals("indexed", extraction.storageMode());
        assertTrue(Files.readString(queries).contains("GGT"));
    }

    @Test
    void buildsTrimAlCommandsAndRegistersAllF3Tasks() {
        ToolRequest request = new ToolRequest("alignment.trim", Map.of(
                "method", "GT",
                "gapThreshold", 0.25,
                "minimumCoverage", 0.8,
                "extraArgs", "--threads 2"
        ));
        List<String> command = TrimalTool.command(Path.of("trimal"), request,
                tempDir.resolve("in.fa"), tempDir.resolve("out.fa"));
        assertTrue(command.contains("-gt"));
        assertTrue(command.contains("0.25"));
        assertTrue(command.contains("-cons"));

        List<String> hmmsearchCommand = HmmsearchTool.command(
                Path.of("hmmsearch"),
                new ToolRequest("alignment.hmmer.hmmsearch", Map.of(
                        "evalue", 1e-8,
                        "threads", 3,
                        "extraArgs", "--max"
                )),
                tempDir.resolve("query.hmm"),
                tempDir.resolve("proteins.fa"),
                tempDir.resolve("hits.domtbl")
        );
        assertEquals("hmmsearch", hmmsearchCommand.getFirst());
        assertTrue(hmmsearchCommand.containsAll(List.of("query.hmm", "proteins.fa", "--max")));
        assertEquals("hits.domtbl", hmmsearchCommand.get(hmmsearchCommand.indexOf("--domtblout") + 1));
        assertEquals("OPENTOOLS_HMMSEARCH", ExternalToolSpecs.HMMSEARCH.environmentVariable());

        ToolRegistry registry = new ToolRegistry();
        ExternalTools.register(registry);
        for (String id : List.of(
                "alignment.blast.xml-to-table",
                "alignment.blast.reciprocal",
                "alignment.blast.fastq",
                "alignment.blast.regions",
                "alignment.hmmer.search",
                "alignment.hmmer.hmmsearch",
                "annotation.pfam.import",
                "annotation.eggnog.import",
                "alignment.muscle",
                "alignment.trim"
        )) {
            assertTrue(registry.find(id).isPresent(), id);
        }

        var blastSchema = ToolContracts.parameterSchema(
                registry.find("alignment.blast.local").orElseThrow().descriptor());
        @SuppressWarnings("unchecked")
        var properties = (Map<String, Object>) blastSchema.get("properties");
        var megabyteLimit = (Map<?, ?>) properties.get("cacheMaxMegabytes");
        var entryLimit = (Map<?, ?>) properties.get("cacheMaxEntries");
        assertEquals(10_240, ((Number) megabyteLimit.get("default")).intValue());
        assertEquals(1.0, ((Number) megabyteLimit.get("minimum")).doubleValue());
        assertEquals(128, ((Number) entryLimit.get("default")).intValue());
    }

    @Test
    void copiesAllPressedHmmerDatabaseSidecarsForHmmscan() throws Exception {
        Path source = tempDir.resolve("source.hmm");
        Files.writeString(source, "HMMER3/f\n");
        for (String suffix : List.of(".h3f", ".h3i", ".h3m", ".h3p")) {
            Files.writeString(source.resolveSibling(source.getFileName() + suffix), suffix);
        }
        Path target = tempDir.resolve("work").resolve("profiles.hmm");
        Files.createDirectories(target.getParent());

        HmmerSearchTool.copyPressedDatabase(source, target);

        assertEquals(Files.readString(source), Files.readString(target));
        for (String suffix : List.of(".h3f", ".h3i", ".h3m", ".h3p")) {
            assertEquals(
                    suffix,
                    Files.readString(target.resolveSibling(target.getFileName() + suffix))
            );
        }
    }

    private ToolResult run(String toolId, Map<String, Object> supplied) throws Exception {
        OpenTool tool = tool(toolId);
        Map<String, Object> parameters = new LinkedHashMap<>();
        for (ToolParameter parameter : tool.descriptor().parameters()) {
            if (parameter.defaultValue() != null) {
                parameters.put(parameter.id(), parameter.defaultValue());
            }
        }
        parameters.putAll(supplied);
        ToolRequest request = new ToolRequest(toolId, parameters);
        var validation = tool.validate(request);
        assertTrue(validation.valid(), validation.issues().toString());
        return tool.execute(request, context());
    }

    private ToolExecutionContext context() throws Exception {
        Path output = Files.createDirectories(tempDir.resolve("out"));
        return new ToolExecutionContext("f3-test", tempDir, output, output,
                ignored -> { }, ignored -> { }, new CancellationToken());
    }

    private OpenTool tool(String id) {
        ToolRegistry registry = new ToolRegistry();
        ExternalTools.register(registry);
        return registry.find(id).orElseThrow();
    }
}
