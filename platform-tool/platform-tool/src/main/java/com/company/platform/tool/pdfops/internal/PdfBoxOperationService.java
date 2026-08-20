package com.company.platform.tool.pdfops.internal;

import com.company.platform.tool.api.UnsafeFileException;
import com.company.platform.tool.common.BoundedInputStream;
import com.company.platform.tool.pdfops.api.PdfOperationService;
import com.company.platform.tool.pdfops.model.PdfInput;
import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.util.Matrix;

public final class PdfBoxOperationService implements PdfOperationService {
    private static final int MAX_PAGES = 10_000;
    @Override public void merge(List<PdfInput> inputs, OutputStream output) throws IOException {
        if (inputs == null || inputs.isEmpty() || inputs.size() > 100) throw new IllegalArgumentException("PDF merge requires 1..100 inputs");
        try (PDDocument destination = new PDDocument()) {
            PDFMergerUtility merger = new PDFMergerUtility();
            for (PdfInput input : inputs) { try (Loaded loaded = load(input)) { ensurePageLimit(loaded.document()); merger.appendDocument(destination, loaded.document()); } }
            ensurePageLimit(destination); destination.save(output);
        }
    }
    @Override public int split(PdfInput input, int pagesPerDocument, OutputStreamProvider outputs) throws IOException {
        if (pagesPerDocument < 1 || pagesPerDocument > MAX_PAGES) throw new IllegalArgumentException("Invalid pagesPerDocument");
        try (Loaded loaded = load(input)) { ensurePageLimit(loaded.document()); Splitter splitter = new Splitter(); splitter.setSplitAtPage(pagesPerDocument); List<PDDocument> parts = splitter.split(loaded.document()); int part = 0; try { for (PDDocument document : parts) { try (document; OutputStream output = outputs.open(++part)) { document.save(output); } } } finally { for (int i = part; i < parts.size(); i++) parts.get(i).close(); } return parts.size(); }
    }
    @Override public void watermark(PdfInput input, String text, OutputStream output) throws IOException {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("watermark text is required"); String safe = text.substring(0, Math.min(80, text.length())).replaceAll("[^\\x20-\\x7E]", "?");
        try (Loaded loaded = load(input)) { ensurePageLimit(loaded.document()); for (PDPage page : loaded.document().getPages()) addWatermark(loaded.document(), page, safe); loaded.document().save(output); }
    }
    @Override public int pageCount(PdfInput input) throws IOException { try (Loaded loaded = load(input)) { ensurePageLimit(loaded.document()); return loaded.document().getNumberOfPages(); } }
    private static Loaded load(PdfInput input) throws IOException {
        Path temporary = Files.createTempFile("platform-tool-pdf-", ".pdf"); boolean success = false;
        try (InputStream source = input.source().open(); InputStream bounded = new BoundedInputStream(source, input.maximumBytes()); OutputStream target = Files.newOutputStream(temporary)) { bounded.transferTo(target); }
        try { PDDocument document = Loader.loadPDF(temporary.toFile()); success = true; return new Loaded(temporary, document); }
        finally { if (!success) Files.deleteIfExists(temporary); }
    }
    private static void ensurePageLimit(PDDocument document) { if (document.getNumberOfPages() > MAX_PAGES) throw new UnsafeFileException("PDF page limit exceeded"); }
    private static void addWatermark(PDDocument document, PDPage page, String text) throws IOException { try (PDPageContentStream content = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)) { content.saveGraphicsState(); content.setNonStrokingColor(new Color(180, 180, 180)); content.transform(Matrix.getRotateInstance(Math.toRadians(45), page.getMediaBox().getWidth() / 3, page.getMediaBox().getHeight() / 2)); content.beginText(); content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 44); content.showText(text); content.endText(); content.restoreGraphicsState(); } }
    private record Loaded(Path path, PDDocument document) implements AutoCloseable { @Override public void close() throws IOException { try { document.close(); } finally { Files.deleteIfExists(path); } } }
}
