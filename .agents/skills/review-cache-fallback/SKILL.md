---
name: review-cache-fallback
description: Review Redis-to-Caffeine fallback, degraded mode, stale reads and recovery behavior in platform-cache. Use when fallback or multi-level behavior changes.
---

1. Identify the source of truth and configured failure policy.
2. Confirm the fallback mode and its per-instance consistency scope.
3. Verify maximum stale limits and stale result signalling.
4. Verify writes during primary failure and behavior after recovery.
5. Reject local fallback for locks, idempotency, security state, counters and quotas.
6. Verify multiple-instance divergence is documented and tested.
7. Verify low-cardinality metrics and sanitized events for activation and recovery.
8. Reject silent inconsistency or swallowed infrastructure failures.
