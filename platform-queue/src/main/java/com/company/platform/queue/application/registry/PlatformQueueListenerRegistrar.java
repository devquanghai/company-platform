package com.company.platform.queue.application.registry;

public interface PlatformQueueListenerRegistrar {
    void register(QueueListenerEndpoint endpoint);
}
