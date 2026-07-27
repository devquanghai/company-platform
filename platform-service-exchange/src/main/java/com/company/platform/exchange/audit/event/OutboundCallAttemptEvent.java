package com.company.platform.exchange.audit.event;

public final class OutboundCallAttemptEvent extends OutboundCallEvent {
    public OutboundCallAttemptEvent(OutboundCallEventData data) {
        super(OutboundEventType.ATTEMPT, data);
    }
}
