package com.company.platform.logging.crypto.internal.autoconfigure;

import com.company.platform.logging.api.crypto.PropertyCryptoService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(afterName =
    "com.company.platform.core.crypto.internal.autoconfigure.JasyptPropertyCryptoAutoConfiguration")
public class PropertyCryptoCompatibilityAutoConfiguration {

    @Bean
    @ConditionalOnBean(com.company.platform.core.crypto.api.PropertyCryptoService.class)
    @ConditionalOnMissingBean(PropertyCryptoService.class)
    PropertyCryptoService loggingPropertyCryptoServiceCompatibility(
        com.company.platform.core.crypto.api.PropertyCryptoService delegate
    ) {
        return new CorePropertyCryptoServiceAdapter(delegate);
    }

    @SuppressWarnings("removal")
    private record CorePropertyCryptoServiceAdapter(
        com.company.platform.core.crypto.api.PropertyCryptoService delegate
    ) implements PropertyCryptoService {
        @Override public String encrypt(String value) { return delegate.encrypt(value); }
        @Override public String encryptAndWrap(String value) { return delegate.encryptAndWrap(value); }
        @Override public String decrypt(String value) { return delegate.decrypt(value); }
        @Override public boolean isEncrypted(String value) { return delegate.isEncrypted(value); }
    }
}
