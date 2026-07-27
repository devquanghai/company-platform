package com.company.platform.exchange.autoconfigure.properties;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import static lombok.AccessLevel.PRIVATE;

@Getter
@Setter
@FieldDefaults(level = PRIVATE)
public class AuditProperties {

    boolean enabled = true;
    boolean publishAttemptEvents;
    AuditFailureMode failureMode = AuditFailureMode.FAIL_OPEN;
    boolean payloadHashEnabled = true;
    boolean payloadEnabled;
    int maxPayloadLength = 2048;
}
