# Platform Queue Design

## Scope and guarantees

`platform-queue` is one Spring Boot library artifact with optional Kafka and
RabbitMQ adapters. It offers named logical destinations, immutable envelopes,
confirmation-aware publishing, listener registration, reliability SPIs and
observability without hiding provider-specific semantics.

Guarantees are path-specific:

- Direct publish returns confirmed, failed, or unknown. It is not a durable
  handoff; a caller retry must reuse the same `messageId`.
- An outbox record committed in the same local database transaction as business
  state is eventually published at least once while the poller/store/broker
  recover and continue operating.
- Consumers rely on broker redelivery (at-least-once). Business side effects
  are deduplicated only when the handler uses inbox/application idempotency at
  the correct transaction boundary.

Kafka producer transactions cover only operations explicitly executed through
the producer transaction API. Consumer `read-committed` configures isolation
only; this library does not currently wire a transactional listener container
or a transactional DLT recoverer. There is no exactly-once guarantee for
business side effects, automatic Kafka/Rabbit failover, or in-memory production
durability.

## Hexagonal boundaries

```text
api + domain <- application ports/services <- provider adapters
                                               |
                              +----------------+----------------+
                              |                                 |
                       adapter.kafka                     adapter.rabbit
```

`api` and `domain` have no Spring/application/adapter/provider dependency.
`application` depends only on them and owns outbound ports. Adapters implement
those ports. `autoconfigure` is the sole composition root. ArchUnit/package
tests enforce this direction.

- `api`: stable publish, consume, annotation, model and provider extension
  contracts.
- `domain`: provider-neutral policies, results, failures and lifecycle states.
- `application`: orchestration, registries, resolution and listener metadata.
- `adapter.kafka`: named producer/admin/container resources, retry/DLT and
  Kafka transaction operations.
- `adapter.rabbit`: named connection/template/container resources, topology,
  confirmations/returns and retry/DLX.
- `envelope` and `serialization`: immutable metadata, safe headers, codecs,
  trusted event types, schemas and upcasters.
- `reliability`: retry classification, dead-letter replay, idempotency, inbox,
  outbox and recovery.
- `resilience`: bounded outbound protection; never wraps consumer poll loops.
- `observability`: low-cardinality metrics, tracing bridge, safe health and
  Spring application audit events.
- `autoconfigure`: bean wiring only, with explicit imports and back-off.

## Broker, destination and subscription model

`platform.queue.brokers.<name>` describes a named connection and exactly one
provider. `platform.queue.destinations.<name>` describes an outbound logical
channel and must reference one enabled broker.
`platform.queue.subscriptions.<name>` describes an inbound binding and refers
to a destination. This supports multiple Kafka consumer groups or Rabbit queues
for one event. Listener annotations refer to a subscription; a compatibility
`destination` attribute is accepted only when exactly one enabled subscription
exists. Provider-specific properties remain separate:

- Kafka destination: topic and producer key/partition policy. Kafka
  subscription: group, offset isolation, retry topics and DLT.
- Rabbit destination: exchange/routing key/persistence. Rabbit subscription:
  queue, binding, acknowledgement, retry queues and DLX/DLQ.

Registries are immutable snapshots constructed after fail-fast validation.
They never choose an implicit default broker and never resolve unregistered
runtime input.

### Lean configuration

The top-level configuration groups are `message`, `topology`, `delivery`,
`brokers`, `destinations`, `subscriptions` and `observability`. Safety
invariants are not runtime configuration: Kafka always uses `acks=all`,
idempotence, bounded retries and at most five in-flight requests; Rabbit
publishers always use correlated confirms, returns and mandatory publishing;
TLS always verifies hostnames. JSON is the default payload format, message IDs
are always generated when missing, and correlation IDs fall back to request
context and then message ID.

Legacy keys must migrate as follows: `source-application` to
`application-name`, `defaults.*` to `message.*` or `topology.mode`,
`reliability.outbox-enabled` to `delivery.outbox-enabled`,
`reliability.inbox-enabled` to `delivery.inbox-enabled`,
`reliability.lock-timeout` to `delivery.processing-lock-timeout`, and
`reliability.max-attempts` to `delivery.outbox-max-attempts`. Kafka producer
`transaction-enabled` becomes `transactions-enabled`, consumer
`transaction-enabled` becomes `read-committed`, Rabbit `ssl.enabled` becomes
`tls-enabled`, `require-key` becomes `key-required`, and
`allow-partition-override` to `partition-override-allowed`. The `acks`,
`enable-idempotence`, `correlated-confirms`, `returns-enabled`, `mandatory`,
`verify-hostname` and `annotations-enabled` keys were removed because they are
either invariants or had no effect.

