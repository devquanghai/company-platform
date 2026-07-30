---
name: review-message-delivery
description: Review queue delivery guarantees, retries, acknowledgements, dead letters, idempotency, inbox, outbox, replay, and transaction boundaries. Use for consistency-sensitive platform-queue changes.
---

# Review Message Delivery

1. State the actual delivery guarantee and all duplicate windows.
2. Verify message ID requirements and acknowledgement timing.
3. Verify retry layers and exception classification are bounded.
4. Verify poison messages reach DLT/DLQ without infinite loops.
5. Verify outbox records are marked published only after required confirmation.
6. Verify inbox uniqueness, concurrent acquisition, stale lock recovery, and retention.
7. Verify replay is explicit, bounded, rate-limited, audited, and schema-validated.
8. Verify database and broker transaction boundaries are described accurately.
9. Verify there is no automatic Kafka/Rabbit fallback or in-memory production store.
10. Reject false exactly-once claims and silent message loss.
