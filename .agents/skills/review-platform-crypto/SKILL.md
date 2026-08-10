---
name: review-platform-crypto
description: Review platform logging encryption, decryption, providers, key lifecycle, ciphertext envelopes, rotation and crypto annotations.
---

1. Confirm Jasypt is a provider, not an algorithm.
2. Confirm AES uses GCM with a fresh nonce and authenticated decryption.
3. Confirm RSA uses OAEP-SHA256 and validates key size/payload boundaries.
4. Confirm large payloads use hybrid envelope encryption.
5. Confirm keys are external, versioned, bounded-cache and clearable.
6. Confirm envelope version/provider/algorithm/key metadata validation.
7. Confirm tampering, malformed input, wrong-key and old-key behavior from executable evidence.
8. Confirm annotations delegate to `CryptoService` and fail safely.
9. Confirm no plaintext, key or full ciphertext reaches logs/exceptions/events.
10. Request a security review for every changed crypto path.
