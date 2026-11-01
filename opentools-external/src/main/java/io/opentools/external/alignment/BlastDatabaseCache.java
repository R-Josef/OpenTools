package io.opentools.external.alignment;

import io.opentools.api.tool.CancellationToken;
import io.opentools.api.tool.OpenToolException;
import io.opentools.api.tool.TaskContext;
import io.opentools.external.process.TemporaryFiles;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Stream;

final class BlastDatabaseCache {
    static final String DATABASE_BASE = "blast-db";
    static final int DEFAULT_MAX_MEGABYTES = 10 * 1024;
    static final int DEFAULT_MAX_ENTRIES = 128;
    private static final String MANIFEST = ".opentools-complete";
    private static final String LAST_USED = ".last-used";
    private static final String LEASE_SUFFIX = ".lease";
    private static final String MAINTENANCE_LOCK = "cache-maintenance.lock";
    private static final String FORMAT_VERSION = "1";

    private BlastDatabaseCache() {
    }

    static String key(
            String subjectSha256,
            String makeblastdbVersion,
            String program,
            String databaseType
    ) {
        return sha256(String.join("\n",
                "opentools-blast-cache",
                FORMAT_VERSION,
                subjectSha256,
                normalize(makeblastdbVersion),
                normalize(program),
                normalize(databaseType)
        ));
    }

    static CachedDatabase acquire(
            Path cacheRoot,
            String key,
            String databaseType,
            boolean refresh,
            TaskContext context,
            Builder builder
    ) throws Exception {
        return acquire(
                cacheRoot, key, databaseType, refresh, Limits.unlimited(),
                false, context, builder);
    }

    static CachedDatabase acquire(
            Path cacheRoot,
            String key,
            String databaseType,
            boolean refresh,
            long maxBytes,
            TaskContext context,
            Builder builder
    ) throws Exception {
        return acquire(
                cacheRoot,
                key,
                databaseType,
                refresh,
                new Limits(maxBytes, 0),
                false,
                context,
                builder
        );
    }

    static CachedDatabase acquire(
            Path cacheRoot,
            String key,
            String databaseType,
            boolean refresh,
            Limits limits,
            boolean maintainOnHit,
            TaskContext context,
            Builder builder
    ) throws Exception {
        Path root = cacheRoot.toAbsolutePath().normalize();
        Files.createDirectories(root);
        Path target = root.resolve(key);
        Path lockPath = root.resolve(key + ".lock");
        try (FileChannel channel = FileChannel.open(
                lockPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE
        ); FileLock ignored = awaitLock(channel, context.cancellationToken())) {
            removeStaleBuilds(root, key);
            recoverBackups(root, key, target);
            boolean complete = isComplete(target, key, databaseType);
            if (!refresh && complete) {
                touchSafely(root, key, context);
                CacheLease lease = CacheLease.open(root, key);
                MaintenanceStats maintenance = maintainOnHit
                        ? maintainSafely(root, key, limits, context)
                        : MaintenanceStats.notRun();
                context.log("BLAST database cache hit: " + key);
                return new CachedDatabase(
                        target.resolve(DATABASE_BASE),
                        root,
                        key,
                        true,
                        true,
                        lease,
                        maintenance
                );
            }
            if (hasActiveLeases(root, key)) {
                throw new OpenToolException(
                        "BLAST_DB_CACHE_BUSY",
                        "The matching BLAST database cache entry is currently in use.",
                        "Wait for the active BLAST run to finish, then retry refreshDatabase."
                );
            }

            Path staging = root.resolve(key + ".build-" + UUID.randomUUID());
            Files.createDirectories(staging);
            boolean published = false;
            try {
                builder.build(staging);
                requireDatabaseFiles(staging, databaseType);
                writeManifest(staging, key, databaseType);
                Path backup = null;
                if (Files.exists(target)) {
                    backup = root.resolve(key + ".backup-" + UUID.randomUUID());
                    moveDirectory(target, backup);
                }
                try {
                    moveDirectory(staging, target);
                    published = true;
                } catch (Exception ex) {
                    if (backup != null && !Files.exists(target)) {
                        try {
                            moveDirectory(backup, target);
                        } catch (Exception restoreFailure) {
                            ex.addSuppressed(restoreFailure);
                        }
                    }
                    throw ex;
                } finally {
                    if (published && backup != null) {
                        TemporaryFiles.deleteRecursively(backup);
                    }
                }
                touchSafely(root, key, context);
                CacheLease lease = CacheLease.open(root, key);
                MaintenanceStats maintenance = maintainSafely(
                        root, key, limits, context);
                context.log("Published BLAST database cache entry: " + key);
                return new CachedDatabase(
                        target.resolve(DATABASE_BASE),
                        root,
                        key,
                        false,
                        false,
                        lease,
                        maintenance
                );
            } finally {
                if (!published) {
                    TemporaryFiles.deleteRecursively(staging);
                }
            }
        }
    }

