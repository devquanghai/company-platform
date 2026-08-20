package com.company.platform.tool.excel.api;

import com.company.platform.tool.excel.model.ExcelWorkbookRequest;

import java.io.OutputStream;

public interface ExcelExportService {
    void export(ExcelWorkbookRequest request, OutputStream output);
}
