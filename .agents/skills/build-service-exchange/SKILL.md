---
name: build-service-exchange
description: Build or extend the platform-service-exchange module for named REST HTTP/HTTPS and gRPC clients, auto-configuration, resilience, fallback, logging and audit events.
---

1. Read `AGENTS.md` and parent POMs; run `exchange_explorer` before design changes.
2. Build HTTP, gRPC, resilience, audit and observability as feature slices with internal ports/adapters; preserve supported API FQCN.
3. Reuse platform-core JSON, tracing, request context and time contracts.
4. Keep HTTP/gRPC adapters separate and resilience transport-neutral. Use injectable boundaries, not static utilities.
5. Preserve named-client lifecycle ownership.
6. Run `exchange_architect`, `exchange_security_reviewer`, then `$verify-service-exchange`.