    static Path prepareForRuntime(
            CachedDatabase database,
            Path runtimeDirectory,
            String databaseType
    ) throws IOException {
        Path source = database.databasePath();
        if (!source.toString().chars().anyMatch(Character::isWhitespace)) {
            return source;
        }
        Path sourceDirectory = source.getParent();
        Path target = runtimeDirectory.resolve(DATABASE_BASE);
        Files.createDirectories(runtimeDirectory);
        for (Path file : databaseFiles(sourceDirectory, databaseType)) {
            String name = file.getFileName().toString();
            Path destination = runtimeDirectory.resolve(name);
            try {
                Files.createLink(destination, file);
            } catch (Exception ignored) {
                Files.copy(file, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return target;
    }

    static boolean isComplete(Path directory, String key, String databaseType) {
        Path manifest = directory.resolve(MANIFEST);
        if (!Files.isRegularFile(manifest)) {
            return false;
        }
        try {
            Properties properties = loadManifest(manifest);
            return FORMAT_VERSION.equals(properties.getProperty("format"))
                    && key.equals(properties.getProperty("key"))
                    && databaseType.equals(properties.getProperty("databaseType"))
                    && hasRequiredDatabaseFile(directory, databaseType);
        } catch (IOException ex) {
            return false;
        }
    }

    private static void writeManifest(Path directory, String key, String databaseType)
            throws IOException {
        Properties properties = new Properties();
        properties.setProperty("format", FORMAT_VERSION);
        properties.setProperty("key", key);
        properties.setProperty("databaseType", databaseType);
        try (var output = Files.newOutputStream(directory.resolve(MANIFEST))) {
            properties.store(output, "OpenTools BLAST database cache");
        }
    }

    private static Properties loadManifest(Path manifest) throws IOException {
        Properties properties = new Properties();
        try (var input = Files.newInputStream(manifest)) {
            properties.load(input);
        }
        return properties;
    }

    private static void requireDatabaseFiles(Path directory, String databaseType)
            throws IOException {
        if (!hasRequiredDatabaseFile(directory, databaseType)) {
            throw new OpenToolException(
                    "BLAST_DB_CACHE_INVALID",
                    "makeblastdb completed without the expected " + databaseType + " database files.",
                    "Check the BLAST+ installation and cache directory permissions."
            );
        }
    }

    private static boolean hasRequiredDatabaseFile(Path directory, String databaseType) {
        String extension = "prot".equals(databaseType) ? ".psq" : ".nsq";
        return Files.isRegularFile(directory.resolve(DATABASE_BASE + extension));
    }

    private static List<Path> databaseFiles(Path directory, String databaseType)
            throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            List<Path> result = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith(DATABASE_BASE + "."))
                    .toList();
            if (result.stream().noneMatch(path -> path.getFileName().toString()
                    .endsWith("prot".equals(databaseType) ? ".psq" : ".nsq"))) {
                throw new OpenToolException(
                        "BLAST_DB_CACHE_INVALID",
                        "Cached BLAST database files are incomplete.",
                        "Enable refreshDatabase to rebuild the cache entry."
                );
            }
            return result;
        }
    }

