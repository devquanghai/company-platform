package com.company.platform.cache.domain.policy;

public enum MultiLevelWritePolicy {
    L2_THEN_L1,
    EVICT_L1_THEN_WRITE_L2,
    EVICT_BOTH,
    WRITE_BOTH
}
