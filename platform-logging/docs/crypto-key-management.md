# Crypto key management

Provide `KeyProvider` from Vault, KMS, Key Vault, HSM or an application-specific
provider. Encryption resolution returns the active version; decryption resolves
the exact envelope version.

AES keys must be 256-bit. RSA keys must be at least 2048-bit and have the
correct public/private purpose. Do not put key bytes or passwords in YAML.

`CachingKeyProvider` has bounded size, TTL, explicit `clear()` and destroys
evicted material on a best-effort basis. Never cache plaintext or decrypted
results. Rotation reads the old version and encrypts with the current active key.

