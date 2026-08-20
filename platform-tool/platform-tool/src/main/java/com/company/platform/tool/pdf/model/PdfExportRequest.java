package com.company.platform.tool.pdf.model;

import java.util.List;
import java.util.Map;

public record PdfExportRequest(String html, float pageWidthMm, float pageHeightMm, PdfOrientation orientation,
                               float marginMm, String watermark, String title, String author, List<PdfFont> fonts,
                               Map<String, byte[]> trustedResources, int maximumHtmlCharacters) {
    public PdfExportRequest {
        if (html == null) throw new IllegalArgumentException("html is required");
        if (pageWidthMm <= 0) pageWidthMm = 210;
        if (pageHeightMm <= 0) pageHeightMm = 297;
        orientation = orientation == null ? PdfOrientation.PORTRAIT : orientation;
        if (marginMm < 0 || marginMm > 100) throw new IllegalArgumentException("invalid margin");
        fonts = fonts == null ? List.of() : List.copyOf(fonts);
        trustedResources = trustedResources == null ? Map.of() : Map.copyOf(trustedResources);
        maximumHtmlCharacters = maximumHtmlCharacters < 1 ? 2_000_000 : maximumHtmlCharacters;
        if (html.length() > maximumHtmlCharacters) throw new IllegalArgumentException("HTML size limit exceeded");
    }

    public static PdfExportRequest a4(String html) {
        return new PdfExportRequest(html, 210, 297, PdfOrientation.PORTRAIT, 12, null, null, null, List.of(), Map.of(), 2_000_000);
    }
}
