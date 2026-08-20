package com.company.platform.tool.excel.model;

public record ExcelImportError(long rowNumber, String column, String code, String message) { }
