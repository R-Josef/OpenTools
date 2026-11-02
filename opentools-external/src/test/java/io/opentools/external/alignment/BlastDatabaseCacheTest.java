package io.opentools.external.alignment;

import io.opentools.api.tool.CancellationToken;
import io.opentools.api.tool.ToolExecutionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BlastDatabaseCacheTest {
    @TempDir
    Path tempDir;

    @Test
    void reusesCompleteEntryAndInvalidatesKeys() throws Exception {
        Path root = tempDir.resolve("cache");
        String key = BlastDatabaseCache.key("subject-a", "2.16.0", "BLASTN", "nucl");
        AtomicInteger builds = new AtomicInteger();

        BlastDatabaseCache.CachedDatabase first;
        try (var database = BlastDatabaseCache.acquire(
                root, key, "nucl", false, context(), directory -> {
                    builds.incrementAndGet();
                    writeDatabase(directory, "nucl");
                })) {
            first = database;
            assertFalse(database.cacheHit());
            assertFalse(database.databaseReused());
        }
        BlastDatabaseCache.CachedDatabase second;
        try (var database = BlastDatabaseCache.acquire(
                root, key, "nucl", false, context(), directory -> {
                    builds.incrementAndGet();
                    writeDatabase(directory, "nucl");
                })) {
            second = database;
            assertTrue(database.cacheHit());
            assertTrue(database.databaseReused());
            assertTrue(BlastDatabaseCache.isComplete(
                    database.databasePath().getParent(), key, "nucl"));
            assertFalse(BlastDatabaseCache.isComplete(
                    database.databasePath().getParent(), key + "-changed", "nucl"));
        }

        assertEquals(1, builds.get());
        assertEquals(first.cacheKey(), second.cacheKey());
        assertNotEquals(key, BlastDatabaseCache.key(
                "subject-changed", "2.16.0", "BLASTN", "nucl"));
        assertNotEquals(key, BlastDatabaseCache.key(
                "subject-a", "2.17.0", "BLASTN", "nucl"));
        assertNotEquals(key, BlastDatabaseCache.key(
                "subject-a", "2.16.0", "TBLASTX", "nucl"));
        assertNotEquals(key, BlastDatabaseCache.key(
                "subject-a", "2.16.0", "BLASTN", "prot"));
    }

    @Test
    void refreshRebuildsAndFailedBuildDoesNotPublish() throws Exception {
        Path root = tempDir.resolve("refresh-cache");
        String key = BlastDatabaseCache.key("subject-b", "2.16.0", "BLASTP", "prot");
        AtomicInteger builds = new AtomicInteger();
        try (var ignored = BlastDatabaseCache.acquire(
                root, key, "prot", false, context(), directory -> {
                    builds.incrementAndGet();
                    writeDatabase(directory, "prot");
                })) {
            // Close the reader before testing refresh.
        }

        BlastDatabaseCache.CachedDatabase refreshed;
        try (var database = BlastDatabaseCache.acquire(
                root, key, "prot", true, context(), directory -> {
                    builds.incrementAndGet();
                    Files.writeString(directory.resolve("blast-db.psq"), "rebuilt");
                    Files.writeString(directory.resolve("blast-db.pin"), "index");
                })) {
            refreshed = database;
            assertFalse(database.cacheHit());
        }
        assertEquals(2, builds.get());
        assertFalse(refreshed.cacheHit());

        String failedKey = BlastDatabaseCache.key("subject-c", "2.16.0", "BLASTP", "prot");
        assertThrows(IllegalStateException.class, () -> BlastDatabaseCache.acquire(
                root, failedKey, "prot", false, context(), directory -> {
                    Files.writeString(directory.resolve("partial"), "incomplete");
                    throw new IllegalStateException("simulated makeblastdb failure");
                }));
        assertFalse(Files.exists(root.resolve(failedKey)));
    }

    @Test
    void serializesConcurrentBuildsAcrossThreads() throws Exception {
        Path root = tempDir.resolve("concurrent-cache");
        String key = BlastDatabaseCache.key("subject-d", "2.16.0", "BLASTN", "nucl");
        AtomicInteger builds = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await();
                try (var database = BlastDatabaseCache.acquire(
                        root, key, "nucl", false, context(), directory -> {
                            builds.incrementAndGet();
                            Thread.sleep(150);
                            writeDatabase(directory, "nucl");
                        })) {
                    return database.cacheHit();
                }
            });
            var second = executor.submit(() -> {
                start.await();
                try (var database = BlastDatabaseCache.acquire(
                        root, key, "nucl", false, context(), directory -> {
                            builds.incrementAndGet();
                            writeDatabase(directory, "nucl");
                        })) {
                    return database.cacheHit();
                }
            });
            start.countDown();
            boolean firstHit = first.get();
            boolean secondHit = second.get();
            assertEquals(1, builds.get());
            assertTrue(firstHit || secondHit);
        }
    }

    @Test
    void refreshRejectsAnActiveCacheReader() throws Exception {
        Path root = tempDir.resolve("refresh-wait-cache");
        String key = BlastDatabaseCache.key("subject-refresh", "2.16.0", "BLASTN", "nucl");
        try (var ignored = BlastDatabaseCache.acquire(
                root, key, "nucl", false, context(),
                directory -> writeDatabase(directory, "nucl"))) {
            // Seed the entry.
        }
        try (var reader = BlastDatabaseCache.acquire(
                root, key, "nucl", false, context(),
                directory -> writeDatabase(directory, "nucl"))) {
            var exception = assertThrows(
                    io.opentools.api.tool.OpenToolException.class,
                    () -> BlastDatabaseCache.acquire(
                            root, key, "nucl", true, context(),
                            directory -> writeDatabase(directory, "nucl"))
            );
            assertEquals("BLAST_DB_CACHE_BUSY", exception.code());
        }
    }

    @Test
    void evictsLeastRecentlyUsedIdleEntries() throws Exception {
        Path root = tempDir.resolve("lru-cache");
        String keyA = BlastDatabaseCache.key("subject-lru-a", "2.16.0", "BLASTN", "nucl");
        String keyB = BlastDatabaseCache.key("subject-lru-b", "2.16.0", "BLASTN", "nucl");
        String keyC = BlastDatabaseCache.key("subject-lru-c", "2.16.0", "BLASTN", "nucl");
        create(root, keyA, "nucl");
        create(root, keyB, "nucl");
        create(root, keyC, "nucl");

        long now = System.currentTimeMillis();
        setLastUsed(root.resolve(keyA), now - 2_000);
        setLastUsed(root.resolve(keyB), now - 3_000);
        setLastUsed(root.resolve(keyC), now - 1_000);
        long sizeA = directorySize(root.resolve(keyA));
        long sizeB = directorySize(root.resolve(keyB));
        long sizeC = directorySize(root.resolve(keyC));

        var result = BlastDatabaseCache.maintain(
                root,
                new BlastDatabaseCache.Limits(sizeA + sizeC, 0),
                context()
        );

        assertFalse(Files.exists(root.resolve(keyB)));
        assertTrue(BlastDatabaseCache.isComplete(root.resolve(keyA), keyA, "nucl"));
        assertTrue(BlastDatabaseCache.isComplete(root.resolve(keyC), keyC, "nucl"));
        assertEquals(1, result.evictedEntries());
        assertTrue(result.capacitySatisfied());
        assertTrue(result.evictedBytes() >= sizeB);
    }

    @Test
    void activeLeasePreventsEviction() throws Exception {
        Path root = tempDir.resolve("leased-cache");
        String activeKey = BlastDatabaseCache.key("subject-active", "2.16.0", "BLASTN", "nucl");
        String idleKey = BlastDatabaseCache.key("subject-idle", "2.16.0", "BLASTN", "nucl");
        create(root, idleKey, "nucl");

        try (var active = BlastDatabaseCache.acquire(
                root, activeKey, "nucl", false, context(),
                directory -> writeDatabase(directory, "nucl"))) {
            var result = BlastDatabaseCache.maintain(
                    root,
                    new BlastDatabaseCache.Limits(1, 0),
                    context()
            );
            assertEquals(1, result.evictedEntries());
            assertFalse(result.capacitySatisfied());
            assertTrue(BlastDatabaseCache.isComplete(
                    root.resolve(activeKey), activeKey, "nucl"));
            assertFalse(Files.exists(root.resolve(idleKey)));
        }
    }

    @Test
    void enforcesEntryCountAndDeferredLimitAfterUse() throws Exception {
        Path root = tempDir.resolve("entry-limit-cache");
        String keyA = BlastDatabaseCache.key("subject-count-a", "2.16.0", "BLASTN", "nucl");
        String keyB = BlastDatabaseCache.key("subject-count-b", "2.16.0", "BLASTN", "nucl");
        String keyC = BlastDatabaseCache.key("subject-count-c", "2.16.0", "BLASTN", "nucl");
        create(root, keyA, "nucl");
        create(root, keyB, "nucl");
        create(root, keyC, "nucl");
        setLastUsed(root.resolve(keyA), 1);
        setLastUsed(root.resolve(keyB), 2);
        setLastUsed(root.resolve(keyC), 3);

        var countResult = BlastDatabaseCache.maintain(
                root,
                new BlastDatabaseCache.Limits(0, 2),
                context()
        );
        assertEquals(1, countResult.evictedEntries());
        assertTrue(countResult.capacitySatisfied());
        assertFalse(Files.exists(root.resolve(keyA)));

        String oversized = BlastDatabaseCache.key(
                "subject-deferred", "2.16.0", "BLASTN", "nucl");
        var database = BlastDatabaseCache.acquire(
                root, oversized, "nucl", false, context(),
                directory -> writeDatabase(directory, "nucl"));
        BlastDatabaseCache.release(
                database,
                new BlastDatabaseCache.Limits(1, 0),
                context()
        );
        assertFalse(Files.exists(root.resolve(oversized)));
    }

    @Test
    void maintenanceRemovesAnInterruptedEvictionDirectory() throws Exception {
        Path root = tempDir.resolve("orphan-eviction-cache");
        String key = BlastDatabaseCache.key(
                "subject-orphan", "2.16.0", "BLASTN", "nucl");
        create(root, key, "nucl");
        Path orphan = root.resolve(
                key + ".evict-11111111-1111-1111-1111-111111111111");
        Files.createDirectories(orphan);
        Files.writeString(orphan.resolve("partial"), "partial eviction");

        BlastDatabaseCache.maintain(
                root,
                new BlastDatabaseCache.Limits(0, 10),
                context()
        );

        assertFalse(Files.exists(orphan));
        assertTrue(BlastDatabaseCache.isComplete(root.resolve(key), key, "nucl"));
    }

    @Test
    void copiesCachedFilesWhenCachePathContainsWhitespace() throws Exception {
        Path root = tempDir.resolve("cache with spaces");
        String key = BlastDatabaseCache.key("subject-e", "2.16.0", "BLASTN", "nucl");
        Path runtime = tempDir.resolve("runtime");
        Path prepared;
        try (var database = BlastDatabaseCache.acquire(
                root, key, "nucl", false, context(),
                directory -> writeDatabase(directory, "nucl"))) {
            prepared = BlastDatabaseCache.prepareForRuntime(database, runtime, "nucl");
        }

        assertEquals(runtime.resolve("blast-db"), prepared);
        assertTrue(Files.isRegularFile(runtime.resolve("blast-db.nsq")));
        assertTrue(Files.isRegularFile(runtime.resolve("blast-db.nin")));
    }

    @Test
    void recoversBackupLeftByInterruptedRefresh() throws Exception {
        Path root = tempDir.resolve("backup-cache");
        String key = BlastDatabaseCache.key("subject-f", "2.16.0", "BLASTN", "nucl");
        create(root, key, "nucl");
        Path target = root.resolve(key);
        Files.move(target, root.resolve(key + ".backup-interrupted"));
        AtomicInteger builds = new AtomicInteger();

        try (var recovered = BlastDatabaseCache.acquire(
                root, key, "nucl", false, context(), directory -> {
                    builds.incrementAndGet();
                    writeDatabase(directory, "nucl");
                })) {
            assertEquals(0, builds.get());
            assertTrue(recovered.cacheHit());
            assertTrue(Files.isDirectory(target));
        }
    }

    private void create(Path root, String key, String type) throws Exception {
        try (var ignored = BlastDatabaseCache.acquire(
                root, key, type, false, context(),
                directory -> writeDatabase(directory, type))) {
            // Seed and close an entry.
        }
    }

    private void writeDatabase(Path directory, String type) throws Exception {
        Files.writeString(directory.resolve(
                "blast-db." + ("prot".equals(type) ? "psq" : "nsq")), "sequence");
        Files.writeString(directory.resolve(
                "blast-db." + ("prot".equals(type) ? "pin" : "nin")), "index");
    }

    private static void setLastUsed(Path entry, long millis) throws Exception {
        Files.writeString(entry.resolve(".last-used"), Long.toString(millis));
    }

    private static long directorySize(Path root) throws Exception {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                    })
                    .sum();
        }
    }

    private ToolExecutionContext context() {
        Path run = tempDir.resolve("run-" + System.nanoTime());
        return new ToolExecutionContext(
                "blast-cache-test",
                tempDir,
                run,
                run.resolve("outputs"),
                ignored -> { },
                ignored -> { },
                new CancellationToken()
        );
    }
}
