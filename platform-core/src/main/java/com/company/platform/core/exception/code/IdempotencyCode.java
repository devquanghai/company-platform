package com.company.platform.core.exception.code;

import com.company.platform.core.i18n.I18nKey;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum IdempotencyCode implements I18nKey {

    KEY_MISSING("error.idempotency.key-missing"),
    KEY_INVALID("error.idempotency.key-invalid"),
    REQUEST_CONFLICT("error.idempotency.request-conflict"),
    REQUEST_PROCESSING("error.idempotency.request-processing"),
    RESULT_UNAVAILABLE("error.idempotency.result-unavailable");

    String key;
}
