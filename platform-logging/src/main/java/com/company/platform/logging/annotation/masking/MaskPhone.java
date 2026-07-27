package com.company.platform.logging.annotation.masking;

import com.company.platform.logging.domain.model.MaskingType;
import com.company.platform.logging.domain.model.PiiType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Sensitive(piiType = PiiType.PHONE, masking = MaskingType.PARTIAL,
    visiblePrefix = 3, visibleSuffix = 2)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaskPhone {
}
