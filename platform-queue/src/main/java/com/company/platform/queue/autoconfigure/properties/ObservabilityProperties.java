package com.company.platform.queue.autoconfigure.properties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ObservabilityProperties {
    private boolean metricsEnabled = true;
    private boolean tracingEnabled = true;
    private boolean healthEnabled = true;
    private boolean auditEventsEnabled = true;
}
