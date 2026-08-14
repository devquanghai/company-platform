package com.company.platform.queue.internal.configuration.properties;

import com.company.platform.queue.api.model.QueueProviderType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "platform.queue")
public class PlatformQueueProperties {
    private boolean enabled = true;
    private QueueProviderType provider = QueueProviderType.KAFKA;
}