    private static void removeStaleBuilds(Path root, String key) throws IOException {
        String prefix = key + ".build-";
        try (Stream<Path> entries = Files.list(root)) {
            for (Path entry : entries.toList()) {
                if (entry.getFileName().toString().startsWith(prefix)) {
                    TemporaryFiles.deleteRecursively(entry);
                }
            }
        }
    }

    private static void recoverBackups(Path root, String key, Path target) throws IOException {
        String prefix = key + ".backup-";
        try (Stream<Path> entries = Files.list(root)) {
            List<Path> backups = entries
                    .filter(path -> path.getFileName().toString().startsWith(prefix))
                    .sorted()
                    .toList();
            if (backups.isEmpty()) {
                return;
            }
            if (!Files.exists(target)) {
                moveDirectory(backups.getFirst(), target);
                backups = backups.subList(1, backups.size());
            }
            for (Path backup : backups) {
                TemporaryFiles.deleteRecursively(backup);
            }
        }
    }

    private static void touch(Path root, String key) throws IOException {
        Path marker = root.resolve(key).resolve(LAST_USED);
        long previous = readLastUsed(root.resolve(key), key);
        long now = Math.max(System.currentTimeMillis(), previous + 1);
        Files.writeString(
                marker,
                Long.toString(now),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        );
    }

    private static void touchSafely(Path root, String key, TaskContext context) {
        try {
            touch(root, key);
        } catch (IOException ex) {
            context.log("Could not update BLAST cache LRU metadata: "
                    + ex.getMessage());
        }
    }

    private static long readLastUsed(Path entry, String key) {
        Path marker = entry.resolve(LAST_USED);
        if (Files.isRegularFile(marker)) {
            try {
                return Long.parseLong(Files.readString(marker).strip());
            } catch (IOException | NumberFormatException ignored) {
                // Fall back to directory metadata after an interrupted marker write.
            }
        }
        try {
            return Files.getLastModifiedTime(entry).toMillis();
        } catch (IOException ignored) {
            return 0;
        }
    }

    private static MaintenanceStats maintainSafely(
            Path root,
            String currentKey,
            Limits limits,
            TaskContext context
    ) {
        if (!limits.enabled()) {
            return MaintenanceStats.notRun();
        }
        try {
            return enforceCapacity(root, currentKey, limits, context);
        } catch (Exception ex) {
            context.log("BLAST cache maintenance failed safely: " + ex.getMessage());
            return new MaintenanceStats(true, 0, 0, false, -1, -1);
        }
    }

    static MaintenanceStats maintain(
            Path root,
            Limits limits,
            TaskContext context
    ) throws Exception {
        return enforceCapacity(root, null, limits, context);
    }

    static void release(
            CachedDatabase database,
            Limits limits,
            TaskContext context
    ) {
        if (database == null) {
            return;
        }
        boolean needsMaintenance = !database.maintenance().ran()
                || !database.maintenance().capacitySatisfied();
        try {
            database.close();
        } catch (IOException ex) {
            context.log("Could not release the BLAST cache lease: " + ex.getMessage());
        }
        if (!needsMaintenance) {
            return;
        }
        try {
            MaintenanceStats stats = enforceCapacity(root(database), null, limits, context);
            if (stats.evictedEntries() > 0) {
                context.log("Evicted " + stats.evictedEntries()
                        + " BLAST cache entries after active use ("
                        + stats.evictedBytes() + " bytes).");
            }
        } catch (Exception ex) {
            context.log("Deferred BLAST cache maintenance failed safely: "
                    + ex.getMessage());
        }
    }

    private static Path root(CachedDatabase database) {
        return database.cacheRoot();
    }

