package com.company.platform.cache.autoconfigure;

import com.company.platform.cache.api.crypto.PropertyCryptoService;
import com.company.platform.cache.internal.crypto.JasyptPropertyCryptoService;
import org.jasypt.encryption.StringEncryptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(afterName =
    "com.ulisesbocchio.jasyptspringbootstarter.JasyptSpringBootAutoConfiguration")
@ConditionalOnClass(StringEncryptor.class)
public class JasyptPlatformAutoConfiguration {

    @Bean
    @ConditionalOnSingleCandidate(StringEncryptor.class)
    @ConditionalOnMissingBean(PropertyCryptoService.class)
    PropertyCryptoService propertyCryptoService(StringEncryptor stringEncryptor) {
        return new JasyptPropertyCryptoService(stringEncryptor);
    }
}
