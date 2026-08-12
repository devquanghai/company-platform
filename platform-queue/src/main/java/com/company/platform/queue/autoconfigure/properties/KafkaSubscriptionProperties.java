package com.company.platform.queue.autoconfigure.properties;

import com.company.platform.queue.api.kafka.KafkaConsumerMode;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;

@Getter
@Setter
public class KafkaSubscriptionProperties {
    private String groupId;
    private int concurrency = 1;
    private KafkaConsumerMode mode = KafkaConsumerMode.REALTIME;
    private Integer maxMessages;
    private Duration maxWait;
    private Duration deferredPollInterval = Duration.ofSeconds(1);
    private boolean strictOrdering;

    public int getMaxMessages() {
        if (maxMessages != null) {
            return maxMessages;
        }
        return mode == KafkaConsumerMode.BULK ? 100_000
            : mode == KafkaConsumerMode.BATCH ? 500 : 1;
    }

    public Duration getMaxWait() {
        if (maxWait != null) {
            return maxWait;
        }
        return mode == KafkaConsumerMode.BULK
            ? Duration.ofDays(1)
            : mode == KafkaConsumerMode.BATCH ? Duration.ofMinutes(30) : Duration.ZERO;
    }

    /** @deprecated use {@code mode=BATCH}. */
    @Deprecated
    public boolean isBatch() {
        return mode == KafkaConsumerMode.BATCH;
    }

    /** @deprecated use {@code mode}. */
    @Deprecated
    public void setBatch(boolean value) {
        mode = value ? KafkaConsumerMode.BATCH : KafkaConsumerMode.REALTIME;
    }
}
