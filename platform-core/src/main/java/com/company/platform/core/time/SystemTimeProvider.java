package com.company.platform.core.time;

import lombok.Getter;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Objects;

@Getter
public final class SystemTimeProvider implements TimeProvider {

    public static final ZoneId DEFAULT_ZONE =
        ZoneId.of("Asia/Ho_Chi_Minh");

    private final Clock clock;
    private final ZoneId defaultZone;

    public SystemTimeProvider() {
        this(
            Clock.systemUTC(),
            DEFAULT_ZONE
        );
    }

    public SystemTimeProvider(
        Clock clock,
        ZoneId defaultZone
    ) {
        this.clock = Objects.requireNonNull(
            clock,
            "clock must not be null"
        );
        this.defaultZone = Objects.requireNonNull(
            defaultZone,
            "defaultZone must not be null"
        );
    }

    @Override
    public Instant nowInstant() {
        return clock.instant();
    }

    @Override
    public OffsetDateTime now() {
        return now(defaultZone);
    }

    @Override
    public OffsetDateTime now(ZoneId zoneId) {
        Objects.requireNonNull(
            zoneId,
            "zoneId must not be null"
        );

        return OffsetDateTime.ofInstant(
            clock.instant(),
            zoneId
        );
    }
}
