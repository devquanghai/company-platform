package com.company.platform.tool.excel.api;

import com.company.platform.tool.api.PlatformToolException;

public final class ExcelExportException extends PlatformToolException {
    public ExcelExportException(String message, Throwable cause) {
        super("EXCEL_EXPORT_FAILED", message, cause);
    }
}
