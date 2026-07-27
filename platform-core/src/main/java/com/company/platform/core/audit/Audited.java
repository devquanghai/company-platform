package com.company.platform.core.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a business operation for structured audit event publication. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {
    String action();

    String businessContext() default "";

    boolean enableDiff() default false;

    /**
     * @deprecated dùng {@link #businessContext()}.
     */
    @Deprecated
    String resource() default "";
}
