package com.company.platform.core.exception.code;

import com.company.platform.core.i18n.I18nKey;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum QueueCode implements I18nKey {

    OPERATION_FAILED("error.queue.operation-failed"),
    PUBLISH_FAILED("error.queue.publish-failed"),
    CONSUME_FAILED("error.queue.consume-failed"),
    CONNECTION_FAILED("error.queue.connection-failed"),
    TOPIC_NOT_FOUND("error.queue.topic-not-found"),
    INVALID_MESSAGE("error.queue.invalid-message"),
    SERIALIZATION_FAILED("error.queue.serialization-failed"),
    DESERIALIZATION_FAILED("error.queue.deserialization-failed"),
    RETRY_EXHAUSTED("error.queue.retry-exhausted"),
    DEAD_LETTER_FAILED("error.queue.dead-letter-failed"),
    DUPLICATE_MESSAGE("error.queue.duplicate-message"),
    PROCESSING_FAILED("error.queue.processing-failed");

    String key;
}
