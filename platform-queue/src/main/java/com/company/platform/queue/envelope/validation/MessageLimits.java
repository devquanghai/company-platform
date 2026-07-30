package com.company.platform.queue.envelope.validation;

public record MessageLimits(
    int maxHeaders,
    int maxHeaderBytes,
    int maxTotalHeaderBytes,
    int maxPayloadBytes,
    int maxEnvelopeBytes
) {
    public static final MessageLimits HARD_MAX =
        new MessageLimits(128, 16 * 1024, 64 * 1024, 8 * 1024 * 1024, 10 * 1024 * 1024);

    public static final MessageLimits DEFAULT =
        new MessageLimits(64, 8 * 1024, 32 * 1024, 1024 * 1024, 2 * 1024 * 1024);

    public MessageLimits {
        if (maxHeaders < 1 || maxHeaderBytes < 1 || maxTotalHeaderBytes < 1
            || maxPayloadBytes < 1 || maxEnvelopeBytes < maxPayloadBytes) {
            throw new IllegalArgumentException("message limits must be positive and consistent");
        }
        if (HARD_MAX != null && (maxHeaders > HARD_MAX.maxHeaders
            || maxHeaderBytes > HARD_MAX.maxHeaderBytes
            || maxTotalHeaderBytes > HARD_MAX.maxTotalHeaderBytes
            || maxPayloadBytes > HARD_MAX.maxPayloadBytes
            || maxEnvelopeBytes > HARD_MAX.maxEnvelopeBytes)) {
            throw new IllegalArgumentException("message limits exceed hard maximum");
        }
    }
}
