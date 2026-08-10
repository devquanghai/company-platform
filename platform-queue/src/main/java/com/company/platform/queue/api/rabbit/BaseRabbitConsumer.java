package com.company.platform.queue.api.rabbit;

import com.company.platform.queue.api.consume.MessageContext;
import com.company.platform.queue.api.consume.MessageHandlingResult;

import java.lang.reflect.Method;
import java.util.Objects;

/** Provider-specific consumer contract; applications may use Spring @RabbitListener directly. */
public abstract class BaseRabbitConsumer<T> {
    private static final Method HANDLER_METHOD = handlerMethod();
    private final String subscription;
    private final String handlerId;
    private final Class<T> payloadType;

    protected BaseRabbitConsumer(String subscription, Class<T> payloadType) {
        this(subscription, null, payloadType);
    }

    protected BaseRabbitConsumer(String subscription, String handlerId, Class<T> payloadType) {
        this.subscription = requireText(subscription, "subscription");
        this.handlerId = handlerId == null || handlerId.isBlank()
            ? getClass().getName() : handlerId;
        this.payloadType = Objects.requireNonNull(payloadType, "payloadType");
    }

    public final String subscription() { return subscription; }
    public final String handlerId() { return handlerId; }
    public final Class<T> payloadType() { return payloadType; }
    public final Method invocationMethod() { return HANDLER_METHOD; }

    public final MessageHandlingResult handleMessage(Object payload, MessageContext context) {
        return receive(payloadType.cast(payload), Objects.requireNonNull(context, "context"));
    }

    protected abstract MessageHandlingResult receive(T payload, MessageContext context);

    private static Method handlerMethod() {
        try {
            return BaseRabbitConsumer.class.getMethod(
                "handleMessage", Object.class, MessageContext.class);
        } catch (NoSuchMethodException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
