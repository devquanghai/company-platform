package com.company.platform.tool.csv.model;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

public record CsvImportRequest(char delimiter, Charset charset, long maximumRows, List<String> requiredHeaders) {
    public CsvImportRequest {
        charset = charset == null ? StandardCharsets.UTF_8 : charset;
        maximumRows = maximumRows < 1 ? 100_000 : maximumRows;
        requiredHeaders = requiredHeaders == null ? List.of() : List.copyOf(requiredHeaders);
    }

    public static CsvImportRequest secureDefaults() {
        return new CsvImportRequest(',', StandardCharsets.UTF_8, 100_000, List.of());
    }
}
