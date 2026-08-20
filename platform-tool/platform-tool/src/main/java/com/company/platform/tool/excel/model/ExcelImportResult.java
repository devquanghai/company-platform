package com.company.platform.tool.excel.model;

import java.util.List;

public record ExcelImportResult<T>(List<T> records, List<ExcelImportError> errors, long totalRows, long successRows, long failedRows) {
    public ExcelImportResult { records = List.copyOf(records); errors = List.copyOf(errors); }
}
