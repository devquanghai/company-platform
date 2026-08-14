package com.company.platform.logging.crypto.internal.autoconfigure;

import com.company.platform.logging.api.crypto.PropertyCryptoService;
import com.company.platform.logging.crypto.internal.adapter.JasyptPropertyCryptoService;
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
