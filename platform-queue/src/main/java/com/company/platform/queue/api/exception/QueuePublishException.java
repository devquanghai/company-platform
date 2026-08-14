package com.company.platform.queue.api.exception;

public final class QueuePublishException extends RuntimeException {
    private final String errorCode;
    private final String messageId;
    private final boolean outcomeUnknown;

    public QueuePublishException(
        String errorCode,
        String message,
        Throwable cause,
        String messageId,
        boolean outcomeUnknown
    ) {
        super(message, cause);
        this.errorCode = errorCode;
        this.messageId = messageId;
        this.outcomeUnknown = outcomeUnknown;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getMessageId() {
        return messageId;
    }

    public boolean isOutcomeUnknown() {
        return outcomeUnknown;
    }
}
