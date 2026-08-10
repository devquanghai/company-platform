# Platform Cache Design

## Decision status

This document is the implementation contract. Startup is two-phase: bind,
normalize and validate the complete store/cache graph first; allocate provider
resources only after the graph is valid. There is no silent default routing.

## Architecture

```text
application
    |
    v
provider-neutral API
    |
    v
application orchestration -- policy/registry -- resilience
    |
    +--> Caffeine adapter
    +--> Redis adapter
    +--> multi-level adapter
    +--> NOOP adapter
```

`autoconfigure` only creates and connects beans. Cache behavior lives in
application services, policies and adapters.

## Package and dependency direction

```text
com.company.platform.cache
├── api
│   ├── operation
│   ├── lock
│   └── model
├── domain
│   ├── model
│   ├── policy
│   ├── result
│   └── exception
├── application
│   ├── port.in
│   ├── port.out
│   ├── resolver
│   └── service
├── adapter
│   ├── caffeine
│   ├── redis
│   ├── multilevel
│   ├── noop
│   └── springcache
├── resilience
├── consistency
├── observability
├── autoconfigure
│   └── properties
└── support
```

The common API/domain packages import no Caffeine, Lettuce, Spring Data Redis,
Redisson or Jackson type. Dependency flow is API/domain, application ports and
services, adapters, then auto-configuration wiring. Provider customizers live
under provider-specific packages. Architecture tests enforce these boundaries.

The module depends on platform-core plus Boot cache/validation. Caffeine, Redis,
resilience, Micrometer and Boot health are optional capabilities. Dependency
versions come from repository BOMs. Redisson is not currently managed, so the
first product slice exposes a fail-closed distributed-lock SPI but does not
pretend a Redisson adapter is present.

## Named stores and caches

A store owns infrastructure and provider configuration. A cache owns TTL, key,
fallback, failure and consistency policies and references a valid enabled
store. Registries are immutable after startup and fail fast for unknown names.

Canonical routing rules:

- A store provider is `CAFFEINE`, `REDIS` or `NOOP`; `MULTI_LEVEL` is rejected
  for a store because it is a cache route, not infrastructure.
- A normal cache references exactly one compatible store and resolves to that
  store provider.
- A multi-level cache has no ambiguous primary store: it explicitly references
  one enabled Caffeine `l1-store` and one enabled Redis `l2-store`.
- A NOOP cache either references a NOOP store or is disabled with explicit NOOP
  disabled-cache policy. It allocates no infrastructure.
- A fallback-local store is a separate reference and must be Caffeine.
- Names are normalized once, must be unique and are allowlisted. The entire
  reference graph is validated before any Redis connection is created.

`CacheProviderType` remains the consumer-visible resolved mode
(`CAFFEINE`, `REDIS`, `MULTI_LEVEL`, `NOOP`). Store validation restricts its
allowed subset rather than introducing a second incompatible public enum.

## Provider routing

- Caffeine stores create bounded per-cache instances.
- Redis stores reuse one connection factory and template per named store.
- Multi-level caches read L1 then L2. Mutations invalidate L1 first, mutate L2,
  publish invalidation, then optionally populate L1 with the committed value.
  A partial failure is observable and never reported as an unqualified success.
- NOOP always misses and never stores.

The registrar only registers and marks provider definitions. The Spring
container is the sole lifecycle owner of platform-created factories/templates;
the runtime registry only indexes and resolves them and never closes them.
Application-provided factories remain owned by their application/Spring
container.

## Redis resource lifecycle

`NamedRedisBeanDefinitionRegistrar` runs before Boot
`DataRedisAutoConfiguration`. It validates the complete graph, then registers
each enabled platform factory and `RedisTemplate<String, byte[]>` under stable
store-qualified bean names. Spring owns init/destroy and closes every created
resource exactly once. Disabled stores have no definition. Application
factories referenced by an explicit bean name are indexed but remain
application-owned.

