package com.company.platform.exchange.audit.event;

public final class OutboundCallFailedEvent extends OutboundCallEvent {
    public OutboundCallFailedEvent(OutboundCallEventData data) {
        super(OutboundEventType.FAILED, data);
    }
}
