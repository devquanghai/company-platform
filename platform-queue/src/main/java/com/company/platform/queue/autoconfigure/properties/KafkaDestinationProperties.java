package com.company.platform.queue.autoconfigure.properties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KafkaDestinationProperties {
    private String topic;
    private int partitions = 1;
    private short replicationFactor = 1;
    private boolean compacted;
    private boolean requireKey;
    private boolean allowPartitionOverride;
}
