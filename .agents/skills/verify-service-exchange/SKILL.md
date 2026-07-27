---
name: verify-service-exchange
description: Run the complete quality gate for platform-service-exchange after implementation or refactoring.
---

1. Compile `platform-service-exchange` and required dependencies.
2. Run unit and integration tests.
3. Run JaCoCo verification.
4. Run repository linters and static analysis configured by the parent.
5. Inspect dependency convergence.
6. Check that child dependencies do not hard-code managed versions.
7. Check generated auto-configuration and configuration metadata.
8. Check that tests close HTTP and gRPC resources.
9. Ask `exchange_test_reviewer` to inspect coverage quality.
10. Return commands, results, remaining risks and changed files.
