package io.opentools.analysis.enrichment;

import io.opentools.api.tool.OpenToolException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ResourcePacksTest {
    @TempDir
    Path tempDir;

    @Test
    void importsActivatesAndRollsBackGoPacks() throws Exception {
        Path obo = writeObo("go.obo");
        Path annotations = writeAnnotations("go.tsv", "gene1\tGO:0001;GO:0002\ngene2\tGO:0001\n");
        ResourcePacks packs = new ResourcePacks(tempDir.resolve("resources"));

        ResourcePacks.Pack first = packs.importGo(obo, annotations, "go-test", "Test GO", "1.0", true);
        assertEquals(ResourcePacks.Kind.GO, first.kind());
        assertTrue(first.active());
        assertEquals(2L, ((Number) first.counts().get("terms")).longValue());
        assertEquals(2L, ((Number) first.counts().get("genes")).longValue());
        assertEquals(3L, ((Number) first.counts().get("annotations")).longValue());
        assertTrue(Files.isRegularFile(first.primary()));
        assertTrue(Files.isRegularFile(first.secondary()));
        assertTrue(Files.isRegularFile(first.directory().resolve("pack.json")));

        ResourcePacks.Pack second = packs.importGo(obo, null, "go-obo-only", "OBO only", "2.0", true);
        assertTrue(second.active());
        assertFalse(packs.find("go-test").orElseThrow().active());
        assertEquals(2L, ((Number) second.counts().get("terms")).longValue());

        packs.activate("go-test");
        assertTrue(packs.find("go-test").orElseThrow().active());
        assertFalse(packs.find("go-obo-only").orElseThrow().active());
        assertEquals("go-test", packs.active(ResourcePacks.Kind.GO).orElseThrow().id());
    }

    @Test
    void importsKeggMappingAndNames() throws Exception {
        Path mapping = writeAnnotations("kegg.tsv", "gene1\tmap00010,map00020\ngene2\tmap00010\n");
        Path names = writeAnnotations("names.tsv", "map00010\tGlycolysis\nmap00020\tTCA cycle\n");
        ResourcePacks packs = new ResourcePacks(tempDir.resolve("resources"));
        ResourcePacks.Pack pack = packs.importKegg(mapping, names, "kegg-test", "KEGG", "1.0", true);

        assertEquals(ResourcePacks.Kind.KEGG, pack.kind());
        assertEquals(2L, ((Number) pack.counts().get("genes")).longValue());
        assertEquals(2L, ((Number) pack.counts().get("pathways")).longValue());
        assertEquals(3L, ((Number) pack.counts().get("pairs")).longValue());
        assertEquals(2L, ((Number) pack.counts().get("pathwayNames")).longValue());
        assertTrue(pack.active());
        assertEquals("kegg-test", packs.active(ResourcePacks.Kind.KEGG).orElseThrow().id());
    }

    @Test
    void rejectsEmptyOboAndUnsafeIds() throws Exception {
        Path empty = tempDir.resolve("empty.obo");
        Files.writeString(empty, "format-version: 1.4\n");
        Path obo = writeObo("go.obo");
        ResourcePacks packs = new ResourcePacks(tempDir.resolve("resources"));

        assertThrows(OpenToolException.class, () -> packs.importGo(empty, null, "empty", "", "", true));
        assertThrows(OpenToolException.class, () -> packs.importGo(obo, null, "../escape", "", "", true));
    }

    private Path writeObo(String name) throws Exception {
        Path path = tempDir.resolve(name);
        Files.writeString(path, """
                format-version: 1.4
                [Term]
                id: GO:0001
                name: alpha
                [Term]
                id: GO:0002
                name: beta
                """);
        return path;
    }

    private Path writeAnnotations(String name, String content) throws Exception {
        Path path = tempDir.resolve(name);
        Files.writeString(path, content);
        return path;
    }
}
