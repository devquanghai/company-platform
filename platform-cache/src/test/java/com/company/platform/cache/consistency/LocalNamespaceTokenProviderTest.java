package com.company.platform.cache.consistency;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class LocalNamespaceTokenProviderTest {

    @Test
    void returnsStableOpaqueTokenAndRotatesIt() {
        LocalNamespaceTokenProvider provider = new LocalNamespaceTokenProvider();
        String first = provider.current("users");
        assertThat(first).hasSize(22).matches("[A-Za-z0-9_-]+");
        assertThat(provider.current("users")).isEqualTo(first);
        assertThat(provider.rotate("users")).isNotEqualTo(first);
        assertThat(provider.current("users")).isNotEqualTo(first);
    }

    @Test
    void rejectsBlankCacheName() {
        LocalNamespaceTokenProvider provider = new LocalNamespaceTokenProvider();
        assertThatIllegalArgumentException().isThrownBy(() -> provider.current(" "));
        assertThatIllegalArgumentException().isThrownBy(() -> provider.rotate(null));
    }
}
