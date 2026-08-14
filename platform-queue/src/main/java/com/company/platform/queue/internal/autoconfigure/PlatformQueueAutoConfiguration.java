package com.company.platform.queue.internal.autoconfigure;

import com.company.platform.queue.internal.configuration.properties.PlatformQueueProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.ObjectProvider;
import com.company.platform.queue.api.publish.MessagePublisher;

@AutoConfiguration
@EnableConfigurationProperties(PlatformQueueProperties.class)
@ConditionalOnProperty(
    prefix = "platform.queue", name = "enabled",
    havingValue = "true", matchIfMissing = true)
public class PlatformQueueAutoConfiguration {

    @Bean(initMethod = "validate")
    @ConditionalOnMissingBean
    PlatformQueueProviderValidator platformQueueProviderValidator(
        PlatformQueueProperties properties,
        Environment environment,
        ObjectProvider<MessagePublisher> publishers
    ) {
        return new PlatformQueueProviderValidator(properties, environment, publishers);
    }
}
