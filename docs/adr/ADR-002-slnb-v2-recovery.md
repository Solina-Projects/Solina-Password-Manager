# ADR-002: SLNB v2 — dual-method backup recovery

- **Status:** Accepted design; implementation not started
- **Date:** 2026-11-13
- **Scope:** M6 backup format and recovery UX
- **Supersedes:** No v1 behavior; SLNB v1 remains supported and unchanged

## Context

SLNB v1 derives one encryption key directly from an independent backup password using Argon2id, HKDF and AES-256-GCM. Its 24-word BIP-39 recovery phrase cannot open a v1 backup. The product acceptance criteria require a backup to be recoverable on a new device both with the backup password and with the 24-word recovery phrase, without an account.

The current Bip39.wordsToEntropy implementation validates a 24-word phrase and returns 32 bytes of entropy. The vault architecture already assigns the recovery phrase a distinct HKDF domain (solina/wrap/rec). The backup must use a separate domain and must not reuse the vault's wrapped Vault Key or depend on an existing device's Keystore.

## Decision

Introduce SLNB v2 as a new, explicitly versioned format. Do not reinterpret, rewrite or silently migrate SLNB v1 files.

Each v2 backup contains:
1. A fresh random 256-bit Backup Encryption Key (BEK).
2. One password-based wrapped copy of the BEK.
3. One recovery-phrase-based wrapped copy of the same BEK.
4. The payload encrypted once under the BEK.

Both unlock slots are mandatory in v2. This guarantees that every v2 backup supports both promised recovery methods. Export must request the user to re-enter the 24-word phrase because the phrase/entropy must not be persisted merely to make future exports convenient. Validate the BIP-39 word list and checksum before deriving a key.

### Key derivation

- **Password slot:** NFKC-normalize the password, derive with Argon2id using the existing bounded KdfParams validation (current maximum 64 MiB, 4 iterations, parallelism 4), then HKDF-SHA256 with the exact info label solina/backup/v2/password-wrap.
- **Recovery slot:** convert the validated 24-word phrase to its 32-byte BIP-39 entropy and derive a 32-byte key with HKDF-SHA256 using a fresh per-backup 16-byte salt and the exact info label solina/backup/v2/recovery-wrap.
- Do not use the vault recovery-wrap key, the Vault Key, a device Keystore key, or a key from another backup. Domain separation labels are protocol constants and must be tested byte-for-byte.
- Wipe entropy, normalized password bytes, Argon2id intermediate output, derived wrapping keys, BEK and plaintext buffers in finally blocks where possible. JVM String instances cannot be reliably zeroized; UI clearing is best-effort and must not be described as guaranteed memory erasure.

### Proposed binary layout

All integers are unsigned big-endian. Parser rejects unknown versions, unexpected flags, truncated data, trailing data, duplicate/invalid fields and lengths outside strict bounds.

- Magic: ASCII SLNB (4 bytes)
- Version: 2 (1 byte)
- Flags: 0x03 (1 byte; password slot and recovery slot both required)
- Argon2id memoryKiB, iterations, parallelism: three unsigned 32-bit big-endian values
- Password salt: 16 bytes
- Recovery HKDF salt: 16 bytes
- Password-wrapped BEK: exactly 60 bytes (GCM nonce 12 + ciphertext 32 + tag 16)
- Recovery-wrapped BEK: exactly 60 bytes (GCM nonce 12 + ciphertext 32 + tag 16)
- Payload: AES-GCM nonce 12 + ciphertext + tag 16

The fixed preamble is 66 bytes. Each wrapped BEK is exactly 60 bytes. The password KDF parameters are validated before Argon2id runs. The payload has an explicit maximum size before allocation/parsing; the final limit must be chosen against realistic vault sizes and tested on supported Android devices before implementation.

### Authentication and tamper binding

- Password-wrap AAD = exact 66-byte preamble concatenated with the fixed ASCII slot label solina/slnb/v2/wrap/password.
- Recovery-wrap AAD = exact preamble concatenated with solina/slnb/v2/wrap/recovery.
- Payload AAD = exact preamble concatenated with both wrapped-BEK records in their specified order.
- Distinct slot labels prevent swapping a recovery wrapper into the password slot or vice versa. Payload AAD authenticates both wrappers and prevents removing/replacing one slot without detection.
- Use the existing AES-256-GCM primitive with fresh independent random nonces. Never reuse a nonce with the same key.

## Restore behavior and compatibility

- Password restore supports SLNB v1 and v2. Version 1 continues to use its existing decoder and password derivation unchanged.
- Recovery-phrase restore is supported only for v2. If a user selects v1 and chooses phrase restore, show a clear message that v1 requires its backup password; do not silently try unrelated keys.
- Both restore paths unwrap the same BEK, authenticate/decrypt the payload, validate the payload schema, then pass records to the restore transaction.
- On a new device, the user first creates/opens a local vault and chooses a new master password; the phrase unlocks the backup's BEK, not an old device's Keystore key. Decrypted records are imported into that local vault.
- Never export a plaintext intermediate file. Never upload the phrase, BEK or decrypted payload to a service.

## UX requirements

- Export: require backup password + confirmation and 24-word recovery phrase re-entry; clearly explain that both unlock methods will work for this v2 file. Do not store the phrase after export.
- Restore: provide separate actions for “restore with backup password” and “restore with 24-word recovery phrase”. Do not ask for both during restore.
- Explain that SLNB v1 files remain password-only.
- Clear fields after submission and on navigation/cancellation; use CharArray where possible. State that clearing is best-effort for immutable UI strings.
- Do not show success until decryption, validation and the full restore transaction have succeeded.

## Required tests before implementation can be accepted

1. Deterministic KDF/HKDF domain-separation tests and official BIP-39 vectors for 24-word entropy conversion.
2. v2 round-trip with password; v2 round-trip with recovery phrase; both yield identical payload.
3. v1 golden fixture still decodes with password; v1 never decodes with recovery phrase.
4. Wrong password, wrong phrase, invalid phrase checksum, malformed version/flags, truncated wrappers, trailing bytes and tampered preamble/wrapper/payload all fail closed.
5. KDF parameters above the current caps are rejected before KDF invocation; oversized payload is rejected before large allocation.
6. Wrapper swap/removal and slot-label substitution tests.
7. Secret wiping on success and every exception path (test observable ownership/cleanup where feasible).
8. New-device flow test: create a fresh empty vault, restore v2 using phrase, verify every record and TOTP secret, then reopen with the new master password.
9. Fuzz the v2 parser and restore pipeline with bounded resource use.
10. CI: unit tests, debug APK, minified release build and Android Lint all pass.

## Risks and open implementation checks

- The existing recovery phrase is not persisted. Re-entry at export is intentional and must be designed to avoid shoulder-surfing and accidental logging.
- The payload-size cap and JSON parser bounds need a measured value; do not choose an arbitrary unlimited allocation.
- Existing Backup.decode currently assumes v1. Version dispatch must be explicit and preserve v1 golden fixtures.
- Restore is currently not guaranteed atomic. v2 cryptography must not be considered complete until M6's all-or-nothing restore transaction is implemented and tested.
- This ADR is a design decision, not a security certification. Independent cryptographic review and physical-device testing remain required.
