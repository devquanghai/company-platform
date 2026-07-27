package com.company.platform.logging.annotation.masking;

import com.company.platform.logging.domain.model.MaskingType;
import com.company.platform.logging.domain.model.PiiType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Sensitive(
    piiType = PiiType.ACCOUNT_NUMBER,
    masking = MaskingType.PARTIAL,
    visiblePrefix = 2,
    visibleSuffix = 4
)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
public @interface MaskAccountNumber {
}
