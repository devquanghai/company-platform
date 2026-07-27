package com.company.platform.exchange.audit.event;

import java.util.Objects;

public abstract class OutboundCallEvent {

    private final OutboundEventType type;
    private final OutboundCallEventData data;

    protected OutboundCallEvent(OutboundEventType type, OutboundCallEventData data) {
        this.type = Objects.requireNonNull(type, "type");
        this.data = Objects.requireNonNull(data, "data");
    }

    public OutboundEventType type() { return type; }
    public OutboundCallEventData data() { return data; }
}
