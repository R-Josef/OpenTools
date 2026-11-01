package io.opentools.plugin.host;

import java.util.List;
import java.util.Map;

public record PluginInfo(
        String id,
        String name,
        String version,
        String apiVersion,
        String source,
        PluginState state,
        String diagnostic,
        List<String> dependencies,
        List<String> requestedCapabilities,
        Map<String, String> metadata
) {
    public PluginInfo {
        apiVersion = apiVersion == null ? "" : apiVersion;
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        requestedCapabilities = requestedCapabilities == null
                ? List.of()
                : List.copyOf(requestedCapabilities);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
