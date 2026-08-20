package com.company.platform.tool.excel.api;

import com.company.platform.tool.api.PlatformToolException;

public final class ExcelImportException extends PlatformToolException {
    public ExcelImportException(String message, Throwable cause) { super("EXCEL_IMPORT_FAILED", message, cause); }
}
