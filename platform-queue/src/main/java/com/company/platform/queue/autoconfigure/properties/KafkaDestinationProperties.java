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
    private boolean keyRequired;
    private boolean partitionOverrideAllowed;

    /** @deprecated use {@code key-required}. */
    @Deprecated
    public boolean isRequireKey() { return keyRequired; }

    /** @deprecated use {@code key-required}. */
    @Deprecated
    public void setRequireKey(boolean value) { keyRequired = value; }

    /** @deprecated use {@code partition-override-allowed}. */
    @Deprecated
    public boolean isAllowPartitionOverride() { return partitionOverrideAllowed; }

    /** @deprecated use {@code partition-override-allowed}. */
    @Deprecated
    public void setAllowPartitionOverride(boolean value) {
        partitionOverrideAllowed = value;
    }
}
