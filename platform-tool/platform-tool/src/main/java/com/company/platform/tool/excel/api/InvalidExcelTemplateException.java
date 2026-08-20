package com.company.platform.tool.excel.api;

import com.company.platform.tool.api.PlatformToolException;

public final class InvalidExcelTemplateException extends PlatformToolException {
    public InvalidExcelTemplateException(String message) { super("INVALID_EXCEL_TEMPLATE", message); }
    public InvalidExcelTemplateException(String message, Throwable cause) { super("INVALID_EXCEL_TEMPLATE", message, cause); }
}
