package com.company.platform.integration.kafkaapp.service;

import com.company.platform.integration.kafkaapp.api.ReceivedKafkaMessage;
import com.company.platform.integration.kafkaapp.model.KafkaTestEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaMessageProbeTest {
    @Test
    void returnsLatestFirstAndCanClear() {
        KafkaMessageProbe probe = new KafkaMessageProbe();
        probe.record(message("first", 0L));
        probe.record(message("second", 1L));

        assertThat(probe.latest(1))
            .extracting(ReceivedKafkaMessage::messageId)
            .containsExactly("second");
        assertThat(probe.find("first")).isNotNull();
        assertThat(probe.totalReceived()).isEqualTo(2L);

        probe.clear();

        assertThat(probe.latest(10)).isEmpty();
        assertThat(probe.totalReceived()).isZero();
    }

    private ReceivedKafkaMessage message(String id, long offset) {
        Instant now = Instant.parse("2026-08-02T00:00:00Z");
        return new ReceivedKafkaMessage(
            new KafkaTestEvent(id, "aggregate", "message", now),
            id,
            id,
            "platform.test.events.v1",
            0,
            offset,
            1,
            now);
    }
}
