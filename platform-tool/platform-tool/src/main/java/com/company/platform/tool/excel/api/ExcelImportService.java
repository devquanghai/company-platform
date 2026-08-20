package com.company.platform.tool.excel.api;

import com.company.platform.tool.excel.model.ExcelImportRequest;
import com.company.platform.tool.excel.model.ExcelImportResult;
import java.io.InputStream;

public interface ExcelImportService { <T> ExcelImportResult<T> importFile(InputStream input, ExcelImportRequest<T> request); }
