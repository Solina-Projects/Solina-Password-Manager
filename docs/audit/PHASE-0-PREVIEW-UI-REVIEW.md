# Phase 0 — Preview Branch Baseline Review

- Repository: `Solina-Projects/Solina-Password-Manager`
- Reviewed branch: `preview/ui`
- Working branch for this review: `preview/rebuild-phase-01`
- Review date: 2026-11-12
- Status: Static source review in progress; no claim of runtime or penetration-test completion.

## Important baseline correction

The default branch `main` is substantially behind `preview/ui`. At review time, GitHub comparison reported `preview/ui` as 32 commits ahead of `main` and not behind it. The initial review of only `main` therefore did not represent the most advanced project state.

The `preview/ui` branch contains:
- A conventional single-module Android Gradle project using Kotlin and Jetpack Compose.
- A checked-in Android manifest and application source tree.
- Password vault, Argon2id/AES-GCM primitives, BIP-39 recovery, Room persistence, biometric unlock, TOTP, import/export, Autofill and folder-based sync implementations.
- Unit tests for crypto, vault, backup, OTP, imports, storage, sync, and hardening.
- Architecture, design, threat-model, development-plan and security documentation.
- A GitHub Actions workflow that runs unit tests, builds debug and release variants, and runs Android Lint.

This branch is a substantial prototype and should be audited and improved rather than discarded.

## Positive findings

1. The app is a real checked-in Android project on `preview/ui`; the default branch's generated Java placeholder is not representative of this branch.
2. Vault data items are encrypted before Room persistence. The Room rows contain IDs, revision/timestamps, wrapped item keys and ciphertext blobs.
3. The cryptographic implementation uses Argon2id, HKDF-SHA256 and AES-256-GCM; each item receives a random item key and the item metadata is bound as AEAD associated data.
4. The vault uses a random Vault Key wrapped separately for master-password and BIP-39 recovery access. Password changes re-wrap the Vault Key instead of re-encrypting every item.
5. Biometric unlock is backed by Android Keystore and BiometricPrompt; the code documents the limitation that clearing JVM arrays cannot guarantee removal of all managed-runtime copies.
6. Room updates archive the previous encrypted item and upsert within a database transaction.
7. Sync records are authenticated with an HMAC key derived from the Vault Key, and the sync engine uses deterministic conflict ordering.
8. The UI and README explicitly identify the app as pre-alpha and warn users not to store real secrets before independent review.
9. The current preview workflow uses Gradle to run unit tests, assemble debug/release and run lint; it does not generate the placeholder Activity used on `main`.

## Findings requiring attention

### P0 — Untrusted KDF parameters can cause resource exhaustion

Files:
- `app/src/main/kotlin/app/solina/passwordmanager/core/crypto/Primitives.kt`
- `app/src/main/kotlin/app/solina/passwordmanager/core/crypto/Vault.kt`
- `app/src/main/kotlin/app/solina/passwordmanager/core/backup/Backup.kt`

The generic `KdfParams` bounds allow up to 256 MiB and 10 iterations. Vault headers and backup files carry KDF parameters which are parsed before password verification. A crafted local vault header or imported backup can therefore request the maximum permitted memory/CPU cost during unlock/import. This is a denial-of-service and reliability risk even though the values are bounded.

Required remediation:
- Define a strict, versioned policy for accepted KDF parameters on unlock/import.
- Reject unreasonable parameters before invoking Argon2id; do not silently downgrade or clamp attacker-controlled parameters.
- Consider a user-visible compatibility path for legitimately older vaults.
- Add tests for extreme but currently valid memory/iteration/parallelism combinations and for graceful failure under memory pressure.

### P1 — Backup format and architecture documentation do not match

Files:
- `docs/ARCHITECTURE.md` §6
- `app/src/main/kotlin/app/solina/passwordmanager/core/backup/Backup.kt`
- `app/src/main/kotlin/app/solina/passwordmanager/MainActivity.kt`

The architecture describes a backup key that may also be wrapped using the recovery phrase, but the current backup implementation derives its encryption key only from the independent backup password. The 24-word vault recovery phrase does not currently provide the documented backup recovery route.

Required remediation:
- Decide whether backup recovery phrase support is part of the intended format.
- If yes, design and version the format before release, then add compatibility and tamper tests.
- If no, correct architecture, onboarding and backup UI copy so users are not led to believe the phrase can decrypt a password-protected backup.

### P1 — Backup export password is not wiped on every path

File:
- `app/src/main/kotlin/app/solina/passwordmanager/MainActivity.kt`

The export handler wipes the password char array after successful encoding, but the catch path does not guarantee wiping if encoding throws.

Required remediation:
- Move password wiping into a `finally` block.
- Review all secret-bearing UI callbacks for exception-safe cleanup.
- Clear temporary ciphertext/plaintext buffers when no longer needed.

### P1 — Batch restore/import is not atomic

Files:
- `app/src/main/kotlin/app/solina/passwordmanager/core/storage/VaultRepository.kt`
- `app/src/main/kotlin/app/solina/passwordmanager/core/storage/RoomStore.kt`
- `app/src/main/kotlin/app/solina/passwordmanager/MainActivity.kt`

