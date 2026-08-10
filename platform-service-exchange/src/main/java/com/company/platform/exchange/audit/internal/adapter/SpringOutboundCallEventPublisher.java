package com.company.platform.exchange.audit.internal.adapter;

import com.company.platform.exchange.audit.event.OutboundCallEvent;
import com.company.platform.exchange.audit.publisher.OutboundCallEventPublisher;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Objects;

public final class SpringOutboundCallEventPublisher implements OutboundCallEventPublisher {

    private final ApplicationEventPublisher publisher;

    public SpringOutboundCallEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = Objects.requireNonNull(publisher, "publisher");
    }

    @Override
    public void publish(OutboundCallEvent event) {
        publisher.publishEvent(event);
    }
}
