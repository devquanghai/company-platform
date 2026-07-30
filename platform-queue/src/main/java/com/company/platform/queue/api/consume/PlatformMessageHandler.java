package com.company.platform.queue.api.consume;

@FunctionalInterface
public interface PlatformMessageHandler<T> {
    MessageHandlingResult handle(T payload, MessageContext context);
}