The early factory definitions make Boot back off from its implicit localhost
factory. With multiple stores there is no single candidate, so Boot does not
create default templates. With exactly one Redis store, the registrar registers
a safe byte template under `redisTemplate` to prevent Boot's JDK-serialized
template; Boot may create its normal, string-safe `StringRedisTemplate`.
Platform operations never route through either compatibility bean. Names always
back off when the application already owns them. No factory is `@Primary`.
Context tests assert no extra default resource/health contributor exists and
each created factory is destroyed once.

## Consistency boundaries

- Caffeine atomicity and optimistic updates apply to one JVM only.
- Redis CAS and optimistic updates use preloaded Lua scripts.
- Single-flight is local unless a distributed extension is explicitly chosen.
- Distributed lock SPI is fail-closed and has no local fallback.
- Cache optimistic locking does not replace database transactions or JPA
  `@Version`.

Two tokens have separate roles:

- `cacheNamespaceToken` is an opaque 128-bit value embedded in physical keys
  and changes only on logical cache clear.
- `entryInvalidationEpoch(cache,key)` is a local monotonic refill guard changed
  by put/evict and never used as the cache-wide namespace.

A reader snapshots both before L2/loader work and populates L1 only when both
remain unchanged. Mutating key A therefore never makes key B miss.

If an L2 mutation fails after local invalidation, the key enters
`DIRTY_DO_NOT_POPULATE`. Same-instance reads do not read or refill the known-old
L2 value and follow source/failure policy until a verified successful L2
write/evict or newer invalidation clears the dirty state. Cross-instance outage
inconsistency remains TTL-bounded because a failed Redis mutation cannot
publish a reliable invalidation.

L1 TTL is bounded by the remaining L2 freshness, not only by configured L2 TTL.
Cross-instance invalidation remains best effort and eventual, bounded by L1 TTL.

## Security

Keys are deterministic, length-bounded and delimiter encoded. Sensitive keys
can use SHA-256; Java `hashCode()` and default object `toString()` are rejected.
Values use the shared strict Jackson mapper and a versioned envelope. Raw keys,
values, credentials and exception messages are excluded from logs and metrics.
Spring Data Redis JDK serialization is never selected.

## Redis deployment

One connection factory builds standalone, Sentinel or Cluster configuration.
Only the selected mode is read. Cluster database must be zero. Node syntax is
validated before connections are initialized.

Logical cache clear replaces a bounded namespace token. It never scans the
whole Redis database and never issues `KEYS`.

Namespace tokens are atomically replaced and non-expiring. A clear returns the
new opaque token rather than fabricating a deleted-key count. Absolute non-reuse
after Redis flush/restore would require an external durable allocator and is
not claimed; 128-bit collision probability is negligible and operations must
protect this metadata from eviction.

## Resilience and fallback

The synchronous pipeline is bulkhead, circuit breaker, retry, Redis operation.
Retries are bounded and restricted to transient connectivity failures.
Fallback runs only after primary failure and follows the named-cache policy.
Coordination caches must use fail-closed.

`STALE_IF_ERROR` stores `freshUntil` and `staleUntil`; the physical local TTL
covers the complete stale window. Only a primary infrastructure failure opens
the stale path. Stale values are exposed only through `CacheResult` with
`HIT_STALE` and `stale=true`; simple `get` never silently returns stale data.

Redis numeric increment uses a dedicated numeric representation. JSON
compare-and-set defines equality on canonical payload bytes plus explicit
schema/negative markers, never regenerated timestamp-bearing envelope bytes.
It preserves Redis TTL and coherent envelope freshness. Java `UnaryOperator`
updates use bounded
WATCH/MULTI-style optimistic retry; the updater must be side-effect-free because
it may run more than once. Version conflicts never count as infrastructure or
circuit-breaker failures.

Single-flight identity includes store, cache, namespace token, the snapped
entry invalidation epoch and encoded key. A follower at a newer epoch never
joins or receives an older loader result. Completion, failure, cancellation and
interruption remove only the same in-flight future. Loader failures are shared
with same-epoch waiters but never cached.
A follower timeout returns a distinct rejected/timeout result without removing
or cancelling the leader and without starting a duplicate loader. Interruption
restores the interrupt flag.

Cluster Lua scripts receive all keys through `KEYS` and reject cross-slot
operations. User key parts cannot inject Redis hash-tag braces.

