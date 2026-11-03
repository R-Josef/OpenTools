package io.opentools.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class WorkflowWatchServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void triggersOnceForAMatchingStableFile() throws Exception {
        Path directory = Files.createDirectories(tempDir.resolve("incoming"));
        CountDownLatch handled = new CountDownLatch(1);
        AtomicReference<Path> seen = new AtomicReference<>();
        WorkflowWatchService watcher = new WorkflowWatchService(
                directory, "*.fa", Duration.ofMillis(150), 1);
        Thread thread = Thread.ofVirtual().start(() -> {
            try {
                watcher.watch(file -> {
                    seen.set(file);
                    handled.countDown();
                });
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
        Thread.sleep(300);
        Path file = directory.resolve("sample.fa");
        Files.writeString(file, ">seq\nACGT\n");
        assertTrue(handled.await(8, TimeUnit.SECONDS));
        thread.join(2_000);
        assertEquals(file.toAbsolutePath().normalize(), seen.get());
    }

    @Test
    void ignoresNonMatchingFiles() throws Exception {
        Path directory = Files.createDirectories(tempDir.resolve("incoming-ignore"));
        CountDownLatch handled = new CountDownLatch(1);
        WorkflowWatchService watcher = new WorkflowWatchService(
                directory, "*.fa", Duration.ofMillis(100), 1);
        Thread thread = Thread.ofVirtual().start(() -> {
            try {
                watcher.watch(file -> handled.countDown());
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
        Thread.sleep(300);
        Files.writeString(directory.resolve("notes.txt"), "ignored");
        Files.writeString(directory.resolve("sample.fa"), ">seq\nACGT\n");
        assertTrue(handled.await(8, TimeUnit.SECONDS));
        thread.join(2_000);
    }
}
