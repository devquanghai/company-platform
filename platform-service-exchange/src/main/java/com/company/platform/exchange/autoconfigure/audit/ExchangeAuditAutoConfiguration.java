package com.company.platform.exchange.autoconfigure.audit;

import com.company.platform.exchange.audit.internal.adapter.SpringOutboundCallEventPublisher;
import com.company.platform.exchange.audit.publisher.OutboundCallEventPublisher;
import com.company.platform.exchange.autoconfigure.PlatformServiceExchangeAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = PlatformServiceExchangeAutoConfiguration.class)
@ConditionalOnProperty(
    prefix = "platform.service-exchange", name = "enabled",
    havingValue = "true", matchIfMissing = true)
public class ExchangeAuditAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OutboundCallEventPublisher outboundCallEventPublisher(
        ApplicationEventPublisher publisher
    ) {
        return new SpringOutboundCallEventPublisher(publisher);
    }
}
