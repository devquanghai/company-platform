package com.company.platform.core.crypto.internal.autoconfigure;

import com.company.platform.core.crypto.api.PropertyCryptoService;
import com.company.platform.core.crypto.internal.adapter.jasypt.JasyptPropertyCryptoService;
import org.jasypt.encryption.StringEncryptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(afterName =
    "com.ulisesbocchio.jasyptspringbootstarter.JasyptSpringBootAutoConfiguration")
@ConditionalOnClass(StringEncryptor.class)
public class JasyptPropertyCryptoAutoConfiguration {
    @Bean
    @ConditionalOnSingleCandidate(StringEncryptor.class)
    @ConditionalOnMissingBean(PropertyCryptoService.class)
    PropertyCryptoService propertyCryptoService(StringEncryptor encryptor) {
        return new JasyptPropertyCryptoService(encryptor);
    }
}
