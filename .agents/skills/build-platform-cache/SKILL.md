---
name: build-platform-cache
description: Build or extend platform-cache with named Caffeine, Redis, multi-level and NOOP stores, resilience, consistency, observability, Spring Cache integration and Boot auto-configuration. Use for implementation or refactoring under platform-cache.
---

1. Read `AGENTS.md`, parent POM and `docs/platform-cache-discovery.md`; run `cache_explorer` before architecture changes.
2. Build vertical cache slices under feature-owned `internal` ports/adapters; preserve supported API FQCN.
3. Reuse platform-core JSON, time, trace, request and exception contracts.
4. Treat cache as disposable acceleration; reject unbounded local caches, Java serialization, `KEYS`, and sensitive raw keys/values.
5. Keep distributed coordination fail-closed; never substitute JVM coordination.
6. Run `cache_architect`, `cache_consistency_reviewer`, `cache_resilience_reviewer`, then `$verify-platform-cache`.
