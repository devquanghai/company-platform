package com.company.platform.queue.reliability.retry;

import com.company.platform.queue.domain.policy.RetryDecision;

@FunctionalInterface
public interface MessageRetryDecisionPolicy {
    RetryDecision evaluate(MessageFailureContext context);
}
