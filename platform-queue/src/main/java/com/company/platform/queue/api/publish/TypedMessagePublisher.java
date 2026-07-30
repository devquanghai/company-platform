package com.company.platform.queue.api.publish;

import java.util.concurrent.CompletionStage;

public interface TypedMessagePublisher<T> {
    PublishResult publish(T payload);
    PublishResult publish(String key, T payload);
    CompletionStage<PublishResult> publishAsync(T payload);
}
