# Platform Core architecture

Each capability owns its supported API and implementation:

```text
<capability>/api
<capability>/internal/domain
<capability>/internal/application
<capability>/internal/port/{in,out}
<capability>/internal/adapter
<capability>/internal/autoconfigure
<capability>/internal/configuration/properties
```

Use capabilities such as `i18n`, `json`, `web`, `audit`, `crypto`, `context`, `trace`, `time`, `rest`, and `mapping`. Cross-capability dependencies target `api`, never `internal`. Application/domain code does not import Spring or vendor APIs. Adapters implement ports; ports exist only at real I/O or extension boundaries.

Boot composition may use shared `internal/autoconfigure` when one configuration wires multiple capabilities. Register it through `AutoConfiguration.imports`, guard optional classpaths, and back off for consumer beans. Keep stable `platform.core.*` defaults and generated metadata.

Jackson uses strict scalar coercion and ISO-8601 input. REST failures use localized, non-sensitive envelopes. Crypto uses authenticated encryption and never logs key material. Context propagation always restores previous state.
