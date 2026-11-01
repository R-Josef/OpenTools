package io.opentools.visualization.io;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.opentools.visualization.model.PlotModel;
import io.opentools.visualization.model.PlotSpec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class PlotJson {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private PlotJson() {
    }

    public static String write(PlotSpec spec) throws IOException {
        return MAPPER.writeValueAsString(spec);
    }

    public static void write(PlotModel model, Path path) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, MAPPER.writeValueAsString(model) + System.lineSeparator());
        Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public static PlotModel readModel(Path path) throws IOException {
        return MAPPER.readValue(path.toFile(), PlotModel.class);
    }

    public static PlotSpec read(Path path) throws IOException {
        return MAPPER.readValue(path.toFile(), PlotSpec.class);
    }

    public static PlotSpec read(String json) throws IOException {
        return MAPPER.readValue(json, PlotSpec.class);
    }
}
