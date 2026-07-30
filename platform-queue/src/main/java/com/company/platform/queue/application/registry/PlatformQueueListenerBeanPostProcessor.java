package com.company.platform.queue.application.registry;

import com.company.platform.queue.api.annotation.PlatformQueueListener;
import com.company.platform.queue.application.resolver.PlatformQueueListenerMetadataResolver;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;

public final class PlatformQueueListenerBeanPostProcessor
    implements BeanPostProcessor {

    private final PlatformQueueListenerMetadataResolver resolver;
    private final PlatformQueueListenerRegistrar registrar;

    public PlatformQueueListenerBeanPostProcessor(
        PlatformQueueListenerMetadataResolver resolver,
        PlatformQueueListenerRegistrar registrar
    ) {
        this.resolver = resolver;
        this.registrar = registrar;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName)
        throws BeansException {
        Class<?> targetClass = ClassUtils.getUserClass(bean);
        MethodIntrospector.selectMethods(
            targetClass,
            (MethodIntrospector.MetadataLookup<PlatformQueueListener>) method ->
                AnnotatedElementUtils.findMergedAnnotation(
                    method, PlatformQueueListener.class))
            .forEach((method, annotation) -> {
                if (annotation.enabled()) {
                    registrar.register(
                        resolver.resolve(bean, beanName, method, annotation));
                }
            });
        return bean;
    }
}
