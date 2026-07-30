package com.company.platform.queue.application.registry;

import java.lang.reflect.Method;

public record QueueListenerEndpoint(
    String handlerId,
    String subscription,
    Object bean,
    Method method,
    Class<?> payloadType
) {
}
