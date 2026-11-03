package io.opentools.analysis.enrichment;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.opentools.api.tool.OpenToolException;
import io.opentools.core.util.Hashing;
import io.opentools.formats.Compression;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Versioned local GO/KEGG resource packs shared by enrichment tasks.
 *
 * <p>Each pack is immutable once imported. The registry only marks one pack per
 * kind as active, so activating an older pack is the rollback path. Files and a
 * manifest live under the user-level resource directory; an import is staged
 * and validated before it can become active.</p>
 */
public final class ResourcePacks {
    public static final String DEFAULT_DIRECTORY = ".opentools/resources";
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]+");
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public enum Kind {
        GO,
        KEGG
    }

    public record Pack(
            String id,
            Kind kind,
            String name,
            String version,
            String createdAt,
            Path directory,
            Path primary,
            Path secondary,
            Map<String, Object> counts,
            Map<String, String> sha256,
            boolean active
    ) {
    }

    private final Path root;

    public ResourcePacks(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    /**
     * Opens the shared resource directory.
     *
     * @return resource store under {@code OPENTOOLS_RESOURCES},
     *         {@code -Dopentools.resources.dir}, or the user home default
     */
    public static ResourcePacks open() {
        String configured = System.getProperty("opentools.resources.dir");
        if (configured == null || configured.isBlank()) {
            configured = System.getenv("OPENTOOLS_RESOURCES");
        }
        Path directory = configured == null || configured.isBlank()
                ? Path.of(System.getProperty("user.home")).resolve(DEFAULT_DIRECTORY)
                : Path.of(configured);
        return new ResourcePacks(directory);
    }

    public Path root() {
        return root;
    }

    public synchronized List<Pack> list() throws IOException {
        Registry registry = readRegistry();
        List<Pack> packs = new ArrayList<>();
        for (PackManifest manifest : registry.packs()) {
            packs.add(toPack(manifest));
        }
        packs.sort((left, right) -> {
            int kind = left.kind().compareTo(right.kind());
            return kind != 0 ? kind : right.createdAt().compareTo(left.createdAt());
        });
        return List.copyOf(packs);
    }

    public synchronized Optional<Pack> find(String id) throws IOException {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return list().stream().filter(pack -> pack.id().equals(id.trim())).findFirst();
    }

    public synchronized Optional<Pack> active(Kind kind) throws IOException {
        return list().stream().filter(pack -> pack.kind() == kind && pack.active()).findFirst();
    }

    public synchronized Pack importGo(
            Path obo,
            Path annotations,
            String requestedId,
            String requestedName,
            String requestedVersion,
            boolean activate
    ) throws IOException {
        requireFile(obo, "GO OBO");
        long terms = countOboTerms(obo);
        if (terms == 0) {
            throw new OpenToolException(
                    "GO_OBO_EMPTY",
                    "No [Term] stanzas were found in the GO OBO file.",
                    "Choose a GO OBO 1.4 release such as go-basic.obo."
            );
        }
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("terms", terms);
        Path stagedAnnotation = null;
        if (annotations != null) {
            Map<String, Set<String>> parsed;
            try {
                parsed = EnrichmentSupport.readAnnotations(annotations);
            } catch (Exception ex) {
                throw new OpenToolException(
                        "GO_ANNOTATION_INVALID",
                        "Could not parse the Gene-GO annotation file: " + ex.getMessage(),
                        "Use a two-column gene/GO-term table."
                );
            }
            long pairs = parsed.values().stream().mapToLong(Set::size).sum();
            counts.put("genes", parsed.size());
            counts.put("annotations", pairs);
            stagedAnnotation = annotations;
        }
        return importPack(Kind.GO, obo, stagedAnnotation,
                requestedId, requestedName, requestedVersion, activate, counts);
    }

    public synchronized Pack importKegg(
            Path mapping,
            Path pathwayNames,
            String requestedId,
            String requestedName,
            String requestedVersion,
            boolean activate
    ) throws IOException {
        requireFile(mapping, "KEGG gene-pathway mapping");
        Map<String, Object> counts = countKegg(mapping);
        if (((Number) counts.get("pairs")).longValue() == 0) {
            throw new OpenToolException(
                    "KEGG_MAPPING_EMPTY",
                    "No gene-pathway pairs were found.",
                    "Provide a two-column gene/pathway table."
            );
        }
        if (pathwayNames != null) {
            long names = countRows(pathwayNames);
            if (names == 0) {
                throw new OpenToolException(
                        "KEGG_NAMES_EMPTY",
                        "The pathway name table contains no rows.",
                        "Provide a two-column pathway/name table or leave it blank."
                );
            }
            counts.put("pathwayNames", names);
        }
        return importPack(Kind.KEGG, mapping, pathwayNames,
                requestedId, requestedName, requestedVersion, activate, counts);
    }

    public synchronized Pack activate(String id) throws IOException {
        Registry registry = readRegistry();
        PackManifest target = registry.packs().stream()
                .filter(pack -> pack.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new OpenToolException(
                        "RESOURCE_NOT_FOUND",
                        "Unknown resource pack: " + id,
                        "Run resource.manage with action=LIST."
                ));
        List<PackManifest> updated = new ArrayList<>();
        for (PackManifest pack : registry.packs()) {
            boolean active = pack.kind() == target.kind()
                    ? pack.id().equals(target.id())
                    : pack.active();
            updated.add(active == pack.active() ? pack : copyActive(pack, active));
        }
        writeRegistry(new Registry(registry.schemaVersion(), updated));
        return toPack(updated.stream().filter(pack -> pack.id().equals(id)).findFirst().orElseThrow());
    }

    private Pack importPack(
            Kind kind,
            Path primary,
            Path secondary,
            String requestedId,
            String requestedName,
            String requestedVersion,
            boolean activate,
            Map<String, Object> counts
    ) throws IOException {
        Files.createDirectories(root);
        String sha = Hashing.sha256(primary);
        String id = resolveId(kind, requestedId, sha);
        if (find(id).isPresent()) {
            throw new OpenToolException(
                    "RESOURCE_PACK_EXISTS",
                    "Resource pack already exists: " + id,
                    "Choose another id; packs are immutable so old runs stay reproducible."
            );
        }
        String name = requestedName == null || requestedName.isBlank()
                ? primary.getFileName().toString()
                : requestedName.trim();
        String version = requestedVersion == null || requestedVersion.isBlank()
                ? LocalDate.now().toString()
                : requestedVersion.trim();
        Path staging = root.resolve(".staging").resolve(UUID.randomUUID().toString());
        Path destination = root.resolve(kind.name().toLowerCase(Locale.ROOT)).resolve(id);
        try {
            Files.createDirectories(staging);
            Path primaryCopy = staging.resolve(primary.getFileName().toString());
            Files.copy(primary, primaryCopy, StandardCopyOption.REPLACE_EXISTING);
            Map<String, String> hashes = new LinkedHashMap<>();
            hashes.put(primary.getFileName().toString(), sha);
            Map<String, String> files = new LinkedHashMap<>();
            files.put("primary", primary.getFileName().toString());
            Path secondaryCopy = null;
            if (secondary != null) {
                secondaryCopy = staging.resolve("secondary-" + secondary.getFileName());
                Files.copy(secondary, secondaryCopy, StandardCopyOption.REPLACE_EXISTING);
                hashes.put(secondary.getFileName().toString(), Hashing.sha256(secondary));
                files.put("secondary", secondaryCopy.getFileName().toString());
            }
            PackManifest manifest = new PackManifest(
                    id,
                    kind,
                    name,
                    version,
                    Instant.now().toString(),
                    root.relativize(destination).toString().replace('\\', '/'),
                    files,
                    Map.copyOf(counts),
                    Map.copyOf(hashes),
                    false
            );
            JSON.writeValue(staging.resolve("pack.json").toFile(), manifest);
            Files.createDirectories(destination.getParent());
            moveDirectory(staging, destination);
            Registry registry = readRegistry();
            List<PackManifest> packs = new ArrayList<>();
            for (PackManifest existing : registry.packs()) {
                boolean sameKind = existing.kind() == kind;
                packs.add(activate && sameKind && existing.active()
                        ? copyActive(existing, false)
                        : existing);
            }
            packs.add(copyActive(manifest, activate));
            writeRegistry(new Registry(registry.schemaVersion(), packs));
            return toPack(copyActive(manifest, activate));
        } catch (Exception ex) {
            deleteRecursively(staging);
            if (ex instanceof OpenToolException error) {
                throw error;
            }
            if (ex instanceof IOException io) {
                throw io;
            }
            throw new OpenToolException(
                    "RESOURCE_IMPORT_FAILED",
                    "Could not import the resource pack: " + ex.getMessage(),
                    "Check disk space and directory permissions."
            );
        }
    }

    private Registry readRegistry() throws IOException {
        Path path = root.resolve("registry.json");
        if (!Files.isRegularFile(path)) {
            return new Registry(1, List.of());
        }
        return JSON.readValue(path.toFile(), Registry.class);
    }

    private void writeRegistry(Registry registry) throws IOException {
        Files.createDirectories(root);
        Path temporary = root.resolve(".registry-" + UUID.randomUUID() + ".tmp");
        try {
            JSON.writeValue(temporary.toFile(), registry);
            try {
                Files.move(temporary, root.resolve("registry.json"),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(temporary, root.resolve("registry.json"), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private Pack toPack(PackManifest manifest) {
        Path directory = root.resolve(manifest.directory()).normalize();
        Path primary = manifest.files().containsKey("primary")
                ? directory.resolve(manifest.files().get("primary"))
                : directory;
        Path secondary = manifest.files().containsKey("secondary")
                ? directory.resolve(manifest.files().get("secondary"))
                : null;
        return new Pack(
                manifest.id(),
                manifest.kind(),
                manifest.name(),
                manifest.version(),
                manifest.createdAt(),
                directory,
                primary,
                secondary,
                manifest.counts(),
                manifest.sha256(),
                manifest.active()
        );
    }

    private String resolveId(Kind kind, String requested, String sha) {
        if (requested != null && !requested.isBlank()) {
            String id = requested.trim();
            if (!SAFE_ID.matcher(id).matches()) {
                throw new OpenToolException(
                        "RESOURCE_ID_INVALID",
                        "Resource id may contain only letters, digits, dot, underscore, and hyphen.",
                        "Use an id such as go-basic-2026-11."
                );
            }
            return id;
        }
        return kind.name().toLowerCase(Locale.ROOT) + "-"
                + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(java.time.LocalDateTime.now())
                + "-" + sha.substring(0, 8);
    }

    private static PackManifest copyActive(PackManifest pack, boolean active) {
        return new PackManifest(pack.id(), pack.kind(), pack.name(), pack.version(), pack.createdAt(),
                pack.directory(), pack.files(), pack.counts(), pack.sha256(), active);
    }

    private static void requireFile(Path path, String label) {
        if (path == null || !Files.isRegularFile(path)) {
            throw new OpenToolException(
                    "RESOURCE_FILE_MISSING",
                    label + " was not provided or does not exist.",
                    "Choose an existing file."
            );
        }
    }

    private static long countOboTerms(Path path) throws IOException {
        long terms = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Compression.open(path), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().equals("[Term]")) {
                    terms++;
                }
            }
        }
        return terms;
    }

    private static Map<String, Object> countKegg(Path mapping) throws IOException {
        Set<String> genes = new LinkedHashSet<>();
        Set<String> pathways = new LinkedHashSet<>();
        long pairs = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Compression.open(mapping), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                String[] fields = trimmed.split("\t", -1);
                if (fields.length < 2) {
                    continue;
                }
                String gene = fields[0].trim();
                String pathway = fields[1].trim();
                if (gene.isEmpty() || pathway.isEmpty()) {
                    continue;
                }
                genes.add(gene);
                for (String value : pathway.split("[,;| ]+")) {
                    if (!value.isBlank() && !value.equals("-")) {
                        pathways.add(value);
                        pairs++;
                    }
                }
            }
        }
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("genes", genes.size());
        counts.put("pathways", pathways.size());
        counts.put("pairs", pairs);
        return counts;
    }

    private static long countRows(Path path) throws IOException {
        long rows = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Compression.open(path), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank() && !line.startsWith("#")) {
                    rows++;
                }
            }
        }
        return rows;
    }

    private static void moveDirectory(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
            Files.move(source, target);
        }
    }

    private static void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var files = Files.walk(path)) {
            files.sorted(java.util.Comparator.reverseOrder()).forEach(item -> {
                try {
                    Files.deleteIfExists(item);
                } catch (IOException ignored) {
                    // Best-effort staging cleanup.
                }
            });
        } catch (IOException ignored) {
            // Best-effort staging cleanup.
        }
    }

    record Registry(int schemaVersion, List<PackManifest> packs) {
        Registry {
            schemaVersion = schemaVersion <= 0 ? 1 : schemaVersion;
            packs = packs == null ? List.of() : List.copyOf(packs);
        }
    }

    record PackManifest(
            String id,
            Kind kind,
            String name,
            String version,
            String createdAt,
            String directory,
            Map<String, String> files,
            Map<String, Object> counts,
            Map<String, String> sha256,
            boolean active
    ) {
        PackManifest {
            files = files == null ? Map.of() : Map.copyOf(files);
            counts = counts == null ? Map.of() : Map.copyOf(counts);
            sha256 = sha256 == null ? Map.of() : Map.copyOf(sha256);
        }
    }
}
