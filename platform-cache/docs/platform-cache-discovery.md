# Platform Cache Discovery

## Repository baseline

| Concern | Repository value |
|---|---|
| Group | `com.company.platform` |
| Runtime JDK used by the build | JDK 25 |
| Maven compiler release | Java 21 |
| Spring Boot | 4.0.7 |
| Spring Framework | 7.0.8 |
| Spring Data Redis | 4.0.6 |
| Lettuce | 6.8.2.RELEASE |
| Caffeine | 3.2.4 |
| Resilience4j | 2.4.0 |
| Jackson | 3.1.4 effective Boot classpath; root property currently says 3.2.1 |
| Micrometer | 1.16.6 |
| JaCoCo | 0.8.14 |

Versions come from the parent, Spring Boot BOM and Resilience4j BOM. The child
module does not declare dependency versions.

The Jackson property and effective dependency graph currently disagree, and
Jackson 2 is also present through springdoc. Cache code therefore compiles
against the shared `JsonMapperHelper` contract and never assumes a patch
version or creates another mapper.

## Reusable platform contracts

- `JsonMapperHelper`: application-configured Jackson 3 serialization without a
  private mapper.
- `TimeProvider`: deterministic time and timezone abstraction.
- `RequestContextProvider`: request and correlation identifiers.
- `TraceContextProvider`: current Micrometer trace and span identifiers.
- `PlatformInfrastructureException`: stable infrastructure failure base.
- Spring `ApplicationEventPublisher`: the repository event publication pattern.
- Micrometer `MeterRegistry`: optional low-cardinality metrics integration.

No existing cache, Redis connection, key-generation or cache-consistency
abstraction exists.

## Repository conventions

- Boot integrations use `@AutoConfiguration`, conditions, bean back-off and
  `AutoConfiguration.imports`; component scanning is not used.
- Properties use Lombok getters/setters/field defaults and generated metadata,
  supplemented by Vietnamese additional metadata.
- Auto-configuration tests use `ApplicationContextRunner`.
- Shared modules contain no executable application or runtime credentials.
- The cache module raises its JaCoCo gate to 85% line and 80% branch.

## Conflicts and migration impact

- The build runs on JDK 25 but intentionally emits Java 21 bytecode; this module
  follows that repository contract.
- Boot's native cache auto-configuration must back off to the platform manager;
  the platform bridge must be ordered before Boot's `CacheAutoConfiguration`.
- Multiple Redis stores require platform-owned connection factories with an
  explicit close lifecycle. Disabled stores must never initialize a connection.
- Spring Data Redis defaults to Java serialization and may clear with `KEYS`;
  neither default is allowed by this module.
- Cache is never the source of truth. Local fallback is forbidden for
  distributed coordination, locks, security state, quotas and exact counters.
- Redis clearing must use logical namespace versioning, never `KEYS`.
- Public APIs remain provider-neutral and never expose Lettuce, Caffeine or
  Redisson types.
- Existing uncommitted core/logging/integration changes are outside this module
  and must be preserved.
