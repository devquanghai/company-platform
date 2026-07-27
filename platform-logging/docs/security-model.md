# Security model

Mandatory credential names are canonicalized across camelCase, kebab-case,
snake_case and HTTP header casing. They are substituted before lower-priority
rules and sanitized again after customizers.

The object sanitizer denies binary, stream, file, multipart, servlet, reactive
buffer/publisher and vendor request/response graphs. It reads fields without
invoking arbitrary getters or `toString()`, detects cycles and returns detached
bounded data.

Crypto uses only AES-256-GCM, RSA-OAEP SHA-256/MGF1-SHA256, explicit strong PBE
or consumer-reviewed custom strategies. Envelope headers are canonical AAD.
Keys are external, purpose/algorithm/version checked and never logged.

Logging failures are fail-open for business operations. Compliance audit may
choose explicit FAIL_CLOSED. No raw exception is attached to backend events.

TEXT output masks through dedicated converters. JSON and audit fragments add a
last-boundary deny filter: a direct SLF4J event that still contains a mandatory
secret in its message, MDC, key-values or throwable is dropped instead of being
serialized. `PlatformLogger` remains the preferred API because it masks fields
while retaining the event.
