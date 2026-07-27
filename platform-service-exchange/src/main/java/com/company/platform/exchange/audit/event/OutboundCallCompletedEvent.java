package com.company.platform.exchange.audit.event;

public final class OutboundCallCompletedEvent extends OutboundCallEvent {
    public OutboundCallCompletedEvent(OutboundCallEventData data) {
        super(OutboundEventType.COMPLETED, data);
    }
}
