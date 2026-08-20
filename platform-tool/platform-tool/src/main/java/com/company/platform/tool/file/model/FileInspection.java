package com.company.platform.tool.file.model;

public record FileInspection(String detectedContentType, String extension, long size, String sha256) {
}
