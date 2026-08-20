package com.company.platform.tool.excel.api;

import com.company.platform.tool.excel.model.ExcelTemplateRequest;
import java.io.OutputStream;

public interface ExcelTemplateService { void export(ExcelTemplateRequest request, OutputStream output); }
