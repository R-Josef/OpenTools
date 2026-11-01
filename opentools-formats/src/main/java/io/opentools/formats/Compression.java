package io.opentools.formats;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class Compression {
    private Compression() {
    }

    public static InputStream open(Path path) throws IOException {
        InputStream raw = new BufferedInputStream(Files.newInputStream(path));
        if (isGzip(path)) {
            return new GZIPInputStream(raw);
        }
        return raw;
    }

    public static BufferedWriter createWriter(Path path) throws IOException {
        OutputStream raw = new BufferedOutputStream(Files.newOutputStream(path));
        OutputStream encoded = isGzip(path) ? new GZIPOutputStream(raw) : raw;
        return new BufferedWriter(new OutputStreamWriter(encoded, StandardCharsets.UTF_8));
    }

    public static boolean isGzip(Path path) {
        String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".gz") || name.endsWith(".gzip");
    }
}
