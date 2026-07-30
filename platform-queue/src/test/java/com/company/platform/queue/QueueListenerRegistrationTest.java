package com.company.platform.queue;

import com.company.platform.queue.api.annotation.PlatformQueueListener;
import com.company.platform.queue.api.consume.MessageContext;
import com.company.platform.queue.api.consume.MessageHandlingResult;
import com.company.platform.queue.application.registry.DefaultPlatformQueueListenerRegistrar;
import com.company.platform.queue.application.registry.PlatformQueueListenerBeanPostProcessor;
import com.company.platform.queue.application.registry.QueueSubscriptionRegistry;
import com.company.platform.queue.application.resolver.PlatformQueueListenerMetadataResolver;
import com.company.platform.queue.autoconfigure.properties.SubscriptionProperties;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueueListenerRegistrationTest {

    @Test
    void registersValidListenerAndRejectsDuplicate() {
        SubscriptionProperties subscription = new SubscriptionProperties();
        DefaultPlatformQueueListenerRegistrar registrar =
            new DefaultPlatformQueueListenerRegistrar(
                new QueueSubscriptionRegistry(Map.of("orders", subscription)));
        PlatformQueueListenerBeanPostProcessor processor =
            new PlatformQueueListenerBeanPostProcessor(
                new PlatformQueueListenerMetadataResolver(), registrar);
        processor.postProcessAfterInitialization(new ValidListener(), "listener");
        assertThat(registrar.endpoints()).containsKey("listener#handle");
        assertThatThrownBy(() ->
            processor.postProcessAfterInitialization(new ValidListener(), "listener"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("duplicate");
    }

    @Test
    void validatesListenerSignature() throws Exception {
        PlatformQueueListenerMetadataResolver resolver =
            new PlatformQueueListenerMetadataResolver();
        Method method = InvalidListener.class.getDeclaredMethod("handle", String.class);
        PlatformQueueListener annotation =
            method.getAnnotation(PlatformQueueListener.class);
        assertThatThrownBy(() ->
            resolver.resolve(new InvalidListener(), "invalid", method, annotation))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("return");
    }

    static class ValidListener {
        @PlatformQueueListener(subscription = "orders")
        public MessageHandlingResult handle(String payload, MessageContext context) {
            return MessageHandlingResult.ACK;
        }
    }

    static class InvalidListener {
        @PlatformQueueListener(subscription = "orders")
        public void handle(String payload) {
        }
    }
}
