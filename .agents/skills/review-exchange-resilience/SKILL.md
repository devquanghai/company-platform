---
name: review-exchange-resilience
description: Review retry, circuit breaker, rate limiter, bulkhead, timeout and fallback behavior for a named outbound REST or gRPC client.
---

1. Identify the logical outbound-call boundary.
2. Confirm the decorator order.
3. Verify which failures are recorded by the circuit breaker.
4. Verify retry decisions by HTTP method, status, exception, gRPC status and idempotency.
5. Ensure POST and PATCH are not retried by default.
6. Verify bounded `Retry-After` behavior.
7. Verify rate-limit and circuit-open errors are normalized.
8. Verify fallback executes only after the resilience pipeline has failed.
9. Verify the final audit event is published exactly once.
10. Trace every policy branch and report any branch lacking executable evidence.
