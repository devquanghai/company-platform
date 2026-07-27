package com.company.platform.logging.annotation.logging;

import com.company.platform.logging.domain.model.LogCategory;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Loggable(category = LogCategory.AUDIT, audit = true)
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {
    @AliasFor(annotation = Loggable.class, attribute = "event")
    String event() default "";
}
