package com.company.platform.tool.pdf.internal;

import com.company.platform.tool.pdf.api.PdfExportService;
import com.company.platform.tool.common.ToolObservations;
import com.company.platform.tool.pdf.api.PdfGenerationException;
import com.company.platform.tool.pdf.model.PdfExportRequest;
import com.company.platform.tool.pdf.model.PdfFont;
import com.company.platform.tool.pdf.model.PdfOrientation;
import com.company.platform.tool.template.api.HtmlSanitizer;
import com.openhtmltopdf.extend.FSStream;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.PageSizeUnits;
import com.openhtmltopdf.outputdevice.helper.ExternalResourceControlPriority;
import com.openhtmltopdf.pdfboxout.PDFCreationListener;
import com.openhtmltopdf.pdfboxout.PdfBoxRenderer;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.util.Locale;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.util.Matrix;
import io.micrometer.observation.ObservationRegistry;

public final class OpenHtmlPdfExportService implements PdfExportService {
    private final HtmlSanitizer sanitizer;
    private final ObservationRegistry observations;

    public OpenHtmlPdfExportService(HtmlSanitizer sanitizer, ObservationRegistry observations) {
        this.sanitizer = sanitizer;
        this.observations = observations;
    }

    @Override
    public void export(PdfExportRequest request, OutputStream output) {
        ToolObservations.observe("platform.tool.pdf.export", "html", observations, () -> exportInternal(request, output));
    }

    private void exportInternal(PdfExportRequest request, OutputStream output) {
        if (containsXmlHazard(request.html())) throw new PdfGenerationException("Unsafe XML/HTML construct", null);
        String html = sanitizer.sanitize(request.html());
        float width = request.orientation() == PdfOrientation.LANDSCAPE ? request.pageHeightMm() : request.pageWidthMm();
        float height = request.orientation() == PdfOrientation.LANDSCAPE ? request.pageWidthMm() : request.pageHeightMm();
        String metadata = (request.title() == null ? "" : "<title>" + xml(request.title()) + "</title>") + (request.author() == null ? "" : "<meta name=\"author\" content=\"" + xml(request.author()) + "\"/>");
        html = "<html><head>" + metadata + "<style>@page{size:" + width + "mm " + height + "mm;margin:" + request.marginMm() + "mm}</style></head><body>" + html + "</body></html>";
        try {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode().withProducer("platform-tool").useDefaultPageSize(width, height, PageSizeUnits.MM).withHtmlContent(html, "memory:/").toStream(output)
                .useExternalResourceAccessControl((uri, type) -> uri != null && uri.startsWith("memory:/"), ExternalResourceControlPriority.RUN_BEFORE_RESOLVING_URI)
                .useProtocolsStreamImplementation(uri -> memoryStream(uri, request.trustedResources()), "memory");
            for (PdfFont font : request.fonts()) builder.useFont(() -> open(font), font.family());
            try (PdfBoxRenderer renderer = builder.buildPdfRenderer()) {
                renderer.setListener(new MetadataAndWatermarkListener(request));
                renderer.layout();
                renderer.createPDF();
            }
        } catch (Exception exception) {
            throw new PdfGenerationException("Unable to render PDF", exception);
        }
    }

    private static InputStream open(PdfFont font) {
        try {
            return font.source().open();
        } catch (java.io.IOException exception) {
            throw new PdfGenerationException("Unable to open PDF font", exception);
        }
    }

    private static FSStream memoryStream(String uri, Map<String, byte[]> resources) {
        String key = uri == null ? "" : uri.replaceFirst("^memory:/+", "");
        byte[] bytes = resources.get(key);
        if (bytes == null || bytes.length > 5 * 1024 * 1024) return null;
        return new FSStream() {
            @Override
            public InputStream getStream() {
                return new ByteArrayInputStream(bytes);
            }

            @Override
            public Reader getReader() {
                return null;
            }
        };
    }

    private static boolean containsXmlHazard(String html) {
        String lower = html.toLowerCase(Locale.ROOT);
        return lower.contains("<!doctype") || lower.contains("<!entity") || lower.contains("xinclude") || lower.contains("javascript:") || lower.contains("file:") || lower.contains("jar:");
    }

    private static String xml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static final class MetadataAndWatermarkListener implements PDFCreationListener {
        private final PdfExportRequest request;

        private MetadataAndWatermarkListener(PdfExportRequest request) {
            this.request = request;
        }

        @Override
        public void preOpen(PdfBoxRenderer renderer) {
        }

        @Override
        public void preWrite(PdfBoxRenderer renderer, int pageCount) {
        }

        @Override
        public void onClose(PdfBoxRenderer renderer) {
            if (request.watermark() == null || request.watermark().isBlank()) return;
            PDDocument document = renderer.getPdfDocument();
            try {
                for (PDPage page : document.getPages()) watermark(document, page, request.watermark());
            } catch (java.io.IOException exception) {
                throw new PdfGenerationException("Unable to apply PDF watermark", exception);
            }
        }

        private static void watermark(PDDocument document, PDPage page, String text) throws java.io.IOException {
            String safe = text.length() > 80 ? text.substring(0, 80) : text;
            try (PDPageContentStream content = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                content.saveGraphicsState();
                content.setNonStrokingColor(new Color(180, 180, 180));
                content.transform(Matrix.getRotateInstance(Math.toRadians(45), page.getMediaBox().getWidth() / 3, page.getMediaBox().getHeight() / 2));
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 44);
                content.showText(safe.replaceAll("[^\\x20-\\x7E]", "?"));
                content.endText();
                content.restoreGraphicsState();
            }
        }
    }
}
