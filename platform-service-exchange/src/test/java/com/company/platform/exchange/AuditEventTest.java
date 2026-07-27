package com.company.platform.exchange;

import com.company.platform.exchange.audit.event.OutboundCallCompletedEvent;
import com.company.platform.exchange.audit.event.OutboundCallEventData;
import com.company.platform.exchange.audit.event.OutboundEventType;
import com.company.platform.exchange.domain.model.ExchangeProtocol;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditEventTest {

    @Test
    void exposesImmutableCompleteEventData() {
        OutboundCallEventData data = OutboundCallEventData.builder()
            .clientName("client").protocol(ExchangeProtocol.HTTP)
            .operation("GET /a").success(true).attemptCount(2).retryCount(1)
            .customAttributes(Map.of("safe", true)).build();
        OutboundCallCompletedEvent event = new OutboundCallCompletedEvent(data);

        assertThat(event.type()).isEqualTo(OutboundEventType.COMPLETED);
        assertThat(event.data()).isSameAs(data);
        assertThat(data.getEventId()).isNotBlank();
        assertThat(data.getClientName()).isEqualTo("client");
        assertThat(data.getCustomAttributes()).containsEntry("safe", true);
    }
}
