package com.company.platform.core.exception.error;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum ErrorCategory {
    VALIDATION (HttpStatus.BAD_REQUEST, "VALIDATION"),
    AUTHENTICATION (HttpStatus.UNAUTHORIZED, "AUTHENTICATION"),
    AUTHORIZATION (HttpStatus.FORBIDDEN, "AUTHORIZATION"),
    BUSINESS (HttpStatus.BAD_REQUEST, "BUSINESS"),
    CONFLICT (HttpStatus.CONFLICT, "CONFLICT"),
    NOT_FOUND (HttpStatus.NOT_FOUND, "NOT_FOUND"),
    RATE_LIMIT (HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT"),
    INTEGRATION (HttpStatus.BAD_GATEWAY, "INTEGRATION"),
    TIMEOUT (HttpStatus.GATEWAY_TIMEOUT, "TIMEOUT"),
    INFRASTRUCTURE (HttpStatus.INTERNAL_SERVER_ERROR, "INFRASTRUCTURE"),
    INTERNAL (HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL");
    HttpStatus httpStatus;
    String errorCode;
}
