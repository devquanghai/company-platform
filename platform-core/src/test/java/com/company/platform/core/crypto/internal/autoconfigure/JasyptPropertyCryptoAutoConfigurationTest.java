package com.company.platform.core.crypto.internal.autoconfigure;

import com.company.platform.core.crypto.api.PropertyCryptoService;
import org.jasypt.encryption.StringEncryptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class JasyptPropertyCryptoAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(JasyptPropertyCryptoAutoConfiguration.class))
        .withBean("jasyptStringEncryptor", StringEncryptor.class, TestStringEncryptor::new);

    @Test
    void createsPropertyCryptoServiceForSingleEncryptor() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(PropertyCryptoService.class));
    }

    @Test
    void usesStarterEncryptorWhenApplicationAlsoDefinesCustomEncryptor() {
        contextRunner
            .withBean("applicationEncryptor", StringEncryptor.class, TestStringEncryptor::new)
            .run(context -> assertThat(context).hasSingleBean(PropertyCryptoService.class));
    }

    @Test
    void backsOffForApplicationPropertyCryptoService() {
        contextRunner
            .withUserConfiguration(ApplicationCryptoConfiguration.class)
            .run(context -> {
                assertThat(context).hasSingleBean(PropertyCryptoService.class);
                assertThat(context.getBean(PropertyCryptoService.class))
                    .isSameAs(context.getBean("applicationPropertyCryptoService"));
            });
    }

    @Configuration(proxyBeanMethods = false)
    static class ApplicationCryptoConfiguration {
        @Bean
        PropertyCryptoService applicationPropertyCryptoService() {
            return new PropertyCryptoService() {
                @Override public String encrypt(String value) { return value; }
                @Override public String encryptAndWrap(String value) { return value; }
                @Override public String decrypt(String value) { return value; }
                @Override public boolean isEncrypted(String value) { return false; }
            };
        }
    }

    private static final class TestStringEncryptor implements StringEncryptor {
        @Override public String encrypt(String message) { return message; }
        @Override public String decrypt(String encryptedMessage) { return encryptedMessage; }
    }
}
