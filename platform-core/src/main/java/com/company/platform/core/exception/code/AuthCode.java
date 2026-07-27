package com.company.platform.core.exception.code;

import com.company.platform.core.i18n.I18nKey;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum AuthCode implements I18nKey {

    UNAUTHORIZED("error.auth.unauthorized"),
    FORBIDDEN("error.auth.forbidden"),
    ACCESS_DENIED("error.auth.access-denied"),
    INVALID_CREDENTIALS("error.auth.invalid-credentials"),
    INVALID_TOKEN("error.auth.invalid-token"),
    TOKEN_EXPIRED("error.auth.token-expired"),
    TOKEN_MISSING("error.auth.token-missing"),
    TOKEN_REVOKED("error.auth.token-revoked"),
    TOKEN_UNSUPPORTED("error.auth.token-unsupported"),
    AUTHENTICATION_FAILED("error.auth.authentication-failed"),
    ACCOUNT_DISABLED("error.auth.account-disabled"),
    ACCOUNT_EXPIRED("error.auth.account-expired"),
    CREDENTIALS_EXPIRED("error.auth.credentials-expired"),
    ACCOUNT_LOCKED("error.auth.account-locked"),
    ACCOUNT_LOCKED_AFTER_FAILURES("error.auth.account-locked-after-failures"),
    CONTENT_ACCESS_DENIED("error.auth.content-access-denied"),
    FUNCTION_ACCESS_DENIED("error.auth.function-access-denied"),
    SESSION_EXPIRED("error.auth.session-expired");

    String key;
}
