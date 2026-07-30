package com.company.platform.cache.observability.event;

import org.springframework.context.ApplicationEventPublisher;

import java.util.Objects;

public final class SpringCacheEventPublisher implements CacheEventPublisher {
    private final ApplicationEventPublisher publisher;

    public SpringCacheEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = Objects.requireNonNull(publisher, "publisher");
    }

    @Override
    public void publish(CacheOperationEvent event) {
        publisher.publishEvent(Objects.requireNonNull(event, "event"));
    }
}
