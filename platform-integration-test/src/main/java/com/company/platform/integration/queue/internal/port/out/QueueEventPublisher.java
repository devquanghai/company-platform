package com.company.platform.integration.queue.internal.port.out;

import com.company.platform.integration.queue.internal.domain.QueueDemoEvent;
import com.company.platform.integration.queue.internal.domain.QueueMode;
import com.company.platform.queue.api.publish.PublishResult;

public interface QueueEventPublisher {
    QueueMode mode();
    PublishResult publish(QueueDemoEvent event);
}
