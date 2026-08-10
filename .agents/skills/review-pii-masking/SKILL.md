---
name: review-pii-masking
description: Review platform logging masking rules, annotations, structured fields, raw messages, exceptions, MDC and Logback output for sensitive-data leakage.
---

1. Identify every data entry point into logs.
2. Verify mandatory credential fields override weaker rules.
3. Verify annotation, JSON path, field/header/query/MDC and regex precedence.
4. Verify nested object, record, map, collection and JSON masking.
5. Verify exception, MDC, SLF4J key-value and final Logback masking.
6. Verify CR/LF/control sanitization and bounded output.
7. Verify cycle handling and source objects are not mutated.
8. Verify binary, stream, file and HTTP objects are never serialized.
9. Require captured-output or packaged-artifact evidence that sentinel secrets never appear.
10. Report exploitable/compliance findings before style concerns.
