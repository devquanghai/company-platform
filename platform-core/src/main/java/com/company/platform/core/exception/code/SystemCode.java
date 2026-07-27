package com.company.platform.core.exception.code;

import com.company.platform.core.i18n.I18nKey;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum SystemCode implements I18nKey {

    INITIALIZATION_FAILED("error.system.initialization-failed"),
    SHUTDOWN_IN_PROGRESS("error.system.shutdown-in-progress"),
    MAINTENANCE("error.system.maintenance"),
    OVERLOADED("error.system.overloaded"),
    PROCESSING("error.system.processing"),
    DEPENDENCY_UNAVAILABLE("error.system.dependency-unavailable"),
    CLOCK_INVALID("error.system.clock-invalid"),
    FEATURE_UNAVAILABLE("error.system.feature-unavailable");

    String key;
}
