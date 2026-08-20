package com.company.platform.tool.qrcode.model;

public record QrCodeRequest(String content, int width, int height, int margin) {
    public QrCodeRequest { if (content == null || content.isBlank() || content.length() > 4096) throw new IllegalArgumentException("content must contain 1..4096 characters"); if (width < 64 || width > 4096 || height < 64 || height > 4096) throw new IllegalArgumentException("invalid dimensions"); if (margin < 0 || margin > 32) throw new IllegalArgumentException("invalid margin"); }
    public static QrCodeRequest png(String content, int size) { return new QrCodeRequest(content, size, size, 2); }
}
