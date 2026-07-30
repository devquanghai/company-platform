package com.company.platform.queue.domain.exception;

public final class QueuePublishException extends RuntimeException {
    private final String errorCode;

    public QueuePublishException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
