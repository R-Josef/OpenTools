package io.opentools.ui;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentools.core.i18n.LanguageManager;
import io.opentools.core.job.JobRecord;
import io.opentools.core.job.JobStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SangerAssemblyViewerPanelTest {
    @TempDir
    Path tempDir;

    @Test
    void showsReferenceReadsAndAssemblyWithoutAb1() throws Exception {
        String sequence = "ACGT".repeat(30);
        Path assembly = tempDir.resolve("assembly.fa");
        Path corrected = tempDir.resolve("corrected.fa");
        Path reads = tempDir.resolve("reads.tsv");
        Files.writeString(assembly, ">assembly\n" + sequence + "\n");
        Files.writeString(corrected, ">reference\n" + sequence + "\n");
        Files.writeString(reads, "read\toriginal_length\ttrim_start\ttrim_end\ttrimmed_length"
                + "\tmean_quality\treference_start\treference_end\tcigar\torientation\tidentity\n"
                + "r1\t120\t0\t120\t120\t40\t0\t60\t60M\tforward\t1.00\n"
                + "r2\t120\t0\t120\t120\t40\t60\t120\t60M\treverse\t1.00\n");
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        var artifacts = root.putArray("artifacts");
        artifacts.addObject().put("title", "Assembled contigs").put("path", assembly.toString());
        artifacts.addObject().put("title", "Reference-anchored consensus").put("path", corrected.toString());
        artifacts.addObject().put("title", "Read and placement summary").put("path", reads.toString());
        JobRecord job = new JobRecord(
                "run", "sanger.assemble", "1.3.0", JobStatus.SUCCEEDED,
                Instant.now(), Instant.now(), Instant.now(),
                mapper.writeValueAsString(java.util.Map.of(
                        "reference", corrected.toString(), "reads", java.util.List.of())),
                null, null, null, tempDir);
        LanguageManager language = new LanguageManager(Path.of("..").toAbsolutePath(), "en_US");
        AtomicReference<SangerAssemblyViewerPanel> reference = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                reference.set(new SangerAssemblyViewerPanel(language, root, job));
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
        assertEquals(2, reference.get().readCount());
        assertEquals(0, reference.get().selectedRead());
    }

    @Test
    void paintsInsertionConsensusOnSharedColumns() throws Exception {
        String reference = "ACGT".repeat(30);
        String expanded = reference.substring(0, 60) + "T" + reference.substring(60);
        Path referenceFile = tempDir.resolve("reference.fa");
        Path assembly = tempDir.resolve("assembly.fa");
        Path corrected = tempDir.resolve("corrected.fa");
        Path finalConsensus = tempDir.resolve("consensus_with_indels.fa");
        Path indels = tempDir.resolve("indels.tsv");
        Path reads = tempDir.resolve("reads.tsv");
        Files.writeString(referenceFile, ">reference\n" + reference + "\n");
        Files.writeString(assembly, ">assembly\nTTTT" + reference + "GGGG\n");
        Files.writeString(corrected, ">reference\n" + reference + "\n");
        Files.writeString(finalConsensus, ">reference_consensus_with_indels\n" + expanded + "\n");
        Files.writeString(indels, "position\ttype\tsequence\tlength\tsupport\treads\n"
                + "61\tINSERTION\tT\t1\t1\tr1\n");
        Files.writeString(reads, "read\toriginal_length\ttrim_start\ttrim_end\ttrimmed_length"
                + "\tmean_quality\treference_start\treference_end\tcigar\torientation\tidentity\n"
                + "r1\t121\t0\t121\t121\t40\t0\t120\t60M1I60M\tforward\t1.00\n");
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        var artifacts = root.putArray("artifacts");
        artifacts.addObject().put("title", "Assembled contigs").put("path", assembly.toString());
        artifacts.addObject().put("title", "Reference-anchored consensus").put("path", corrected.toString());
        artifacts.addObject().put("title", "Consensus with indels").put("path", finalConsensus.toString());
        artifacts.addObject().put("title", "Consensus indels").put("path", indels.toString());
        artifacts.addObject().put("title", "Read and placement summary").put("path", reads.toString());
        JobRecord job = new JobRecord(
                "run", "sanger.assemble", "1.3.0", JobStatus.SUCCEEDED,
                Instant.now(), Instant.now(), Instant.now(),
                mapper.writeValueAsString(java.util.Map.of(
                        "reference", referenceFile.toString(),
                        "reads", java.util.List.of(),
                        "minIndelSupport", 1)),
                null, null, null, tempDir);
        LanguageManager language = new LanguageManager(Path.of("..").toAbsolutePath(), "en_US");
        AtomicReference<BufferedImage> image = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                SangerAssemblyViewerPanel panel = new SangerAssemblyViewerPanel(language, root, job);
                panel.setSize(1800, 420);
                BufferedImage buffer = new BufferedImage(1800, 420, BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = buffer.createGraphics();
                try {
                    panel.paint(graphics);
                } finally {
                    graphics.dispose();
                }
                image.set(buffer);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
        long painted = 0;
        for (int y = 0; y < image.get().getHeight(); y++) {
            for (int x = 0; x < image.get().getWidth(); x++) {
                if ((image.get().getRGB(x, y) & 0xFFFFFF) != 0xFFFFFF) painted++;
            }
        }
        assertTrue(painted > 1000);
    }
}