`importItems` persists records one at a time. If a later write fails, earlier records may already have been committed. The architecture and product requirements describe safe/atomic restore behavior, but the current import path does not provide a single all-or-nothing transaction.

Required remediation:
- Add a batch store operation that runs in one Room transaction, or implement staging database + atomic swap.
- Validate and decrypt all imported entries before mutating the live vault.
- Add fault-injection tests proving no partial restore remains after failure.

### P1 — Folder sync writes are not crash-safe

File:
- `app/src/main/kotlin/app/solina/passwordmanager/sync/FolderRemote.kt`

When a sync filename already exists, `write` opens it with mode `wt`, truncating it before writing. A process interruption or provider failure can leave a partial file under the final immutable filename. The sync engine may then see the filename as already present and skip rewriting it.

Required remediation:
- Use a temporary object/name, verify the completed bytes, then promote it where the SAF provider supports safe rename.
- Where atomic promotion is unavailable, delete and recreate only after validating the existing object is corrupt and coordinating writes.
- Add interruption and concurrent-writer tests.

### P1 — Successful UI messages can be shown after failed operations

File:
- `app/src/main/kotlin/app/solina/passwordmanager/MainActivity.kt`

Some delete/restore callbacks wrap repository calls in `runCatching` but show success messages regardless of whether the operation failed.

Required remediation:
- Show success only after a successful result.
- Surface recoverable failures and keep the user on the relevant screen.
- Add UI tests for storage failures.

### P2 — Product scope is ahead of the actual data model

Files:
- `README.md`
- `docs/ARCHITECTURE.md`
- `app/src/main/kotlin/app/solina/passwordmanager/core/storage/ItemPayload.kt`

The README/architecture describe identity records and broader item types, while the current `ItemKind` enum contains Login, Card and Note. TOTP is represented as fields on the payload rather than a separate item kind. This may be intentional for MVP, but documentation and UI should not imply unsupported record types.

Required remediation:
- Align the 1.0 feature matrix with implemented item types.
- Add Identity only with a defined schema, validation, import/export and tests.

### P2 — Architecture plan and implementation maturity differ

Files:
- `docs/ARCHITECTURE.md`
- `docs/MASTER_PROMPT.md`
- `settings.gradle.kts`
- `app/build.gradle.kts`
- `app/src/main/kotlin/app/solina/passwordmanager/AppContainer.kt`
- `app/src/main/kotlin/app/solina/passwordmanager/MainActivity.kt`

The current code is a single Gradle app module with a manual `AppContainer`; the architecture plan describes multiple core/feature/data modules and Hilt. The main activity is also a large composition/navigation coordinator. This is not automatically a security vulnerability, but it increases coupling and makes independent testing/review harder.

Required remediation:
- Split modules incrementally only after dependency boundaries are mapped.
- Keep cryptography isolated and enforce dependency rules.
- Extract navigation/state holders from MainActivity; avoid a risky full rewrite.

### P2 — CI and supply-chain controls are incomplete

File:
- `.github/workflows/build_apk.yml`
- `gradle/libs.versions.toml`
- `.github/dependabot.yml`

Positive: CI runs unit tests, debug/release builds and Android Lint; Dependabot updates Gradle and GitHub Actions weekly.

Remaining:
- Add dependency vulnerability scanning and an SBOM.
- Pin third-party GitHub Actions to reviewed full commit SHAs.
- Add detekt/ktlint or document an alternative.
- Confirm Gradle Wrapper is committed and used in CI rather than relying only on a separately installed Gradle version.
- Review workflow permissions and artifact provenance.
- Verify release signing remains external to source control.

## Not yet verified

- No local Gradle build or test execution has been performed in this review.
- No CI result for the current preview head has been independently inspected.
- No APK was installed or tested on a physical device.
- No dynamic analysis, fuzz campaign, penetration test or independent cryptographic review has been performed.
- No full repository settings/branch-protection review has been completed.
- No claim is made that the implementation is safe for production credentials.

## Recommended execution order from this audit

1. Finish M0 inventory: CI result, wrapper, repository settings, dependency/license review and test baseline.
2. Resolve P0 KDF resource-exhaustion policy.
3. Resolve P1 backup/recovery format mismatch and exception-safe secret cleanup.
4. Make import/restore transactional.
5. Make sync writes resilient to interrupted/concurrent writes.
6. Fix success/error reporting and add regression tests.
7. Reconcile product scope and architecture; then modularize incrementally.
8. Add supply-chain controls and run the full device/security test matrix.

## Review status

| Area | Status |
|---|---|
| Main branch baseline | Reviewed |
| Preview branch source tree | Partially reviewed |
| Architecture/design docs | Reviewed |
| Crypto primitives and vault format | Static review started |
| Backup/recovery | Static review started; mismatch found |
| Room persistence | Static review started |
| Sync protocol/provider | Static review started; crash-safety issue found |
| Autofill | Not fully reviewed |
| TOTP/import parsers | Not fully reviewed |
| UI/accessibility | Not fully reviewed |
| CI execution | Not verified |
| Physical-device testing | Not performed |
| Independent security review | Not performed |
