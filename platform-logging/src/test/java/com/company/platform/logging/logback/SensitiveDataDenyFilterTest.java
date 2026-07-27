package com.company.platform.logging.logback;

import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.spi.FilterReply;
import com.company.platform.logging.logback.filter.SensitiveDataDenyFilter;
import org.junit.jupiter.api.Test;
import org.slf4j.event.KeyValuePair;

import java.util.Map;
import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveDataDenyFilterTest {
    private final SensitiveDataDenyFilter filter = new SensitiveDataDenyFilter();

    @Test
    void acceptsSafeEventsAndNullInput() {
        LoggingEvent event = event("Operation completed", Map.of("traceId", "trace-1"));
        event.addKeyValuePair(new KeyValuePair("count", 2));

        assertThat(filter.decide(null)).isEqualTo(FilterReply.NEUTRAL);
        assertThat(filter.decide(event)).isEqualTo(FilterReply.NEUTRAL);
    }

    @Test
    void rejectsSecretsAcrossMessageMdcKeyValuesAndThrowable() {
        assertThat(filter.decide(event("{\"password\":\"sentinel\"}")))
            .isEqualTo(FilterReply.DENY);

        LoggingEvent mdc = event("safe", Map.of("authorization", "Bearer sentinel"));
        assertThat(filter.decide(mdc)).isEqualTo(FilterReply.DENY);

        LoggingEvent keyValue = event("safe");
        keyValue.addKeyValuePair(new KeyValuePair("api-key", "sentinel"));
        assertThat(filter.decide(keyValue)).isEqualTo(FilterReply.DENY);

        LoggingEvent object = event("safe");
        object.addKeyValuePair(new KeyValuePair("payload", new Object()));
        assertThat(filter.decide(object)).isEqualTo(FilterReply.DENY);

        RuntimeException cause = new RuntimeException("password=sentinel");
        IllegalStateException failure = new IllegalStateException("safe", cause);
        failure.addSuppressed(new IllegalArgumentException("safe"));
        LoggingEvent throwable = event("safe");
        throwable.setThrowableProxy(new ThrowableProxy(failure));
        assertThat(filter.decide(throwable)).isEqualTo(FilterReply.DENY);
    }

    @Test
    void acceptsEveryScalarTypeNullValuesSafeCausesAndDeepThrowableGraphs() {
        LoggingEvent noPairs = event(null);
        assertThat(filter.decide(noPairs)).isEqualTo(FilterReply.NEUTRAL);

        LinkedHashMap<String, String> nullableMdc = new LinkedHashMap<>();
        nullableMdc.put("optional", null);
        LoggingEvent scalar = event("safe", nullableMdc);
        scalar.addKeyValuePair(new KeyValuePair("text", new StringBuilder("safe")));
        scalar.addKeyValuePair(new KeyValuePair("number", 12));
        scalar.addKeyValuePair(new KeyValuePair("boolean", true));
        scalar.addKeyValuePair(new KeyValuePair("enum", FilterReply.NEUTRAL));
        scalar.addKeyValuePair(new KeyValuePair("nullable", null));
        assertThat(filter.decide(scalar)).isEqualTo(FilterReply.NEUTRAL);

        RuntimeException deep = new RuntimeException("safe");
        for (int index = 0; index < 10; index++) {
            deep = new RuntimeException("safe", deep);
        }
        deep.addSuppressed(new IllegalArgumentException("safe"));
        LoggingEvent deepEvent = event("safe");
        deepEvent.setThrowableProxy(new ThrowableProxy(deep));
        assertThat(filter.decide(deepEvent)).isEqualTo(FilterReply.NEUTRAL);
    }

    @Test
    void rejectsSecretInSuppressedThrowableAndUnsafeScalarText() {
        IllegalStateException failure = new IllegalStateException("safe");
        failure.addSuppressed(new IllegalArgumentException("api-key=sentinel"));
        LoggingEvent throwable = event("safe");
        throwable.setThrowableProxy(new ThrowableProxy(failure));
        assertThat(filter.decide(throwable)).isEqualTo(FilterReply.DENY);

        LoggingEvent text = event("safe");
        text.addKeyValuePair(new KeyValuePair("note", "password=sentinel"));
        assertThat(filter.decide(text)).isEqualTo(FilterReply.DENY);
    }

    private static LoggingEvent event(String message) {
        return event(message, Map.of());
    }

    private static LoggingEvent event(String message, Map<String, String> mdc) {
        LoggingEvent event = new LoggingEvent();
        event.setMessage(message);
        event.setMDCPropertyMap(mdc);
        return event;
    }
}
