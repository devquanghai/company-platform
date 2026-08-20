package com.company.platform.tool.api;

public final class InvalidFileException extends PlatformToolException {
    public InvalidFileException(String message) { super("INVALID_FILE", message); }
    public InvalidFileException(String message, Throwable cause) { super("INVALID_FILE", message, cause); }
}
