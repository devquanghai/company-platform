package com.company.platform.queue.api.publish;

import java.time.Duration;
import java.time.Instant;

public interface DelayedMessagePublisher {
    PublishResult publishAt(PublishRequest<?> request, Instant deliverAt);
    PublishResult publishAfter(PublishRequest<?> request, Duration delay);
}