Deprecated binding aliases remain for one migration cycle. Applications must
not supply old and new forms of the same setting together.

## Publish flow

```text
PublishRequest
 -> destination/broker validation
 -> envelope + safe headers + schema validation
 -> optional outbound resilience
 -> provider adapter
 -> required confirmation
 -> PublishResult + metrics/audit
```

The request/destination selects the mode before execution:

- `DIRECT`: publish to the broker. A timeout or lost confirmation returns
  `UNKNOWN`; it never writes an outbox record after attempting the broker send.
- `OUTBOX`: write the serialized envelope only, in the caller's local database
  transaction. The poller publishes after commit.

`BaseKafkaProducer<T>` uses `KafkaDestinationResolver` to validate and fix a
Kafka-backed logical destination and payload type while
allowing each send to supply a `KafkaPublishMessage<T>` with key,
message/event ID, correlation/causation ID, event type, schema version, custom
headers, partition and timeout. `KafkaPublishFailureHandler` is a fail-open
publish-failure extension point. Its context contains no payload, credential or
native `ProducerRecord`, and handlers do not retry automatically because a
timeout can have an `UNKNOWN_OUTCOME`.

Kafka returns topic/partition/offset after the send future completes with an
acceptable ACK policy; `acks=0` is rejected for confirmation-aware and outbox
paths. Rabbit resolves both correlated confirm and mandatory-return outcomes
before completion and treats an unroutable message as failure even when the
broker confirm is ACK.

Platform-owned Kafka producers enforce `acks=all`, explicit idempotence,
positive retries, at most five in-flight requests, bounded request/block/delivery
timeouts and stable keys where ordering is required. Ambiguous timeouts map to
`UNKNOWN_OUTCOME`. Consumers enforce auto-commit off; record handlers default
to `AckMode.RECORD`. Setting `read-committed=true` only hides uncommitted Kafka
records; it does not make handler side effects or offset commits transactional.

Rabbit messages are persistent for durable destinations. Critical paths require
durable topology, mandatory publishing, correlated confirms and returns. The
result state machine distinguishes ACK, NACK, RETURN, TIMEOUT and
CHANNEL_CLOSED. Broker DLX is documented as at-most-once unless a quorum queue
is explicitly validated with `dead-letter-strategy=at-least-once` and
`overflow=reject-publish`; otherwise application republish-confirm is used.

## Consume and retry flow

```text
broker container
 -> decode envelope
 -> metadata/schema/security validation
 -> optional fenced inbox acquire
 -> business mutation + inbox COMPLETED in one DB transaction where supported
 -> commit transaction
 -> map result/exception to retry decision
 -> confirm retry/DLT publication when required
 -> ACK/commit original only after the preceding boundary succeeds
 -> metrics/audit
```

Fatal malformed, unsupported-schema, oversized and security-invalid messages do
not retry. `maxAttempts` includes the original delivery and uses a bounded
exponential backoff. Kafka currently accepts `NONE` or `BLOCKING`; startup rejects
`NON_BLOCKING`/`COMBINED` until retry-topic recovery is implemented. Rabbit delayed retry uses TTL/DLX unless
an explicitly enabled plugin is validated. Attempt headers are system-owned,
bounded and never trusted from caller input. If retry/DLT publication fails or
has an unknown outcome, the original is not acknowledged. A DLT/DLQ consumer
does not enter the same retry chain automatically.

## Listener registration

The module ships no custom listener annotation. Applications using native
Spring factories use `@KafkaListener` or `@RabbitListener` directly. Applications
using platform named brokers extend `BaseKafkaConsumer<T>` or
`BaseRabbitConsumer<T>`; the internal registrar resolves the logical destination
and delegates to the matching broker adapter.

Applications can extend `BaseKafkaConsumer<T>`, declare the subscription and
`Class<T>` in the constructor, and implement `receive`. Auto-configuration
registers this consumer directly. The explicit `Class<T>` avoids generic type
erasure and removes the need for an annotated method that only forwards a call.

Kafka consumers can request `read_committed` isolation but do not gain a
transactional processing boundary from that setting. Retry-topic subscriptions
require idempotency. Kafka ordering is per partition only; strict ordering
requires a stable non-null key, forbids non-blocking retry and uses partition
pause/blocking retry. Concurrency may scale across partitions; records sharing
the same key remain on one partition and are processed serially. Retry/DLT topics preserve the key and have partition
parity. Increasing partitions is treated as an ordering-affecting governance
change.

