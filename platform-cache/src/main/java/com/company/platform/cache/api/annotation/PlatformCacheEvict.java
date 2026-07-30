package com.company.platform.cache.api.annotation;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@CacheEvict
public @interface PlatformCacheEvict {
    @AliasFor(annotation = CacheEvict.class, attribute = "cacheNames")
    String[] cacheNames();

    @AliasFor(annotation = CacheEvict.class, attribute = "key")
    String key() default "";

    @AliasFor(annotation = CacheEvict.class, attribute = "allEntries")
    boolean allEntries() default false;

    @AliasFor(annotation = CacheEvict.class, attribute = "beforeInvocation")
    boolean beforeInvocation() default false;
}
