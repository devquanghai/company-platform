package com.company.platform.core.exception.code;

import com.company.platform.core.i18n.I18nKey;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * Compatibility codes for legacy systems.
 *
 * @deprecated Use semantic domain codes such as CommonCode,
 * ValidationCode or AuthCode.
 */
@Getter
@Deprecated
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum LegacyCode implements I18nKey {

    MSG0000("MSG0000"),
    MSG0001("MSG0001"),
    MSG0002("MSG0002"),
    MSG0003("MSG0003"),
    MSG0004("MSG0004"),
    MSG0005("MSG0005"),
    MSG0006("MSG0006"),
    MSG0007("MSG0007"),
    MSG0008("MSG0008"),
    MSG0009("MSG0009"),
    MSG0010("MSG0010"),
    MSG0011("MSG0011");

    String key;
}
