package io.opentools.core.tool;

import io.opentools.api.tool.OpenTool;
import io.opentools.api.tool.ToolDescriptor;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ToolRegistry {
    private final Map<String, OpenTool> tools = new ConcurrentHashMap<>();

    public void register(OpenTool tool) {
        String id = tool.descriptor().id();
        OpenTool existing = tools.putIfAbsent(id, tool);
        if (existing != null) {
            throw new IllegalArgumentException("Tool already registered: " + id);
        }
    }

    public void unregister(String id) {
        tools.remove(id);
    }

    public Optional<OpenTool> find(String id) {
        return Optional.ofNullable(tools.get(id));
    }

    public List<ToolDescriptor> list() {
        return tools.values().stream()
                .map(OpenTool::descriptor)
                .sorted(Comparator.comparing(ToolDescriptor::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public List<ToolDescriptor> search(String query) {
        if (query == null || query.isBlank()) {
            return list();
        }
        String needle = query.trim().toLowerCase();
        return list().stream()
                .filter(tool -> tool.searchText().contains(needle))
                .toList();
    }
}
