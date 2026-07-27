package com.company.platform.logging.annotation.masking;

import com.company.platform.logging.domain.model.MaskingType;
import com.company.platform.logging.domain.model.PiiType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({
    ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER,
    ElementType.METHOD, ElementType.ANNOTATION_TYPE
})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Sensitive {
    PiiType piiType() default PiiType.GENERIC;
    MaskingType masking() default MaskingType.FULL;
    int visiblePrefix() default 0;
    int visibleSuffix() default 0;
    String substitution() default "***";
    String strategyBean() default "";
    boolean preserveDomain() default false;
}
