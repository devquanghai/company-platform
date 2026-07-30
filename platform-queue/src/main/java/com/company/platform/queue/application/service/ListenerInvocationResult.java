package com.company.platform.queue.application.service;

import com.company.platform.queue.api.consume.MessageHandlingResult;
import com.company.platform.queue.domain.policy.RetryDecision;

public record ListenerInvocationResult(
    MessageHandlingResult result,
    RetryDecision failureDecision,
    String errorCode
) {
    public static ListenerInvocationResult handled(MessageHandlingResult result) {
        return new ListenerInvocationResult(result, null, null);
    }

    public static ListenerInvocationResult failed(RetryDecision decision) {
        return new ListenerInvocationResult(
            null, decision, "QUEUE.LISTENER_PROCESSING_FAILED");
    }
}
