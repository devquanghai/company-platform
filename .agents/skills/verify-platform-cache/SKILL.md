---
name: verify-platform-cache
description: Run the complete platform-cache compile, test, metadata, security, dependency and JaCoCo quality gate. Use before delivering platform-cache changes.
---

1. Compile platform-cache and reactor dependencies with JDK 25.
2. Run unit, auto-configuration, concurrency and local-provider tests.
3. Run Redis standalone integration tests when Docker is available.
4. Run Sentinel and Cluster profiles when their environments are available.
5. Verify Lua resources, atomicity, namespace clearing and cluster-slot rejection.
6. Run JaCoCo and require at least 85% line and 80% branch coverage.
7. Verify dependency convergence, metadata and `AutoConfiguration.imports`.
8. Search test output for raw passwords, tokens, keys and cache values.
9. Run `cache_test_reviewer` and fix blocker/high findings.
10. Report exact commands, counts, coverage and skipped external topology tests.
