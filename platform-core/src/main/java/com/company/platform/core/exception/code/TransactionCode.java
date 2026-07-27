package com.company.platform.core.exception.code;

import com.company.platform.core.i18n.I18nKey;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum TransactionCode implements I18nKey {

    FAILED("error.transaction.failed"),
    TIMEOUT("error.transaction.timeout"),
    BLOCKED("error.transaction.blocked"),
    CANCELLED("error.transaction.cancelled"),
    REJECTED("error.transaction.rejected"),
    DUPLICATE("error.transaction.duplicate"),
    INVALID_STATE("error.transaction.invalid-state"),
    NOT_FOUND("error.transaction.not-found"),
    ALREADY_COMPLETED("error.transaction.already-completed"),
    ALREADY_CANCELLED("error.transaction.already-cancelled"),
    PROCESSING("error.transaction.processing"),
    PROCESSING_LATER("error.transaction.processing-later"),
    LIMIT_EXCEEDED("error.transaction.limit-exceeded"),
    INSUFFICIENT_BALANCE("error.transaction.insufficient-balance"),
    STATUS_UNKNOWN("error.transaction.status-unknown");

    String key;
}
