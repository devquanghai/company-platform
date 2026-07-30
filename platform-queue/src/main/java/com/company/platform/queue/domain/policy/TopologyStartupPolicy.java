package com.company.platform.queue.domain.policy;

public enum TopologyStartupPolicy {
    IGNORE,
    VALIDATE,
    DECLARE,
    DECLARE_AND_VALIDATE,
    FAIL_FAST
}
