# Platform Core architecture

- `auto_configuration`: Boot wiring only; proxy-less configuration, conditional beans, no application component scanning.
- `configuration.properties`: the single home for type-safe `platform.core.*` properties and IDE metadata.
- `config.jackson`, `config.web`, `config.task`: feature implementations only; never property holders.
- `i18n`: `I18nService`, typed keys, deterministic locale and fallback behavior.
- `context`, `trace`, `time`: small injectable abstractions with safe defaults.
- `rest`: HTTP response, pagination, and request metadata contracts.
- `crypto`: pure implementations, authenticated encryption, explicit exceptions, no key material in logs.
- `utils`: dependency-light helpers; do not introduce hidden global state.

Auto-configuration must back off for consumer beans and optional classpath integrations. Public configuration uses the `platform.core` prefix. Configuration defaults must be stable across environments.

Jackson uses strict scalar coercion and ISO-8601 date/time input by default. REST failures use the shared envelope and localized public messages. Mechanical property accessors use Lombok; explicit methods remain where defensive copies or invariants are required.
