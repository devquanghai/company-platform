package com.company.platform.security.internal.autoconfigure;

import com.company.platform.security.authority.api.SecurityAuthorityMapper;
import com.company.platform.security.authority.internal.DefaultSecurityAuthorityMapper;
import com.company.platform.security.authority.internal.PlatformJwtAuthoritiesConverter;
import com.company.platform.security.context.internal.JwtSecurityIdentityExtractor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

@AutoConfiguration(after = PlatformSecurityAutoConfiguration.class)
@ConditionalOnClass({Jwt.class, JwtAuthenticationConverter.class})
public class JwtAuthorityAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    JwtSecurityIdentityExtractor jwtSecurityIdentityExtractor() {
        return new JwtSecurityIdentityExtractor();
    }

    @Bean
    @ConditionalOnMissingBean
    SecurityAuthorityMapper securityAuthorityMapper() {
        return new DefaultSecurityAuthorityMapper();
    }

    @Bean
    @ConditionalOnMissingBean
    JwtAuthenticationConverter jwtAuthenticationConverter(SecurityAuthorityMapper authorityMapper) {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new PlatformJwtAuthoritiesConverter(authorityMapper));
        return converter;
    }
}
