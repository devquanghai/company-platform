package com.company.platform.security.showcase;

import static org.springframework.security.config.Customizer.withDefaults;

import com.company.platform.security.apikey.api.ApiKeyAuthenticationFilterFactory;
import com.company.platform.security.apikey.api.ApiKeyPrincipal;
import com.company.platform.security.apikey.api.ApiKeyValidator;
import com.company.platform.security.multitenancy.api.TrustedIssuerRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class ShowcaseSecurityConfiguration {
    @Bean
    @Order(0)
    SecurityFilterChain publicEndpoints(HttpSecurity http) throws Exception {
        http.securityMatcher("/public/**")
            .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    @Order(100)
    @Profile("!authorization-server")
    SecurityFilterChain denyByDefault(HttpSecurity http, AuthenticationEntryPoint entryPoint,
                                      AccessDeniedHandler deniedHandler) throws Exception {
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize.anyRequest().denyAll())
            .exceptionHandling(errors -> errors.authenticationEntryPoint(entryPoint)
                .accessDeniedHandler(deniedHandler));
        return http.build();
    }

    @Bean
    @Order(Integer.MIN_VALUE)
    @Profile("authorization-server")
    SecurityFilterChain authorizationServer(HttpSecurity http) throws Exception {
        var html = new MediaTypeRequestMatcher(MediaType.TEXT_HTML);
        html.setIgnoredMediaTypes(Set.of(MediaType.ALL));
        http.oauth2AuthorizationServer(server -> {
                http.securityMatcher(server.getEndpointsMatcher());
                server.oidc(withDefaults());
            })
            .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
            .oauth2ResourceServer(resource -> resource.jwt(withDefaults()))
            .exceptionHandling(errors -> errors.defaultAuthenticationEntryPointFor(
                new LoginUrlAuthenticationEntryPoint("/login"), html));
        return http.build();
    }

    @Bean
    @Order(100)
    @Profile("authorization-server")
    SecurityFilterChain authorizationServerLogin(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
            .formLogin(withDefaults());
        return http.build();
    }

    @Bean
    @Order(10)
    @Profile("resource-server")
    SecurityFilterChain jwtResourceServer(HttpSecurity http, JwtAuthenticationConverter converter,
                                          AuthenticationEntryPoint entryPoint,
                                          AccessDeniedHandler deniedHandler) throws Exception {
        http.securityMatcher("/api/**")
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/api/admin").hasRole("ADMIN")
                .anyRequest().authenticated())
            .exceptionHandling(errors -> errors.authenticationEntryPoint(entryPoint)
                .accessDeniedHandler(deniedHandler))
            .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)));
        return http.build();
    }

    @Bean
    @Order(10)
    @Profile("opaque-token")
    SecurityFilterChain opaqueResourceServer(HttpSecurity http, AuthenticationEntryPoint entryPoint,
                                             AccessDeniedHandler deniedHandler) throws Exception {
        http.securityMatcher("/api/**")
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
            .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
            .exceptionHandling(errors -> errors.authenticationEntryPoint(entryPoint)
                .accessDeniedHandler(deniedHandler))
            .oauth2ResourceServer(resource -> resource.opaqueToken(withDefaults()));
        return http.build();
    }

    @Bean
    @Order(10)
    @Profile("multi-tenant")
    SecurityFilterChain multiTenantResourceServer(
        HttpSecurity http,
        AuthenticationManagerResolver<HttpServletRequest> authenticationManagers,
        AuthenticationEntryPoint entryPoint,
        AccessDeniedHandler deniedHandler
    ) throws Exception {
        http.securityMatcher("/api/**")
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
            .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
            .exceptionHandling(errors -> errors.authenticationEntryPoint(entryPoint)
                .accessDeniedHandler(deniedHandler))
            .oauth2ResourceServer(resource -> resource.authenticationManagerResolver(authenticationManagers));
        return http.build();
    }

    @Bean
    @Order(5)
    @Profile("api-key")
    SecurityFilterChain apiKey(HttpSecurity http, ApiKeyAuthenticationFilterFactory filters,
                               AuthenticationEntryPoint entryPoint,
                               AccessDeniedHandler deniedHandler) throws Exception {
        var matcher = PathPatternRequestMatcher.pathPattern("/internal/**");
        http.securityMatcher(matcher)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf.ignoringRequestMatchers(matcher))
            .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
            .exceptionHandling(errors -> errors.authenticationEntryPoint(entryPoint)
                .accessDeniedHandler(deniedHandler))
            .addFilterBefore(filters.create(matcher), BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(6)
    @Profile("basic")
    SecurityFilterChain basic(HttpSecurity http, AuthenticationEntryPoint entryPoint,
                              AccessDeniedHandler deniedHandler) throws Exception {
        http.securityMatcher("/basic/**")
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf.ignoringRequestMatchers("/basic/**"))
            .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
            .exceptionHandling(errors -> errors.authenticationEntryPoint(entryPoint)
                .accessDeniedHandler(deniedHandler))
            .httpBasic(withDefaults());
        return http.build();
    }

    @Bean
    @Order(20)
    @Profile("oidc-login")
    SecurityFilterChain oidcLogin(HttpSecurity http) throws Exception {
        http.securityMatcher("/web/**", "/oauth2/**", "/login/**")
            .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
            .oauth2Login(withDefaults());
        return http.build();
    }

    @Bean
    @Profile("api-key")
    ApiKeyValidator developmentApiKeyValidator(@Value("${DEMO_API_KEY:demo-only-change-me}") String demoKey) {
        // DEVELOPMENT ONLY. Production validators must compare a strong stored hash or delegate to IAM/Vault.
        byte[] expectedHash = sha256(demoKey);
        return rawKey -> {
            if (!MessageDigest.isEqual(expectedHash, sha256(rawKey))) {
                throw new BadCredentialsException("Invalid API key");
            }
            return new ApiKeyPrincipal("demo-key-1", "showcase-client", "tenant-a",
                Set.of("ROLE_INTERNAL"), Set.of("internal.read"),
                Instant.now().plus(1, ChronoUnit.HOURS), Map.of("environment", "demo"));
        };
    }

    @Bean
    @Profile("multi-tenant")
    TrustedIssuerRepository developmentTrustedIssuers() {
        Set<URI> issuers = Set.of(
            URI.create("https://identity.example.com/realms/tenant-a"),
            URI.create("https://identity.example.com/realms/tenant-b"));
        return issuers::contains;
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK SHA-256 support is unavailable", exception);
        }
    }
}
