package com.company.platform.logging.annotation.masking;

import com.company.platform.logging.domain.model.MaskingType;
import com.company.platform.logging.domain.model.PiiType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Sensitive(piiType = PiiType.CARD_NUMBER, masking = MaskingType.PARTIAL,
    visibleSuffix = 4)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaskCardNumber {
}
