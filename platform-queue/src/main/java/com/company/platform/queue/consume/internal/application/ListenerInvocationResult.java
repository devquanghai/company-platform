package com.company.platform.queue.consume.internal.application;

import com.company.platform.queue.api.consume.MessageHandlingResult;
import com.company.platform.queue.domain.policy.RetryDecision;
import java.time.Instant;

public record ListenerInvocationResult(
    MessageHandlingResult result,
    RetryDecision failureDecision,
    String errorCode,
    Instant retryAt
) {
    public static ListenerInvocationResult handled(MessageHandlingResult result) {
        return new ListenerInvocationResult(result, null, null, null);
    }

    public static ListenerInvocationResult failed(RetryDecision decision) {
        return new ListenerInvocationResult(
            null, decision, "QUEUE.LISTENER_PROCESSING_FAILED", null);
    }

    public static ListenerInvocationResult contended(Instant retryAt) {
        return new ListenerInvocationResult(
            MessageHandlingResult.RETRY, null, "QUEUE.INBOX_CONTENDED", retryAt);
    }
}
