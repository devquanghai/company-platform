package com.company.platform.queue.application.port.out;

import com.company.platform.queue.api.publish.PublishResult;
import com.company.platform.queue.domain.model.QueueProviderType;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

public interface ProviderMessagePublisher {
    QueueProviderType provider();
    CompletionStage<PublishResult> publish(PreparedMessage message, Duration timeout);
}
