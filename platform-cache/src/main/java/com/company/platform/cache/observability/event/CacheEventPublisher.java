package com.company.platform.cache.observability.event;

public interface CacheEventPublisher {
    void publish(CacheOperationEvent event);
}
