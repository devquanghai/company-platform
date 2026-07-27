package com.company.platform.core.exception.code;

import com.company.platform.core.i18n.I18nKey;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum SecurityCode implements I18nKey {

    VIOLATION("error.security.violation"),
    REQUEST_BLOCKED("error.security.request-blocked"),
    INVALID_SIGNATURE("error.security.invalid-signature"),
    SIGNATURE_EXPIRED("error.security.signature-expired"),
    ENCRYPTION_FAILED("error.security.encryption-failed"),
    DECRYPTION_FAILED("error.security.decryption-failed"),
    INVALID_API_KEY("error.security.invalid-api-key"),
    API_KEY_EXPIRED("error.security.api-key-expired"),
    API_KEY_MISSING("error.security.api-key-missing"),
    IP_NOT_ALLOWED("error.security.ip-not-allowed"),
    ORIGIN_NOT_ALLOWED("error.security.origin-not-allowed"),
    CSRF_INVALID("error.security.csrf-invalid");

    String key;
}
