package com.company.platform.queue.reliability.internal.application;

import com.company.platform.queue.domain.policy.RetryDecision;
import com.company.platform.queue.reliability.retry.MessageFailureContext;
import com.company.platform.queue.reliability.retry.MessageRetryDecisionPolicy;

import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

public final class DefaultMessageRetryDecisionPolicy
    implements MessageRetryDecisionPolicy {

    @Override
    public RetryDecision evaluate(MessageFailureContext context) {
        if (!context.schemaValid() || context.exception() instanceof IllegalArgumentException) {
            return RetryDecision.DEAD_LETTER;
        }
        Throwable failure = context.exception();
        if (failure instanceof SocketTimeoutException
            || failure instanceof TimeoutException) {
            return RetryDecision.RETRY_DELAYED;
        }
        return RetryDecision.REJECT;
    }
}
