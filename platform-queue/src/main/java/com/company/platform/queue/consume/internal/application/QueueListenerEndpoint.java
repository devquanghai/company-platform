package com.company.platform.queue.consume.internal.application;

import java.lang.reflect.Method;

public record QueueListenerEndpoint(
    String handlerId,
    String subscription,
    Object bean,
    Method method,
    Class<?> payloadType
) {
}
