package com.company.platform.security.authority.api;

import java.util.Collection;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;

@FunctionalInterface
public interface SecurityAuthorityMapper {
    Collection<? extends GrantedAuthority> map(Map<String, Object> attributes);
}
