package com.company.platform.cache.autoconfigure.properties;

import com.company.platform.cache.domain.policy.MultiLevelWritePolicy;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Duration;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MultiLevelProperties {
    boolean enabled;
    String l1Store;
    String l2Store;
    Duration l1Ttl = Duration.ofMinutes(5);
    Duration l2Ttl = Duration.ofHours(1);
    boolean populateL1OnL2Hit = true;
    MultiLevelWritePolicy writePolicy =
        MultiLevelWritePolicy.EVICT_L1_THEN_WRITE_L2;
}
