package com.company.platform.core.time;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public interface TimeProvider {

    Instant nowInstant();

    OffsetDateTime now();

    OffsetDateTime now(ZoneId zoneId);

    ZoneId getDefaultZone();
}
