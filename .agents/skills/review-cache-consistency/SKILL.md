---
name: review-cache-consistency
description: Review cache atomicity, CAS, optimistic updates, stampede protection, cluster slots and distributed locks. Use for consistency-sensitive platform-cache changes.
---

1. Identify whether consistency is JVM-local or Redis-distributed.
2. Verify Redis mutations use atomic commands, Lua or bounded optimistic transactions.
3. Verify Lua keys are passed through `KEYS` and cluster hash slots are compatible.
4. Verify optimistic conflicts are results, not infrastructure failures.
5. Verify single-flight cleanup, bounded wait and loader failure propagation.
6. Verify distributed lock ownership, lease handling and fail-closed behavior.
7. Require fencing when a stale lock holder can mutate an external resource.
8. Confirm database transactions and database optimistic locking are not replaced.
9. Require deterministic evidence for concurrency guarantees; reject timing-only reasoning.
