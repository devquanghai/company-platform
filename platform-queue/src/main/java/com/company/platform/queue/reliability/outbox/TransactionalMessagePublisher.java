package com.company.platform.queue.reliability.outbox;

import com.company.platform.queue.api.model.MessageEnvelope;
import com.company.platform.queue.api.publish.PublishRequest;
import com.company.platform.queue.api.publish.PublishResult;

public interface TransactionalMessagePublisher {
    <T> PublishResult publish(String destination, T payload);
    PublishResult publish(PublishRequest<?> request, MessageEnvelope<?> envelope);
}
