package com.company.platform.queue.reliability.retry;

import com.company.platform.queue.api.model.MessageMetadata;
import com.company.platform.queue.domain.model.QueueProviderType;

public record MessageFailureContext(
    QueueProviderType provider,
    String destination,
    int attempt,
    Throwable exception,
    String exceptionCategory,
    MessageMetadata metadata,
    boolean schemaValid,
    boolean redelivered
) {
}
