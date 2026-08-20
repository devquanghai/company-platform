package com.company.platform.tool.csv.model;

import java.util.Objects;
import java.util.function.Function;

public record CsvColumn<T>(String header, Function<T, ?> value) {
    public CsvColumn {
        if (header == null || header.isBlank()) throw new IllegalArgumentException("header is required");
        Objects.requireNonNull(value, "value");
    }
}
