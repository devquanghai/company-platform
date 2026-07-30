package com.company.platform.queue.api.publish;

public interface TypedMessagePublisherFactory {
    <T> TypedMessagePublisher<T> getPublisher(String destination, Class<T> payloadType);
}