Distributed lock acquisition, circuit-open, timeout, lease loss and owner loss
are fail-closed; the protected action never starts without ownership. A fencing
mode requires a monotonic token that the protected resource validates. The
module never retries an entire critical section and never falls back to a JVM
lock.

## Fallback state machine

```text
PRIMARY_HEALTHY
  primary hit  -> HIT
  primary miss -> loader -> primary put -> LOADED
  infra failure -> apply named failure policy

DEGRADED_READ_ONLY
  local fresh hit -> HIT_FALLBACK
  local miss -> MISS/FAIL_CLOSED according to policy

DEGRADED_READ_THROUGH
  local fresh hit -> HIT_FALLBACK
  local miss -> loader -> local put -> LOADED

DEGRADED_STALE
  local fresh hit -> HIT_FALLBACK
  local stale-within-limit hit -> HIT_STALE(stale=true)
  expired/miss -> loader only when policy explicitly permits

RECOVERED
  primary success increments outage epoch
  old-epoch fallback entries are ignored and expire naturally
```

Healthy Redis reads and successful loads may mirror safe business-data values
to the configured fallback store. Every write/delete invalidates local data
before the primary mutation. Recovery never performs an unscoped bulk clear.
Loader exceptions are application failures: they are not retried, converted to
cache misses or used to activate stale fallback.

## Multi-level sequences

Read:

```text
snapshot namespace token + entry epoch -> L1
  hit -> return
  miss -> L2
    hit -> populate L1 only if both guards unchanged, with remaining freshness
    miss -> single-flight -> recheck -> loader -> L2 put -> guarded L1 put
    failure -> named failure/fallback policy
```

Mutation:

```text
increment entry epoch -> invalidate L1 -> mutate L2
  success -> publish invalidation -> optional guarded L1 populate
  failure -> mark DIRTY_DO_NOT_POPULATE -> emit partial/degraded outcome
```

Invalidation events contain application/environment namespace, cache,
namespace token, entry epoch, key digest, event ID, source instance, timestamp and
trace ID. Handlers are idempotent. Self events may be ignored only after local
epoch increment and invalidation. Pub/Sub is best effort, never durable or
strongly consistent.

Entry epochs are local refill guards, not globally comparable counters. A
remote event increments the receiver's local epoch; an out-of-order numeric
value never clears `DIRTY_DO_NOT_POPULATE`. Dirty state clears only after a
verified successful L2 operation or verified valid read under current guards.

## Key and cluster contract

The deterministic format is application, environment, cache, opaque namespace
token, key version, optional tenant and encoded parts. Raw
passwords/tokens are rejected. Braces are escaped/rejected so untrusted input
cannot inject Redis hash tags. Sensitive key mode uses SHA-256, never Java
`hashCode()` or default object `toString()`.

Bulk operations partition keys by Redis cluster slot and are explicitly
non-atomic. Multi-key atomic operations fail fast unless all keys share the
same validated slot. Lua uses only keys provided through `KEYS`.

## Serialization envelope

Redis always stores bytes through `RedisTemplate<String, byte[]>`. Above that,
the platform serializer uses:

```json
{
  "formatVersion": 1,
  "schemaId": "configured-cache-schema",
  "schemaVersion": 1,
  "entryVersion": 1,
  "createdAt": "instant",
  "freshUntil": "instant",
  "staleUntil": "instant-or-null",
  "negative": false,
  "payload": {}
}
```

The envelope contains no Java class name. Expected type comes from the typed
API/cache registry. Parameterized values use a provider-neutral type descriptor
based on `java.lang.reflect.Type`; applications never pass `Object.class`.
Jackson default typing and Java native serialization are forbidden. JSON
migration SPI stays inside Redis serialization packages. Corrupt data is not
retried; policy may evict that single key and emits sanitized metadata only.

Public results contain a sanitized `CacheFailure` code/category/retryable flag,
not a raw `Throwable` or vendor exception.

## Atomic and optimistic design

- Caffeine uses `asMap()` atomic operations; scope is one JVM.
- Redis numeric counters use a dedicated string/integer representation and
  atomic increment commands; they are not JSON envelopes.
- Redis payload CAS/delete uses preloaded Lua with canonical payload bytes,
  schema/negative markers and explicit TTL preservation.
