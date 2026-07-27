package com.company.platform.logging.annotation.crypto;

import com.company.platform.logging.domain.model.CryptoAlgorithm;
import com.company.platform.logging.domain.model.CryptoProviderType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DecryptResult {
    CryptoProviderType provider() default CryptoProviderType.JCA;
    CryptoAlgorithm algorithm() default CryptoAlgorithm.AES_GCM_256;
    String keyAlias();
    String strategyBean() default "";
}