    private static MaintenanceStats enforceCapacity(
            Path root,
            String currentKey,
            Limits limits,
            TaskContext context
    ) throws Exception {
        Path maintenanceLock = root.resolve(MAINTENANCE_LOCK);
        try (FileChannel channel = FileChannel.open(
                maintenanceLock,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE
        ); FileLock ignored = awaitLock(channel, context.cancellationToken())) {
            removeOrphanedEvictions(root);
            List<CacheEntry> entries = scanEntries(root);
            long totalBytes = entries.stream().mapToLong(CacheEntry::bytes).sum();
            long totalEntries = entries.size();
            if (!overLimit(totalEntries, totalBytes, limits)) {
                return new MaintenanceStats(
                        true, 0, 0, true, totalEntries, totalBytes);
            }

            long evictedEntries = 0;
            long evictedBytes = 0;
            entries.sort(Comparator.comparingLong(CacheEntry::lastUsed)
                    .thenComparing(CacheEntry::key));
            for (CacheEntry entry : entries) {
                if (!overLimit(totalEntries, totalBytes, limits)) {
                    break;
                }
                if (entry.key().equals(currentKey)) {
                    continue;
                }
                if (tryEvict(root, entry)) {
                    totalEntries--;
                    totalBytes -= entry.bytes();
                    evictedEntries++;
                    evictedBytes += entry.bytes();
                }
            }
            boolean satisfied = !overLimit(totalEntries, totalBytes, limits);
            context.log("BLAST cache maintenance evicted " + evictedEntries
                    + " LRU entr" + (evictedEntries == 1 ? "y" : "ies") + " ("
                    + evictedBytes + " bytes); capacity satisfied=" + satisfied);
            return new MaintenanceStats(
                    true,
                    evictedEntries,
                    evictedBytes,
                    satisfied,
                    totalEntries,
                    totalBytes
            );
        }
    }

    private static void removeOrphanedEvictions(Path root) throws IOException {
        try (Stream<Path> paths = Files.list(root)) {
            for (Path path : paths.toList()) {
                if (Files.isDirectory(path)
                        && path.getFileName().toString()
                        .matches("[0-9a-f]{64}\\.evict-[0-9a-fA-F-]+")) {
                    TemporaryFiles.deleteRecursively(path);
                }
            }
        }
    }

    private static boolean overLimit(long entries, long bytes, Limits limits) {
        return (limits.maxEntries() > 0 && entries > limits.maxEntries())
                || (limits.maxBytes() > 0 && bytes > limits.maxBytes());
    }

    private static List<CacheEntry> scanEntries(Path root) throws IOException {
        List<CacheEntry> entries = new ArrayList<>();
        try (Stream<Path> paths = Files.list(root)) {
            for (Path path : paths.toList()) {
                String name = path.getFileName().toString();
                if (!Files.isDirectory(path)
                        || !name.matches("[0-9a-f]{64}")
                        || !Files.isRegularFile(path.resolve(MANIFEST))) {
                    continue;
                }
                try {
                    Properties manifest = loadManifest(path.resolve(MANIFEST));
                    String type = manifest.getProperty("databaseType");
                    if (!FORMAT_VERSION.equals(manifest.getProperty("format"))
                            || !name.equals(manifest.getProperty("key"))
                            || type == null
                            || !hasRequiredDatabaseFile(path, type)) {
                        continue;
                    }
                    entries.add(new CacheEntry(
                            name,
                            path,
                            directorySize(path),
                            readLastUsed(path, name)
                    ));
                } catch (IOException ignored) {
                    // An unreadable entry is not a valid eviction candidate.
                }
            }
        }
        return entries;
    }