- Typed Java compute uses bounded optimistic transaction retry. Updaters must
  be pure because they can run more than once.
- Optimistic entries initialize `entryVersion` deterministically. Lua or an
  optimistic transaction atomically compares expected entry version, writes
  payload plus `entryVersion + 1`, and preserves coherent TTL/freshness.
- Optimistic results distinguish updated, version conflict, missing and failed.
  Conflict does not enter retry/circuit-breaker infrastructure metrics.

## Logical clear

The namespace token is Redis-backed for distributed stores, atomic,
non-expiring and scoped by application/environment/cache. Clear uses Lua to
replace it with a cryptographically random opaque 128-bit token. Caffeine uses
an equivalent local token.

`clear` returns a `CacheClearResult` containing strategy, success,
previous/current token and optional exact count. Namespace replacement has no
exact deletion count. Spring `Cache.clear()` delegates to this logical clear.
Bounded namespace-verified SCAN is an opt-in maintenance extension only.

## Spring Cache and Boot ordering

The platform annotation bridge replaces Boot's annotation `CacheManager`:

- `SpringCacheBridgeAutoConfiguration` runs before Boot
  `CacheAutoConfiguration`.
- When annotations are enabled, it contributes exactly one bean named
  `cacheManager` and enables caching; facade APIs do not depend on annotations.
- A user-provided `CacheManager` or bean named `cacheResolver` wins. The
  platform bridge backs off clearly; it never creates a second primary manager.
- Platform Redis templates/factories are resolved by named registry, not Boot's
  single-candidate template.
- Boot Redis JDK serialization is never used by platform caches.

Context tests cover annotations enabled/disabled, Boot cache present, custom
manager/resolver, provider classpath combinations and absence of ambiguous
primary beans.

## Auto-configuration graph

```text
PlatformCacheAutoConfiguration (properties, validation, immutable definitions)
  -> CaffeineCacheAutoConfiguration
  -> RedisCacheAutoConfiguration (early named bean definitions, before
     DataRedisAutoConfiguration)
  -> MultiLevelCacheAutoConfiguration
  -> CacheResilienceAutoConfiguration
  -> CacheConsistencyAutoConfiguration
  -> SpringCacheBridgeAutoConfiguration (before Boot CacheAutoConfiguration)
  -> CacheObservabilityAutoConfiguration
  -> CacheLockAutoConfiguration (SPI; provider only when explicitly available)
```

Every default bean backs off. Provider configurations use class/property
conditions. No component scan, connection for disabled stores, implicit default
store, or behavior inside auto-configuration is allowed.

## Test matrix

| Area | Required behavior |
|---|---|
| Properties/registry | full graph validation before allocation; safe errors |
| Caffeine/NOOP | TTL, bounds, bulk, atomic, clear generation, concurrency |
| Serialization | envelope, types, malformed/corrupt, no native serialization |
| Redis standalone | TTL, bulk, scripts, CAS, optimistic, namespace bump |
| Sentinel/Cluster profiles | discovery, reconnect, slots, cross-slot rejection |
| Multi-level | L1/L2 paths, generation stale-fill race, remaining TTL |
| Fallback | all modes, stale bound, outage epoch, recovery, forbidden caches |
| Resilience | transient-only retry, circuit states, bulkhead, fail policies |
| Single-flight | success/failure/timeout/interrupt cleanup and e1/e2 race with latches |
| Spring Cache | manager replacement/back-off and annotation behavior |
| Locks | fail-closed SPI, ownership and no local fallback |
| Observability | low-cardinality metrics, sanitized events and health |

Concurrency tests use barriers/latches and controlled time rather than long
sleeps. Default builds run local behavior; Docker-backed standalone, Sentinel
and Cluster suites are separate profiles and are reported when unavailable.

## Observability

Metrics use cache, store, provider, operation, outcome and tier only. Spring
events carry safe metadata plus trace/request identifiers. Health contributors
never expose credentials, nodes with credentials, raw failures, keys or values.

## Verification

Package with JDK 25, validate metadata and dependency convergence, then exercise
Redis topologies only through reactor integration scenarios when available.
