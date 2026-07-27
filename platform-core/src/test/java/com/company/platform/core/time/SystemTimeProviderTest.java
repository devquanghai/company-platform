package com.company.platform.core.time;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class SystemTimeProviderTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-07-23T01:02:03Z");

    @Test
    void usesInjectedClockAndRequestedZones() {
        Clock clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        ZoneId defaultZone = ZoneId.of("Asia/Ho_Chi_Minh");
        SystemTimeProvider provider = new SystemTimeProvider(clock, defaultZone);

        assertThat(provider.getClock()).isSameAs(clock);
        assertThat(provider.getDefaultZone()).isEqualTo(defaultZone);
        assertThat(provider.nowInstant()).isEqualTo(FIXED_INSTANT);
        assertThat(provider.now()).isEqualTo(OffsetDateTime.parse("2026-07-23T08:02:03+07:00"));
        assertThat(provider.now(ZoneOffset.UTC))
            .isEqualTo(OffsetDateTime.parse("2026-07-23T01:02:03Z"));
    }

    @Test
    void defaultConstructorUsesDocumentedDefaultZone() {
        SystemTimeProvider provider = new SystemTimeProvider();

        assertThat(provider.getDefaultZone()).isEqualTo(SystemTimeProvider.DEFAULT_ZONE);
        assertThat(provider.nowInstant()).isNotNull();
    }

    @Test
    void rejectsNullDependenciesAndZone() {
        Clock clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

        assertThatNullPointerException()
            .isThrownBy(() -> new SystemTimeProvider(null, ZoneOffset.UTC))
            .withMessage("clock must not be null");
        assertThatNullPointerException()
            .isThrownBy(() -> new SystemTimeProvider(clock, null))
            .withMessage("defaultZone must not be null");

        SystemTimeProvider provider = new SystemTimeProvider(clock, ZoneOffset.UTC);
        assertThatNullPointerException()
            .isThrownBy(() -> provider.now(null))
            .withMessage("zoneId must not be null");
    }
}
