package com.company.platform.cache.api.annotation;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Cacheable
public @interface PlatformCacheable {
    @AliasFor(annotation = Cacheable.class, attribute = "cacheNames")
    String[] cacheNames();

    @AliasFor(annotation = Cacheable.class, attribute = "key")
    String key() default "";

    @AliasFor(annotation = Cacheable.class, attribute = "condition")
    String condition() default "";

    @AliasFor(annotation = Cacheable.class, attribute = "unless")
    String unless() default "";
}
