package com.company.platform.core.exception.code;

import com.company.platform.core.i18n.I18nKey;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum RateLimitCode implements I18nKey {

    EXCEEDED("error.rate-limit.exceeded"),
    USER_EXCEEDED("error.rate-limit.user-exceeded"),
    CLIENT_EXCEEDED("error.rate-limit.client-exceeded"),
    IP_EXCEEDED("error.rate-limit.ip-exceeded");

    String key;
}
