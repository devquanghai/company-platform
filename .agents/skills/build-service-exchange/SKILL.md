---
name: build-service-exchange
description: Build or extend the platform-service-exchange module for named REST HTTP/HTTPS and gRPC clients, auto-configuration, resilience, fallback, logging and audit events.
---

1. Read root `AGENTS.md` and parent POMs.
2. Use `exchange_explorer` before proposing changes.
3. Reuse existing `platform-core` abstractions for JSON, tracing, request context and time.
4. Keep HTTP and gRPC adapters separate.
5. Keep resilience orchestration transport-neutral.
6. Use injectable interfaces, not static utility classes.
7. Apply named-client configuration and lifecycle ownership rules.
8. Add or update behavioral tests with every change.
9. Run the module quality gate.
10. Ask `exchange_architect` and `exchange_security_reviewer` to review the final design.
