# Masking rules

Precedence:

1. Mandatory security names.
2. `@Sensitive` and composed annotations.
3. Exact JSON path.
4. Exact field/header/query/MDC name.
5. Startup-compiled regex.
6. Default policy.

JSON path supports `$`, `.property`, `[index]` and `[*]`. Other syntax fails
startup. Regex input is bounded and dangerous nested quantifier patterns are
rejected. `REMOVE` omits a member rather than replacing it with null.

