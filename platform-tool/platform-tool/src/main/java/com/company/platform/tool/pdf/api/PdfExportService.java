package com.company.platform.tool.pdf.api;

import com.company.platform.tool.pdf.model.PdfExportRequest;

import java.io.OutputStream;

public interface PdfExportService {
    void export(PdfExportRequest request, OutputStream output);
}
