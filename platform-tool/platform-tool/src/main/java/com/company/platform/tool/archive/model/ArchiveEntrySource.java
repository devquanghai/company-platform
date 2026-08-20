package com.company.platform.tool.archive.model;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public record ArchiveEntrySource(String name, InputStreamSource source) {
    public ArchiveEntrySource {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        Objects.requireNonNull(source, "source");
    }

    @FunctionalInterface
    public interface InputStreamSource {
        InputStream open() throws IOException;
    }
}
