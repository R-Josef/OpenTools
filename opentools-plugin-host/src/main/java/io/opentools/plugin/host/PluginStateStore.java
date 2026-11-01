package io.opentools.plugin.host;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

final class PluginStateStore {
    private final Path stateFile;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private Map<String, Boolean> enabled = new LinkedHashMap<>();
    private String diagnostic = "";

    PluginStateStore(Path pluginsDirectory) {
        this.stateFile = pluginsDirectory.resolve("plugin-state.json");
        load();
    }

    synchronized boolean isEnabled(String pluginId) {
        return enabled.getOrDefault(pluginId, false);
    }

    synchronized void setEnabled(String pluginId, boolean value) {
        enabled.put(pluginId, value);
        save();
    }

    synchronized String diagnostic() {
        return diagnostic;
    }

    private void load() {
        if (!Files.isRegularFile(stateFile)) {
            return;
        }
        try {
            StateDocument document = objectMapper.readValue(stateFile.toFile(), StateDocument.class);
            if (document.enabled() != null) {
                enabled = new LinkedHashMap<>(document.enabled());
            }
        } catch (Exception ex) {
            diagnostic = "Plugin state could not be read; enabled plugins use defaults: " + ex.getMessage();
        }
    }

    private void save() {
        try {
            Path parent = stateFile.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temporary = stateFile.resolveSibling(stateFile.getFileName() + ".tmp");
            objectMapper.writeValue(temporary.toFile(), new StateDocument(1, enabled));
            try {
                Files.move(
                        temporary,
                        stateFile,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, stateFile, StandardCopyOption.REPLACE_EXISTING);
            }
            diagnostic = "";
        } catch (IOException ex) {
            diagnostic = "Plugin state could not be saved: " + ex.getMessage();
        }
    }

    record StateDocument(int schemaVersion, Map<String, Boolean> enabled) {
    }
}
