---
name: review-rabbit-queue
description: Review platform-queue RabbitMQ topology, publisher confirms and returns, acknowledgements, retries, DLX, DLQ, parking-lot, and recovery. Use for Rabbit adapter and configuration changes.
---

# Review Rabbit Queue

1. Verify exchange, queue, binding, queue type, durability, and arguments.
2. Verify correlated publisher confirms, mandatory publishing, returns, and timeouts.
3. Distinguish broker ACK, broker NACK, unroutable return, and consumer completion.
4. Verify manual acknowledgement occurs after successful processing boundaries.
5. Verify prefetch, concurrency, recovery, and graceful shutdown.
6. Verify retries preserve original routing metadata and have a bounded attempt count.
7. Verify DLX, DLQ, and parking-lot flows cannot requeue infinitely.
8. Verify optional delayed-message plugin capability fails clearly when unavailable.
9. Verify topology mismatch fails without destructive recreation.
10. Require executable broker evidence for changed delivery behavior when an environment is available.
