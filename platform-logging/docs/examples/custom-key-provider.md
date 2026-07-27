# Custom key provider

Implement `KeyProvider` with a Vault/KMS/HSM client. Return `KeyMaterial` whose
alias, version, purpose and algorithm match the request. Encryption returns the
active version; decryption resolves the requested historical version. Do not log
provider exceptions, key aliases with high cardinality or raw key material.

