package com.company.platform.tool.excel.model;

import java.util.List;

public record ExcelImportRequest<T>(String claimedFilename, String sheetName, Integer sheetIndex, int headerRow, int startRow, boolean skipBlankRows, boolean failFast, List<String> requiredColumns, int maximumSheets, int maximumRows, int maximumColumns, long maximumBytes, ExcelRowMapper<T> mapper) {
    public ExcelImportRequest {
        if (sheetName != null && sheetIndex != null) throw new IllegalArgumentException("Select a sheet by name or index, not both");
        if (headerRow < 0 || startRow <= headerRow) throw new IllegalArgumentException("startRow must follow headerRow");
        requiredColumns = requiredColumns == null ? List.of() : List.copyOf(requiredColumns); maximumSheets = maximumSheets < 1 ? 100 : maximumSheets; maximumRows = maximumRows < 1 ? 100_000 : maximumRows; maximumColumns = maximumColumns < 1 ? 512 : maximumColumns; maximumBytes = maximumBytes < 1 ? 20L * 1024 * 1024 : maximumBytes;
        if (mapper == null) throw new IllegalArgumentException("mapper is required");
    }
    public static <T> ExcelImportRequest<T> defaults(String filename, ExcelRowMapper<T> mapper) { return new ExcelImportRequest<>(filename, null, 0, 0, 1, true, false, List.of(), 100, 100_000, 512, 20L * 1024 * 1024, mapper); }
}
