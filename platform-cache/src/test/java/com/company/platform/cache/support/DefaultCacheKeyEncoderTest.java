package com.company.platform.cache.support;

import com.company.platform.cache.application.resolver.CacheDefinitionRegistry;
import com.company.platform.cache.application.resolver.NamedCacheDefinition;
import com.company.platform.cache.application.resolver.CacheStoreDefinition;
import com.company.platform.cache.autoconfigure.properties.CacheStoreProperties;
import com.company.platform.cache.autoconfigure.properties.NamedCacheProperties;
import com.company.platform.cache.domain.model.CacheProviderType;
import com.company.platform.core.json.JsonMapperHelper;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class DefaultCacheKeyEncoderTest {
    private static final String TOKEN = "abcdefghijklmnop";

    @Test
    void encodesScalarAndStructuredKeysDeterministically() {
        DefaultCacheKeyEncoder encoder = encoder();
        NamedCacheDefinition cache = definition();

        assertThat(encoder.encode(cache, "alpha", TOKEN))
            .startsWith("application:cache:{users}:users:v1:" + TOKEN + ":")
            .doesNotContain("alpha");
        assertThat(encoder.encode(cache, 12, TOKEN))
            .isEqualTo(encoder.encode(cache, 12, TOKEN));
        assertThat(encoder.encode(cache, true, TOKEN))
            .isNotEqualTo(encoder.encode(cache, "true", TOKEN));
        assertThat(encoder.encode(cache, Map.of("id", 7), TOKEN))
            .contains(":{users}:users:v1:");
    }

    @Test
    void hashesSensitiveAndOversizedKeysWithoutHashCode() {
        var properties = CacheTestFixtures.validProperties();
        properties.getCaches().get("users").getKey().setSensitive(true);
        NamedCacheDefinition sensitive = registry(properties).requireCache("users");
        String encoded = encoder().encode(sensitive, "customer@example.test", TOKEN);
        assertThat(encoded).doesNotContain("customer").doesNotContain("example");

        String huge = "x".repeat(2_000);
        String compact = encoder().encode(definition(), huge, TOKEN);
        assertThat(compact).contains(":sha256:");
        assertThat(compact.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
            .isLessThanOrEqualTo(512);

        var routed = CacheTestFixtures.validProperties();
        routed.getCaches().get("users").getKey().setHashTag("customer-group");
        assertThat(encoder().encode(
            registry(routed).requireCache("users"), "id", TOKEN))
            .contains(":{customer-group}:");
    }

    @Test
    void validatesAllKeySegments() {
        DefaultCacheKeyEncoder encoder = encoder();
        NamedCacheDefinition cache = definition();
        assertThatNullPointerException().isThrownBy(() -> new DefaultCacheKeyEncoder(null));
        assertThatNullPointerException().isThrownBy(() -> encoder.encode(null, "x", TOKEN));
        assertThatNullPointerException().isThrownBy(() -> encoder.encode(cache, null, TOKEN));
        assertThatIllegalArgumentException().isThrownBy(() ->
            encoder.encode(cache, "x", "short")).withMessageContaining("token");
        assertThatIllegalArgumentException().isThrownBy(() ->
            encoder.encode(cache, "x", null)).withMessageContaining("token");

        var unsafeTag = CacheTestFixtures.validProperties();
        unsafeTag.getCaches().get("users").getKey().setHashTag("{secret}");
        assertThatIllegalArgumentException().isThrownBy(() ->
            encoder.encode(registry(unsafeTag).requireCache("users"), "x", TOKEN))
            .withMessageContaining("hash tag");

        NamedCacheProperties invalidVersion = new NamedCacheProperties();
        invalidVersion.getKey().setVersion(null);
        assertThatIllegalArgumentException().isThrownBy(() ->
            encoder.encode(manual(invalidVersion, "prefix"), "x", TOKEN))
            .withMessageContaining("key version");

        NamedCacheProperties normal = new NamedCacheProperties();
        assertThatIllegalArgumentException().isThrownBy(() ->
            encoder.encode(manual(normal, "p".repeat(500)), "x".repeat(2_000), TOKEN))
            .withMessageContaining("exceeds 512");
    }

    private DefaultCacheKeyEncoder encoder() {
        return new DefaultCacheKeyEncoder(new JsonMapperHelper(JsonMapper.builder().build()));
    }

    private NamedCacheDefinition definition() {
        return registry(CacheTestFixtures.validProperties()).requireCache("users");
    }

    private CacheDefinitionRegistry registry(
        com.company.platform.cache.autoconfigure.properties.PlatformCacheProperties properties
    ) {
        return new CacheDefinitionRegistry(
            properties, new PlatformCachePropertiesValidator(properties));
    }

    private NamedCacheDefinition manual(
        NamedCacheProperties properties, String prefix
    ) {
        CacheStoreProperties storeProperties = new CacheStoreProperties();
        CacheStoreDefinition store = new CacheStoreDefinition(
            "local", CacheProviderType.CAFFEINE, storeProperties);
        return new NamedCacheDefinition(
            "users", CacheProviderType.CAFFEINE, store, null, null,
            java.time.Duration.ofMinutes(1), false, 1_000_000,
            prefix, properties);
    }
}