    private static long directorySize(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            return paths.filter(Files::isRegularFile)
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException ex) {
                            throw new DirectorySizeException(ex);
                        }
                    })
                    .sum();
        } catch (DirectorySizeException ex) {
            throw ex.ioException;
        }
    }

    private static boolean tryEvict(Path root, CacheEntry entry) throws IOException {
        Path lockPath = root.resolve(entry.key() + ".lock");
        try (FileChannel channel = FileChannel.open(
                lockPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE
        )) {
            FileLock lock;
            try {
                lock = channel.tryLock();
            } catch (OverlappingFileLockException ex) {
                return false;
            }
            if (lock == null) {
                return false;
            }
            try (lock) {
                if (hasActiveLeases(root, entry.key())) {
                    return false;
                }
                Path evicted = root.resolve(
                        entry.key() + ".evict-" + UUID.randomUUID());
                moveDirectory(entry.path(), evicted);
                TemporaryFiles.deleteRecursively(evicted);
                return true;
            }
        }
    }

    private static boolean hasActiveLeases(Path root, String key) throws IOException {
        String prefix = key + ".use-";
        try (Stream<Path> paths = Files.list(root)) {
            for (Path lease : paths
                    .filter(path -> path.getFileName().toString().startsWith(prefix))
                    .filter(path -> path.getFileName().toString().endsWith(LEASE_SUFFIX))
                    .toList()) {
                boolean active = false;
                try (FileChannel channel = FileChannel.open(
                        lease,
                        StandardOpenOption.WRITE
                )) {
                    FileLock lock;
                    try {
                        lock = channel.tryLock();
                    } catch (OverlappingFileLockException ex) {
                        active = true;
                        lock = null;
                    }
                    if (lock == null) {
                        active = true;
                    } else {
                        lock.close();
                    }
                } catch (IOException ex) {
                    active = true;
                }
                if (active) {
                    return true;
                }
                Files.deleteIfExists(lease);
            }
        }
        return false;
    }

    private static FileLock awaitLock(
            FileChannel channel,
            CancellationToken cancellationToken
    ) throws IOException, InterruptedException {
        while (true) {
            try {
                FileLock lock = channel.tryLock();
                if (lock != null) {
                    return lock;
                }
            } catch (OverlappingFileLockException ignored) {
                // Another thread in this JVM owns the same cache lock.
            }
            if (cancellationToken.isCancelled()) {
                throw new OpenToolException(
                        "BLAST_DB_CACHE_CANCELLED",
                        "Cancelled while waiting for the BLAST database cache lock.",
                        "Retry the task."
                );
            }
            Thread.sleep(200);
        }
    }

    private static void moveDirectory(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, target);
        }
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip();
    }

    @FunctionalInterface
    interface Builder {
        void build(Path directory) throws Exception;
    }

    record Limits(long maxBytes, int maxEntries) {
        static Limits unlimited() {
            return new Limits(0, 0);
        }

        boolean enabled() {
            return maxBytes > 0 || maxEntries > 0;
        }
    }

    record MaintenanceStats(
            boolean ran,
            long evictedEntries,
            long evictedBytes,
            boolean capacitySatisfied,
            long remainingEntries,
            long remainingBytes
    ) {
        static MaintenanceStats notRun() {
            return new MaintenanceStats(false, 0, 0, true, -1, -1);
        }
    }

    record CachedDatabase(
            Path databasePath,
            Path cacheRoot,
            String cacheKey,
            boolean cacheHit,
            boolean databaseReused,
            CacheLease lease,
            MaintenanceStats maintenance
    ) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            lease.close();
        }
    }

    static final class CacheLease implements AutoCloseable {
        private final Path path;
        private final FileChannel channel;
        private final FileLock lock;
        private boolean closed;

        private CacheLease(Path path, FileChannel channel, FileLock lock) {
            this.path = path;
            this.channel = channel;
            this.lock = lock;
        }

        static CacheLease open(Path root, String key) throws IOException {
            Path path = root.resolve(key + ".use-" + UUID.randomUUID() + LEASE_SUFFIX);
            FileChannel channel = FileChannel.open(
                    path,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE
            );
            try {
                return new CacheLease(path, channel, channel.lock());
            } catch (IOException ex) {
                try {
                    channel.close();
                } catch (IOException closeFailure) {
                    ex.addSuppressed(closeFailure);
                }
                Files.deleteIfExists(path);
                throw ex;
            }
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }
            closed = true;
            IOException failure = null;
            try {
                lock.close();
            } catch (IOException ex) {
                failure = ex;
            }
            try {
                channel.close();
            } catch (IOException ex) {
                if (failure == null) {
                    failure = ex;
                } else {
                    failure.addSuppressed(ex);
                }
            }
            try {
                Files.deleteIfExists(path);
            } catch (IOException ex) {
                if (failure == null) {
                    failure = ex;
                } else {
                    failure.addSuppressed(ex);
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }

    private record CacheEntry(
            String key,
            Path path,
            long bytes,
            long lastUsed
    ) {
    }

    private static final class DirectorySizeException extends RuntimeException {
        private final IOException ioException;

        private DirectorySizeException(IOException ioException) {
            super(ioException);
            this.ioException = ioException;
        }
    }
}
