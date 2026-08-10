package com.company.platform.queue.consume.internal.application;

public interface PlatformQueueListenerRegistrar {
    void register(QueueListenerEndpoint endpoint);
}
