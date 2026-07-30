package com.company.platform.queue.application.port.in;

import com.company.platform.queue.application.registry.QueueListenerEndpoint;
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
