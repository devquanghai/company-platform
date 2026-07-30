package com.company.platform.queue.autoconfigure.properties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KafkaSubscriptionProperties {
    private String groupId;
    private int concurrency = 1;
    private boolean batch;
    private boolean transactionEnabled;
    private boolean strictOrdering;
    private String autoOffsetReset = "earliest";
}
