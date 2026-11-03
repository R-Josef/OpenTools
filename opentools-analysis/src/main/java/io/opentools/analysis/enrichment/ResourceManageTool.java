package io.opentools.analysis.enrichment;

import io.opentools.api.tool.AbstractOpenTool;
import io.opentools.api.tool.OpenToolException;
import io.opentools.api.tool.OutputArtifact;
import io.opentools.api.tool.ParameterType;
import io.opentools.api.tool.ResultTable;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolParameter;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ToolResult;
import io.opentools.api.tool.ValidationIssue;
import io.opentools.api.tool.ValidationResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lists, imports, and activates versioned GO/KEGG resource packs.
 */
public final class ResourceManageTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "resource.manage",
            "Manage GO/KEGG Resource Packs",
            "List, import, and activate versioned local GO/KEGG resource packs for enrichment tasks.",
            "Enrichment",
            List.of("resource pack", "GO resource", "KEGG resource", "资源包", "GO 资源"),
            List.of("GO OBO", "Gene-GO TSV", "Gene-pathway TSV"),
            List.of("TSV", "JSON"),
            "1.0.0",
            List.of(
                    new ToolParameter("action", "Action",
                            "LIST reports packs; IMPORT_GO/IMPORT_KEGG add an immutable pack; ACTIVATE switches the active pack.",
                            ParameterType.ENUM, false, "LIST",
                            List.of("LIST", "IMPORT_GO", "IMPORT_KEGG", "ACTIVATE"),
                            null, null, false, null),
                    new ToolParameter("kind", "List filter",
                            "Restrict LIST to GO, KEGG, or all packs.",
                            ParameterType.ENUM, false, "ALL", List.of("ALL", "GO", "KEGG"),
                            null, null, true, null),
                    ToolParameter.string("id", "Pack id",
                            "Optional stable id for an import, or the pack to activate. Imports are immutable.",
                            ""),
                    ToolParameter.string("name", "Pack name", "Display name for an imported pack.", ""),
                    ToolParameter.string("version", "Pack version",
                            "Version label such as go-basic-2026-11; defaults to the import date.", ""),
                    ToolParameter.optionalFile("obo", "GO OBO file",
                            "GO OBO 1.4 file used by go.level and for pack metadata."),
                    ToolParameter.optionalFile("annotations", "Gene-GO annotation",
                            "Two columns: gene and GO terms; required for GO enrichment from the pack."),
                    ToolParameter.optionalFile("mapping", "Gene-pathway mapping",
                            "Two columns: gene and KEGG pathway IDs."),
                    ToolParameter.optionalFile("pathwayNames", "Pathway names",
                            "Optional two-column pathway/name table for KEGG reporting."),
                    ToolParameter.bool("activate", "Activate after import",
                            "Make the imported pack active for its kind, replacing the previous active pack.", true)
            )
    );

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ValidationResult validate(ToolRequest request) {
        ValidationResult result = validateParameters(request);
        String action = request.string("action") == null || request.string("action").isBlank()
                ? "LIST" : request.string("action").toUpperCase(Locale.ROOT);
        switch (action) {
            case "IMPORT_GO" -> {
                if (!isFile(request.path("obo"))) {
                    result = result.merge(ValidationResult.of(ValidationIssue.error(
                            "obo", "A GO OBO file is required for IMPORT_GO.",
                            "Choose a go-basic.obo file.")));
                }
            }
            case "IMPORT_KEGG" -> {
                if (!isFile(request.path("mapping"))) {
                    result = result.merge(ValidationResult.of(ValidationIssue.error(
                            "mapping", "A gene-pathway mapping is required for IMPORT_KEGG.",
                            "Choose a two-column gene/pathway table.")));
                }
            }
            case "ACTIVATE" -> {
                if (request.string("id").isBlank()) {
                    result = result.merge(ValidationResult.of(ValidationIssue.error(
                            "id", "ACTIVATE requires a pack id.",
                            "Run LIST and copy the id of the pack to activate.")));
                }
            }
            default -> {
                // LIST needs no additional fields.
            }
        }
        return result;
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        String action = request.string("action") == null || request.string("action").isBlank()
                ? "LIST" : request.string("action").toUpperCase(Locale.ROOT);
        ResourcePacks packs = ResourcePacks.open();
        try {
            return switch (action) {
                case "IMPORT_GO" -> importGo(request, context, packs);
                case "IMPORT_KEGG" -> importKegg(request, context, packs);
                case "ACTIVATE" -> activate(request, context, packs);
                default -> list(request, context, packs);
            };
        } catch (OpenToolException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new OpenToolException(
                    "RESOURCE_IO_ERROR",
                    "Could not access the resource library: " + ex.getMessage(),
                    "Check the resource directory permissions and free space."
            );
        }
    }

    private ToolResult list(ToolRequest request, ToolExecutionContext context, ResourcePacks packs)
            throws Exception {
        String kindFilter = request.string("kind") == null || request.string("kind").isBlank()
                ? "ALL" : request.string("kind").toUpperCase(Locale.ROOT);
        List<ResourcePacks.Pack> all = packs.list();
        List<ResourcePacks.Pack> visible = all.stream()
                .filter(pack -> kindFilter.equals("ALL") || pack.kind().name().equals(kindFilter))
                .toList();
        List<List<String>> rows = new ArrayList<>();
        for (ResourcePacks.Pack pack : visible) {
            rows.add(List.of(
                    pack.id(),
                    pack.kind().name(),
                    pack.name(),
                    pack.version(),
                    pack.active() ? "yes" : "no",
                    summarizeCounts(pack.counts()),
                    shortHash(pack)
            ));
        }
        context.progress(1.0, "Listed " + visible.size() + " resource packs");
        return new ToolResult(
                "Listed " + visible.size() + " resource packs",
                List.of(visible.size()),
                "Resource directory: " + packs.root(),
                List.of(packs.root().toString()),
                new ResultTable(List.of("ID", "Kind", "Name", "Version", "Active", "Entries", "SHA-256"),
                        rows),
                List.of(),
                Map.of("packs", visible.size(), "resourceRoot", packs.root().toString())
        );
    }

    private ToolResult importGo(ToolRequest request, ToolExecutionContext context, ResourcePacks packs)
            throws Exception {
        ResourcePacks.Pack pack = packs.importGo(
                request.path("obo"),
                request.path("annotations"),
                request.string("id"),
                request.string("name"),
                request.string("version"),
                request.bool("activate")
        );
        return imported(pack, context, "GO");
    }

    private ToolResult importKegg(ToolRequest request, ToolExecutionContext context, ResourcePacks packs)
            throws Exception {
        ResourcePacks.Pack pack = packs.importKegg(
                request.path("mapping"),
                request.path("pathwayNames"),
                request.string("id"),
                request.string("name"),
                request.string("version"),
                request.bool("activate")
        );
        return imported(pack, context, "KEGG");
    }

    private ToolResult imported(ResourcePacks.Pack pack, ToolExecutionContext context, String label)
            throws Exception {
        Path manifest = pack.directory().resolve("pack.json");
        context.progress(1.0, label + " resource pack imported");
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("id", pack.id());
        metrics.put("kind", pack.kind().name());
        metrics.put("version", pack.version());
        metrics.put("active", pack.active());
        metrics.putAll(pack.counts());
        return new ToolResult(
                label + " resource pack imported: " + pack.id(),
                List.of(pack.id()),
                "Stored in " + pack.directory() + "; active=" + pack.active(),
                List.of(pack.directory().toString()),
                new ResultTable(List.of("Metric", "Value"), List.of(
                        List.of("ID", pack.id()),
                        List.of("Kind", pack.kind().name()),
                        List.of("Version", pack.version()),
                        List.of("Active", Boolean.toString(pack.active())),
                        List.of("Entries", summarizeCounts(pack.counts())),
                        List.of("Directory", pack.directory().toString())
                )),
                Files.isRegularFile(manifest)
                        ? List.of(new OutputArtifact(manifest, "Resource pack manifest", "application/json"))
                        : List.of(),
                metrics
        );
    }

    private ToolResult activate(ToolRequest request, ToolExecutionContext context, ResourcePacks packs)
            throws Exception {
        ResourcePacks.Pack pack = packs.activate(request.string("id").trim());
        context.progress(1.0, "Resource pack activated");
        return new ToolResult(
                "Activated " + pack.kind().name() + " resource pack " + pack.id(),
                List.of(pack.id()),
                "The previous active pack remains installed for rollback.",
                List.of(),
                new ResultTable(List.of("Metric", "Value"), List.of(
                        List.of("ID", pack.id()),
                        List.of("Kind", pack.kind().name()),
                        List.of("Version", pack.version()),
                        List.of("Active", "true")
                )),
                List.of(),
                Map.of("id", pack.id(), "kind", pack.kind().name(), "active", true)
        );
    }

    private static boolean isFile(Path path) {
        return path != null && Files.isRegularFile(path);
    }

    private static String summarizeCounts(Map<String, Object> counts) {
        if (counts.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        counts.forEach((key, value) -> parts.add(key + "=" + value));
        return String.join("; ", parts);
    }

    private static String shortHash(ResourcePacks.Pack pack) {
        return pack.sha256().values().stream().findFirst()
                .map(hash -> hash.length() <= 12 ? hash : hash.substring(0, 12))
                .orElse("");
    }
}