Kafka consumer modes:

- `REALTIME`: process one record and commit only after ACK/inbox completion.
- `BATCH`: durably stage, then claim one ordered partition batch when 500 records
  or the oldest record reaches 30 minutes.
- `BULK`: same flow with 100000 records or one day.

`BATCH` and `BULK` require both `InboxStore` and
`DeferredKafkaMessageStore`. Kafka offsets are committed only after staging.
The durable store deduplicates subscription/topic/partition/offset, permits one
fenced claim per partition, and returns contiguous offsets in ascending order.
Partial retry is safe because already processed message IDs are rejected by the
inbox. Inbox retention must exceed the maximum deferred retry/replay window.
`markDeadLetter` atomically completes the successful prefix, marks only the
failed head terminal, preserves the untouched suffix, and keeps newer offsets
blocked until that transition commits. This is at-least-once processing, not
exactly-once; irreversible business mutations should use the same database
transaction as a transactional inbox implementation or carry a fencing token.

```yaml
platform:
  queue:
    subscriptions:
      invoice-batch:
        destination: invoice-events
        idempotency-enabled: true
        kafka:
          group-id: invoice-batch-v1
          mode: BATCH
          max-messages: 500
          max-wait: 30m
          strict-ordering: true
      invoice-bulk:
        destination: invoice-events
        idempotency-enabled: true
        kafka:
          group-id: invoice-bulk-v1
          mode: BULK
          max-messages: 100000
          max-wait: 1d
          strict-ordering: true
```

Both modes require application-provided durable `InboxStore` and
`DeferredKafkaMessageStore` implementations. No in-memory production fallback
is installed.

Rabbit listeners use manual acknowledgement, `defaultRequeueRejected=false`,
bounded prefetch/concurrency and acknowledge only on the consumer channel.
Strict ordering requires prefetch/concurrency of one. Delayed retry uses one
queue per delay tier with queue TTL and DLX back to the configured route; it
does not mix per-message TTLs that cause head-of-line expiry. DLQ and
parking-lot queues are terminal and never auto-DLX/requeue.

## Inbox and outbox

`messageId` is producer-generated, immutable and preserved through outbox,
retry and dead-letter flows. Inbox uniqueness is at least
`(handlerId, messageId)`; tenant/destination scoping must be an explicit store
policy. `InboxStore.acquire` atomically inserts or CAS-updates
`PROCESSING(ownerId, token, lockedUntil)`. Complete/fail/renew operations require
the current fencing token so a stale worker cannot overwrite a reclaimed
record. `COMPLETED` duplicates ACK and skip. Retention defines a documented
duplicate window. A Redis inbox is not atomic with a relational business
transaction and therefore offers a weaker boundary than a database inbox in
the same datasource. No in-memory store is auto-configured in main runtime;
enabling inbox without a durable/custom store fails startup.

`OutboxMessageStore.claimBatch` atomically claims with CAS/`SKIP LOCKED` and
returns `ownerId`, fencing token/version and `lockedUntil`. Mark/renew/fail
operations require that token. The poller never holds row locks/database
transactions across a broker network wait; leases exceed the send timeout or
are renewed. It calls `markPublished` only after required confirmation. A crash
between broker confirmation and mark creates a documented duplicate window.
Exhausted records enter a dead state only after bounded attempts and require
explicit recovery/replay. Enabling outbox without a durable/custom store fails
startup. Neither pattern claims exactly-once delivery.

Known duplicate windows include lost producer confirmation followed by a
same-ID retry; commit followed by process death before ACK; broker confirmation
followed by outbox-worker death before mark; lease expiry during send; confirmed
retry/DLT publish followed by lost original ACK; and explicit replay.

## Schema and headers

JSON is the default through platform-core `JsonMapperHelper`. Kafka consumes
with `ByteArrayDeserializer`; Rabbit consumes the raw body. All Spring type
headers (`__TypeId__`, `__ContentTypeId__`, `__KeyTypeId__` and variants) are
stripped/rejected, and wildcard trusted packages are forbidden. The fixed
envelope is decoded first. An immutable registry maps exact
`event-type + schema-version` pairs to fixed Java types; unknown entries fail
before upcast/bind. Upcasters advance one registered version after raw
size/envelope/type validation. External registries use configured endpoints and
allowlisted schema IDs only, never message-supplied URLs.

