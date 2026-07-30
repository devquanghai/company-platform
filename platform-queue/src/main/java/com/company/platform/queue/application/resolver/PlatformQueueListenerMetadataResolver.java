package com.company.platform.queue.application.resolver;

import com.company.platform.queue.api.annotation.PlatformQueueListener;
import com.company.platform.queue.api.consume.MessageContext;
import com.company.platform.queue.api.consume.MessageHandlingResult;
import com.company.platform.queue.application.registry.QueueListenerEndpoint;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public final class PlatformQueueListenerMetadataResolver {

    public QueueListenerEndpoint resolve(
        Object bean, String beanName, Method method, PlatformQueueListener annotation
    ) {
        if (Modifier.isPrivate(method.getModifiers())) {
            throw new IllegalStateException("queue listener method must not be private");
        }
        if (method.getReturnType() != MessageHandlingResult.class) {
            throw new IllegalStateException(
                "queue listener must return MessageHandlingResult");
        }
        Class<?>[] parameters = method.getParameterTypes();
        if (parameters.length != 2 || parameters[1] != MessageContext.class) {
            throw new IllegalStateException(
                "queue listener parameters must be payload and MessageContext");
        }
        String subscription = annotation.subscription();
        if (subscription.isBlank()) {
            subscription = annotation.destination();
        }
        if (subscription.isBlank()) {
            throw new IllegalStateException("queue listener subscription is required");
        }
        String handlerId = annotation.handlerId().isBlank()
            ? beanName + "#" + method.getName() : annotation.handlerId();
        return new QueueListenerEndpoint(
            handlerId, subscription, bean, method, parameters[0]);
    }
}
