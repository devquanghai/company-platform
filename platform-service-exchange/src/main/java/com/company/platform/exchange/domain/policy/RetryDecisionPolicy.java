package com.company.platform.exchange.domain.policy;

public interface RetryDecisionPolicy {
    RetryDecision evaluate(RetryContext context);
}
