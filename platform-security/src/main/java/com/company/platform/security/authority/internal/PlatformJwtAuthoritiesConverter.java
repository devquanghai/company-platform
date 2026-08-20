package com.company.platform.security.authority.internal;

import com.company.platform.security.authority.api.SecurityAuthorityMapper;
import java.util.Collection;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

public final class PlatformJwtAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
    private final SecurityAuthorityMapper mapper;

    public PlatformJwtAuthoritiesConverter(SecurityAuthorityMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Collection<GrantedAuthority> convert(Jwt source) {
        return mapper.map(source.getClaims()).stream().map(GrantedAuthority.class::cast).toList();
    }
}
