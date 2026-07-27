# Custom masking strategy

Create a `MaskingStrategy` bean and reference its bean name from a configured
rule or `@Sensitive(strategyBean = "customerCodeMasking")`. Return a typed
`MaskingResult`; never mutate the source or weaken mandatory credential rules.

