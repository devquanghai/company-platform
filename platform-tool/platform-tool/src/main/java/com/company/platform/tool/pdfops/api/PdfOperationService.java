package com.company.platform.tool.pdfops.api;

import com.company.platform.tool.pdfops.model.PdfInput;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public interface PdfOperationService {
    void merge(List<PdfInput> inputs, OutputStream output) throws IOException;
    int split(PdfInput input, int pagesPerDocument, OutputStreamProvider outputs) throws IOException;
    void watermark(PdfInput input, String text, OutputStream output) throws IOException;
    int pageCount(PdfInput input) throws IOException;
    @FunctionalInterface interface OutputStreamProvider { OutputStream open(int oneBasedPartNumber) throws IOException; }
}
