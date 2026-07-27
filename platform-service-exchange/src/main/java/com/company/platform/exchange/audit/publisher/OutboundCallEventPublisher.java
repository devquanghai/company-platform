package com.company.platform.exchange.audit.publisher;

import com.company.platform.exchange.audit.event.OutboundCallEvent;

public interface OutboundCallEventPublisher {
    void publish(OutboundCallEvent event);
}
