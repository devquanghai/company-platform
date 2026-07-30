---
name: build-platform-cache
description: Build or extend platform-cache with named Caffeine, Redis, multi-level and NOOP stores, resilience, consistency, observability, Spring Cache integration and Boot auto-configuration. Use for implementation or refactoring under platform-cache.
---

1. Read root `AGENTS.md`, parent POM and `docs/platform-cache-discovery.md`.
2. Run the read-only `cache_explorer` agent before architectural changes.
3. Reuse platform-core JSON, time, trace, request and exception contracts.
4. Keep public APIs provider-neutral and auto-configuration free of business logic.
5. Treat cache as disposable acceleration, never as the source of truth.
6. Reject unbounded local caches, Java serialization, `KEYS`, raw sensitive keys and values.
7. Keep distributed coordination fail-closed; never fall back to a JVM lock or local cache.
8. Implement one vertical slice with behavior tests before starting the next.
9. Run `cache_architect`, `cache_consistency_reviewer` and `cache_resilience_reviewer`.
10. Run `./mvnw -pl platform-cache -am clean verify` with JDK 25 and report limitations.
