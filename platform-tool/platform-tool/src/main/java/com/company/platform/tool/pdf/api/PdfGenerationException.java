package com.company.platform.tool.pdf.api;

import com.company.platform.tool.api.PlatformToolException;

public final class PdfGenerationException extends PlatformToolException { public PdfGenerationException(String message, Throwable cause) { super("PDF_GENERATION_FAILED", message, cause); } }
