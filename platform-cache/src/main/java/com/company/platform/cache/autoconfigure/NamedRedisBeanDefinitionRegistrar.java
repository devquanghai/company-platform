package com.company.platform.cache.autoconfigure;

import com.company.platform.cache.adapter.redis.RedisConnectionFactoryBuilder;
import com.company.platform.cache.autoconfigure.properties.CacheStoreProperties;
import com.company.platform.cache.autoconfigure.properties.PlatformCacheProperties;
import com.company.platform.cache.domain.model.CacheProviderType;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;

import java.util.Map;

public final class NamedRedisBeanDefinitionRegistrar
    implements ImportBeanDefinitionRegistrar, EnvironmentAware {

    public static final String BEAN_PREFIX = "platformCacheRedisConnectionFactory__";
    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void registerBeanDefinitions(
        AnnotationMetadata importingClassMetadata,
        BeanDefinitionRegistry registry
    ) {
        PlatformCacheProperties properties = Binder.get(environment)
            .bind("platform.cache", Bindable.of(PlatformCacheProperties.class))
            .orElseGet(PlatformCacheProperties::new);
        if (!properties.isEnabled()) {
            return;
        }
        for (Map.Entry<String, CacheStoreProperties> entry
            : properties.getStores().entrySet()) {
            CacheStoreProperties store = entry.getValue();
            if (store == null || !store.isEnabled()
                || store.getProvider() != CacheProviderType.REDIS
                || hasText(store.getConnectionFactoryBean())) {
                continue;
            }
            String beanName = beanName(entry.getKey());
            if (registry.containsBeanDefinition(beanName)) {
                continue;
            }
            RootBeanDefinition definition = new RootBeanDefinition(
                org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory.class,
                () -> new RedisConnectionFactoryBuilder().build(store.getRedis()));
            definition.setRole(RootBeanDefinition.ROLE_INFRASTRUCTURE);
            registry.registerBeanDefinition(beanName, definition);
        }
    }

    public static String beanName(String storeName) {
        return BEAN_PREFIX + storeName;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
