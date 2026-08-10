package com.company.platform.queue.consume.internal.port.out;

import com.company.platform.queue.consume.internal.application.QueueListenerEndpoint;
import com.company.platform.queue.autoconfigure.properties.DestinationProperties;
import com.company.platform.queue.autoconfigure.properties.SubscriptionProperties;
import com.company.platform.queue.domain.model.QueueProviderType;

public interface QueueListenerContainerAdapter {
    QueueProviderType provider();

    void register(
        QueueListenerEndpoint endpoint,
        SubscriptionProperties subscription,
        DestinationProperties destination
    );
}