Serializer, schema registry, upcaster and external registry clients are SPIs.
No Java native serialization, default typing, arbitrary class loading, or Java
class name is used as an inter-service contract.

Mandatory headers use lowercase kebab-case and cannot be overridden by callers.
Custom propagation uses an ASCII-name allowlist; reserved/internal/provider type
headers, control characters, Unicode confusables and duplicate canonical names
are rejected. Custom values are opaque and never logged/audited. Defaults are
64 headers, 8 KiB per header, 32 KiB total headers, 1 MiB payload and 2 MiB
record/envelope, with hard ceilings of 128/16 KiB/64 KiB/8 MiB/10 MiB.
Limits apply before parse/decompression and again after decoding across direct,
outbox, consume, retry, replay and dead-letter paths. Trace context is separately
bounded and arbitrary baggage is not propagated.

Retry/dead-letter exception metadata passes a built-in fail-safe sanitizer,
removes control characters and is truncated. `platform-logging` can strengthen
sanitization but is not required for the secure baseline.

## Security and resource ownership

- TLS hostname verification is mandatory and trust-all is unsupported.
- Production rejects Kafka `PLAINTEXT`/`SASL_PLAINTEXT`, Rabbit `amqp://`,
  inline JAAS, credentials in broker URIs, and non-allowlisted
  SASL/TLS mechanisms. Insecure transport requires an explicit local/test-only
  mode. PLAIN/SCRAM/OAUTH credentials require TLS.
- Kafka SASL and Rabbit credentials remain in provider configuration and are
  never copied into envelope, logs, health, audit events, or errors.
- Topology names come only from validated configuration, never untrusted
  message input.
- Publish callers select only an exact, case-sensitive logical destination.
  They cannot override topic, exchange, queue, consumer group, retry/DLT names
  or topology arguments. Routing/partition overrides require a per-destination
  allow policy. Replay/reroute is a separate privileged API.
- Application-provided factories/templates/containers are not closed by the
  module. Module-created named resources are closed on context shutdown.
- Topology governance validates or declares only non-destructively; mismatches
  fail startup.

Each resource registry entry stores explicit `MODULE` or `APPLICATION`
ownership and an idempotent closer. Startup rollback closes only partially
created module resources. Shutdown stops new publish/registration and outbox
pollers, stops main/retry/DLT containers with bounded in-flight drain, then
closes module producer factories/admin/connections/executors. Fencing is a
fatal lifecycle event.

Topology modes are `DISABLED`, `VALIDATE_ONLY` (default),
`DECLARE_IF_MISSING`, and `DECLARE_AND_VALIDATE`; declaration never
alters/deletes topics or increases partitions.
Rabbit uses one connection/admin per broker with
`explicitDeclarationsOnly=true`; declarables identify exactly that admin and
containers receive it explicitly. Rabbit mismatches are fatal and are never
deleted/recreated. Runtime and topology-admin credentials may be separated and
least-privilege ACLs are documented.

## Auto-configuration

`PlatformQueueAutoConfiguration` enables properties and common registries.
Focused auto-configurations wire envelope, serialization, publisher, listener,
Kafka, Rabbit, reliability, inbox/outbox and observability. Each is conditional
on the global flag, relevant classpath and feature flag. All replaceable beans
use `@ConditionalOnMissingBean`; no component scan or ambiguous primary bean is
used.

Type-level back-off applies only to singleton SPIs/facades. Named broker,
destination and subscription resources merge/override per exact name through
registries/factories; one user bean cannot accidentally suppress unrelated
names. All maps validate before any connection is created. Optional provider
classes are isolated behind class conditions so loading the module without a
broker dependency cannot resolve provider types.

## Verification strategy

- Package with JDK 25 and Java 21 release target.
- Validate auto-configuration metadata and optional broker classpaths.
- Exercise broker behavior only through reactor integration scenarios.
- Dependency convergence asserts one effective `tools.jackson` version before
  release; the discovered 3.2.1-declared/3.1.4-effective drift is a release
  blocker if unresolved by the parent.

## Dead-letter replay

Replay is explicit, admin-authorized by the consuming application, target
allowlisted, dry-run capable, schema-validated/upcast, and bounded by message
count, duration and rate. It emits an audit event and never exposes a default
HTTP endpoint. A replay uses a new `messageId` plus immutable
`original-message-id` and `replay-id` headers by default; preserving the old ID
requires an explicit inbox-reset policy and audit because an existing
`COMPLETED` inbox record would otherwise skip it.
