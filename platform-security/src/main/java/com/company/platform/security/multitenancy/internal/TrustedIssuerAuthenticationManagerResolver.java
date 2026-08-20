package com.company.platform.security.multitenancy.internal;

import com.company.platform.security.multitenancy.api.TrustedIssuerRepository;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;

public final class TrustedIssuerAuthenticationManagerResolver {
    private static final int MAX_CACHED_ISSUERS = 100;

    private final TrustedIssuerRepository trustedIssuers;
    private final Converter<Jwt, ? extends AbstractAuthenticationToken> authenticationConverter;
    private final Map<String, AuthenticationManager> managers = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, AuthenticationManager> eldest) {
            return size() > MAX_CACHED_ISSUERS;
        }
    };

    public TrustedIssuerAuthenticationManagerResolver(
        TrustedIssuerRepository trustedIssuers,
        Converter<Jwt, ? extends AbstractAuthenticationToken> authenticationConverter
    ) {
        this.trustedIssuers = trustedIssuers;
        this.authenticationConverter = authenticationConverter;
    }

    public AuthenticationManagerResolver<jakarta.servlet.http.HttpServletRequest> create() {
        return new JwtIssuerAuthenticationManagerResolver(this::resolveIssuer);
    }

    private AuthenticationManager resolveIssuer(String issuer) {
        URI issuerUri;
        try {
            issuerUri = URI.create(issuer);
        } catch (IllegalArgumentException exception) {
            return rejectingManager();
        }
        if (!isSafeHttpsIssuer(issuerUri) || !trustedIssuers.isTrusted(issuerUri)) {
            return rejectingManager();
        }
        synchronized (managers) {
            AuthenticationManager cached = managers.get(issuer);
            if (cached != null) {
                return cached;
            }
        }
        AuthenticationManager created = createManager(issuer);
        synchronized (managers) {
            return managers.computeIfAbsent(issuer, ignored -> created);
        }
    }

    private AuthenticationManager createManager(String issuer) {
        var provider = new JwtAuthenticationProvider(JwtDecoders.fromIssuerLocation(issuer));
        provider.setJwtAuthenticationConverter(authenticationConverter);
        return provider::authenticate;
    }

    private static AuthenticationManager rejectingManager() {
        return authentication -> {
            throw new InvalidBearerTokenException("Bearer token issuer is not trusted");
        };
    }

    private static boolean isSafeHttpsIssuer(URI issuer) {
        return "https".equalsIgnoreCase(issuer.getScheme())
            && issuer.getHost() != null
            && issuer.getUserInfo() == null
            && issuer.getQuery() == null
            && issuer.getFragment() == null;
    }
}
