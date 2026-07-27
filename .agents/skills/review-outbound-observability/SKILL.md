---
name: review-outbound-observability
description: Review outbound REST and gRPC logging, curl or grpcurl generation, masking, tracing, metrics and audit-event safety.
---

1. Trace one successful call and one failed call end to end.
2. Verify trace and request identifiers propagate correctly.
3. Verify metric tags are low-cardinality.
4. Verify credentials and sensitive fields are masked.
5. Verify binary, multipart and streaming payloads are not logged.
6. Verify body truncation and log failure isolation.
7. Verify curl shell escaping.
8. Verify grpcurl output is only created when valid.
9. Verify audit events contain no raw secrets or PII by default.
10. Ask `exchange_security_reviewer` to review any logging or audit change.
