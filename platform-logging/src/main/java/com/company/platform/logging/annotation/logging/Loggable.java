package com.company.platform.logging.annotation.logging;

import com.company.platform.logging.domain.model.LogCategory;
import com.company.platform.logging.domain.model.LogSeverity;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Loggable {
    String event() default "";
    LogSeverity level() default LogSeverity.INFO;
    LogCategory category() default LogCategory.APPLICATION;
    boolean includeArguments() default false;
    boolean includeResult() default false;
    boolean includeDuration() default true;
    boolean includeException() default true;
    boolean audit() default false;
}
