package com.company.platform.security.internal.autoconfigure;

import com.company.platform.security.context.internal.OAuth2SecurityIdentityExtractor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;

@AutoConfiguration(after = PlatformSecurityAutoConfiguration.class)
@ConditionalOnClass(OAuth2AuthenticationToken.class)
public class OAuth2IdentityAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    OAuth2SecurityIdentityExtractor oauth2SecurityIdentityExtractor() {
        return new OAuth2SecurityIdentityExtractor();
    }
}
