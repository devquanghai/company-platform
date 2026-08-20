package com.company.platform.tool.pdf.model;

import java.io.IOException;
import java.io.InputStream;

public record PdfFont(String family, InputStreamSource source) { public PdfFont { if (family == null || family.isBlank() || source == null) throw new IllegalArgumentException("font family and source are required"); } @FunctionalInterface public interface InputStreamSource { InputStream open() throws IOException; } }
