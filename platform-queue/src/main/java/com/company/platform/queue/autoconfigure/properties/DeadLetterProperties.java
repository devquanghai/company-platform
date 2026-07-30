package com.company.platform.queue.autoconfigure.properties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeadLetterProperties {
    private boolean enabled = true;
    private String destination;
    private boolean parkingLotEnabled;
    private String parkingLotDestination;
    private boolean publishOriginalMessage = true;
    private int maxExceptionMessageLength = 512;
}
