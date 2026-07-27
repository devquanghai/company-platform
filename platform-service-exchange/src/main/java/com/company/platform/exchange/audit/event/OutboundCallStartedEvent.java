package com.company.platform.exchange.audit.event;

public final class OutboundCallStartedEvent extends OutboundCallEvent {
    public OutboundCallStartedEvent(OutboundCallEventData data) {
        super(OutboundEventType.STARTED, data);
    }
}
