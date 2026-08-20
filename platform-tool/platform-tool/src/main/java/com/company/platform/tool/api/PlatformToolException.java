package com.company.platform.tool.api;

public class PlatformToolException extends RuntimeException {
    private final String code;

    public PlatformToolException(String code, String message) { super(message); this.code = code; }
    public PlatformToolException(String code, String message, Throwable cause) { super(message, cause); this.code = code; }
    public String code() { return code; }
}
