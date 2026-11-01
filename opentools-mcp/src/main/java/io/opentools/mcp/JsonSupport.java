package io.opentools.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.nio.file.Path;

public final class JsonSupport {
    public static final ObjectMapper MAPPER = create(false);
    public static final ObjectMapper PRETTY = create(true);

    private JsonSupport() {
    }

    private static ObjectMapper create(boolean pretty) {
        SimpleModule module = new SimpleModule();
        module.addSerializer(Path.class, ToStringSerializer.instance);
        ObjectMapper mapper = new ObjectMapper()
                .findAndRegisterModules()
                .registerModule(module)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        if (pretty) {
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
        }
        return mapper;
    }
}
