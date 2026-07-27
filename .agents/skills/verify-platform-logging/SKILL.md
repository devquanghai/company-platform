---
name: verify-platform-logging
description: Run the complete compile, test, security-output, metadata, dependency and JaCoCo quality gate for platform-logging.
---

1. Compile `platform-logging` and required reactor modules with JDK 25.
2. Run unit/integration tests through Maven Wrapper.
3. Run JaCoCo verify and inspect XML/HTML coverage.
4. Validate dependency convergence and optional integration boundaries.
5. Validate `AutoConfiguration.imports` and generated metadata.
6. Capture test logs and search for known sentinel secrets.
7. Verify no top-level Logback config is packaged.
8. Run test, architecture and security reviewers.
9. Fix blocker/high findings and rerun `clean verify`.
10. Report commands, counts, coverage and limitations accurately.
