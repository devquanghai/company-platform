package com.company.platform.queue.api.publish;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface MessagePublisher {
    <T> PublishResult publish(String destination, T payload);
    <K, T> PublishResult publish(String destination, K key, T payload);
    <T> PublishResult publish(PublishRequest<T> request);
    <T> CompletionStage<PublishResult> publishAsync(PublishRequest<T> request);
    <T> List<PublishResult> publishBatch(List<PublishRequest<T>> requests);
}
