# Security Requirements and Release Gates

- Status: Initial requirements; implementation and verification pending
- Date: 2026-11-12

## Data classification

### Critical secrets
Master password, KDF output, KEK, DEK, plaintext passwords, secure notes, TOTP seeds, recovery codes, private credential material.

### Sensitive metadata
Account titles, usernames, URIs/domains, record IDs, timestamps, device identifiers, sync metadata and security-event details.

### Public/non-sensitive
App version, non-user-specific build metadata and public documentation.

## Mandatory requirements

### SR-01 — Encryption at rest
All vault secret fields must be encrypted before writing to persistent storage. Database schema and logs must not contain plaintext secret fields.

### SR-02 — Authenticated encryption
Use a reviewed AEAD construction. Any ciphertext or authenticated metadata modification must fail closed.

### SR-03 — Key hierarchy
Use an independent random vault data key. Derive a wrapping key from the master password using a reviewed password KDF and unique random salt. Do not reuse keys across unrelated purposes.

### SR-04 — Nonce safety
The encryption design must make nonce uniqueness under each key an explicit invariant and testable property.

### SR-05 — Master password handling
Never store or log the master password. Minimize its lifetime in memory and document platform limitations around managed runtimes and memory clearing.

### SR-06 — Device-bound protection
Use Android Keystore only for device-bound protection and carefully defined convenience unlock. Do not treat Keystore as a substitute for the master-password-based vault design.

### SR-07 — Biometric lifecycle
Biometric unlock must be invalidated or safely re-bound when relevant biometric/device security state changes. Fallback behavior must be explicit and tested.

### SR-08 — Session lifecycle
Lock on configured inactivity and relevant app/device lifecycle events. On lock, revoke in-memory authorization and clear sensitive UI state.

### SR-09 — Clipboard and screen exposure
Sensitive copy operations must be user initiated, time limited where supported, and communicated clearly. Sensitive screens should use platform protections where appropriate without claiming complete screenshot prevention.

### SR-10 — Backup and recovery
Backups must be encrypted, authenticated, versioned and integrity-checked before restore. Restore must be atomic or safely recoverable after interruption.

### SR-11 — Network and sync
No plaintext vault content or decryption keys may be sent to a sync service. Define replay protection, conflict handling, deletion and device revocation before implementation.

### SR-12 — Autofill
Autofill must validate package/domain context and avoid suggesting credentials for ambiguous or mismatched origins. Sensitive fill actions must be auditable and user controllable.

### SR-13 — Agent permissions
Agent operations must use least-privilege scopes, explicit approvals for sensitive actions, short-lived grants, revocation and audit records. No default bulk export or unrestricted vault read.

### SR-14 — Diagnostics
Logs, analytics, crash reports, notifications and support bundles must be reviewed for secret leakage. Secret-bearing objects must be excluded/redacted by construction.

### SR-15 — Build and supply chain
Pin dependencies and actions to reviewed versions/SHAs where practical. Minimize GitHub Actions permissions. Never generate a release key with a committed password or store release signing credentials in the repository.

## Blocking release gates

- Critical or high severity security findings: zero open.
- Crypto vectors, tamper detection, wrong-password and nonce strategy tests: pass.
- Backup/restore and migration tests: pass.
- Session and biometric lifecycle tests: pass.
- No known plaintext secret persistence or diagnostic leakage.
- Dependency and secret scans: pass or have reviewed, documented exceptions.
- Release artifact signature verified and stable across upgrades.
- Security and recovery documentation matches actual behavior.
