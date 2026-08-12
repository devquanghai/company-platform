package com.company.platform.queue.autoconfigure.properties;

import com.company.platform.queue.domain.model.QueueProviderType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BrokerProperties {
    private boolean enabled = true;
    private QueueProviderType provider;
}
