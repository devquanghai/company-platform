//package com.company.platform.integration;
//
//import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
//import io.github.resilience4j.retry.RetryRegistry;
//import io.github.resilience4j.springboot.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
//import io.github.resilience4j.springboot.retry.autoconfigure.RetryAutoConfiguration;
//import org.junit.jupiter.api.Test;
//import org.springframework.boot.autoconfigure.AutoConfigurations;
//import org.springframework.boot.cache.autoconfigure.CacheAutoConfiguration;
//import org.springframework.boot.test.context.runner.ApplicationContextRunner;
//import org.springframework.cache.annotation.CacheEvict;
//import org.springframework.cache.annotation.Cacheable;
//import org.springframework.cache.annotation.EnableCaching;
//import org.springframework.cache.caffeine.CaffeineCacheManager;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//import java.util.concurrent.atomic.AtomicInteger;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
//class NativeConfigurationBehaviorTest {
//
//    @Test
//    void caffeineCacheUsesBootManagerAndSpringCacheSemantics() {
//        new ApplicationContextRunner()
//            .withConfiguration(AutoConfigurations.of(CacheAutoConfiguration.class))
//            .withUserConfiguration(CacheTestConfiguration.class)
//            .withPropertyValues(
//                "spring.cache.type=caffeine",
//                "spring.cache.cache-names=native-cache",
//                "spring.cache.caffeine.spec=maximumSize=100,expireAfterWrite=1m")
//            .run(context -> {
//                assertThat(context).hasSingleBean(CaffeineCacheManager.class);
//                var service = context.getBean(CountingService.class);
//
//                assertThat(service.load("one")).isEqualTo(1);
//                assertThat(service.load("one")).isEqualTo(1);
//                service.evict("one");
//                assertThat(service.load("one")).isEqualTo(2);
//            });
//    }
//
//    @Test
//    void resilienceInstancesComeFromNativeRegistries() {
//        new ApplicationContextRunner()
//            .withConfiguration(AutoConfigurations.of(
//                CircuitBreakerAutoConfiguration.class,
//                RetryAutoConfiguration.class))
//            .withPropertyValues(
//                "resilience4j.circuitbreaker.instances.echo.sliding-window-size=20",
//                "resilience4j.retry.instances.echo.max-attempts=2",
//                "resilience4j.retry.instances.echo.wait-duration=10ms")
//            .run(context -> {
//                assertThat(context).hasSingleBean(CircuitBreakerRegistry.class);
//                assertThat(context).hasSingleBean(RetryRegistry.class);
//                assertThat(context.getBean(CircuitBreakerRegistry.class)
//                    .circuitBreaker("echo").getCircuitBreakerConfig()
//                    .getSlidingWindowSize()).isEqualTo(20);
//                assertThat(context.getBean(RetryRegistry.class)
//                    .retry("echo").getRetryConfig().getMaxAttempts()).isEqualTo(2);
//            });
//    }
//
//    @Configuration(proxyBeanMethods = false)
//    @EnableCaching
//    static class CacheTestConfiguration {
//        @Bean
//        CountingService countingService() {
//            return new CountingService();
//        }
//    }
//
//    static class CountingService {
//        private final AtomicInteger calls = new AtomicInteger();
//
//        @Cacheable(cacheNames = "native-cache", key = "#key")
//        int load(String key) {
//            return calls.incrementAndGet();
//        }
//
//        @CacheEvict(cacheNames = "native-cache", key = "#key")
//        void evict(String key) {
//        }
//    }
//}
