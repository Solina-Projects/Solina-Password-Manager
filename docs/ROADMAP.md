# Solina Password Manager — Execution Roadmap

## Project rules

- The `main` branch is protected by process: all implementation work happens on feature/rebuild branches and reaches `main` only through reviewed pull requests.
- Never describe a build as a usable password manager until vault encryption, key management, recovery behavior, and security tests have passed.
- Never put signing keys, passwords, API tokens, recovery secrets, or test credentials in source control or workflow logs.
- Security-sensitive features require tests and a documented threat-model review before release.
- A completed phase means its exit criteria are demonstrated, not merely that files were added.

## Baseline (Phase 0) — Repository audit

**Status: Initial audit completed; deeper source and runtime audit remains.**

Observed on the default branch during the initial review:
- The README contains only a project heading and one-line description.
- The only confirmed application build workflow inspected is `.github/workflows/build_apk.yml`.
- That workflow generates a Java Activity during CI rather than building a checked-in Android application module.
- The generated Activity is a static informational screen; it does not implement a password vault, encryption, authentication, database, or sync.
- The workflow creates a new signing keystore using a hard-coded password on each run. This is unsuitable for release signing and does not provide stable app-signing identity.
- Standard Gradle project paths and a checked-in Android manifest were not found in the initial path checks. This is not proof that no files exist elsewhere in the repository.
- No source-level security audit or device/runtime test has yet been performed.

### Remaining audit tasks
- Enumerate every tracked file and branch; inspect all workflows and repository settings.
- Check open issues, pull requests, releases, tags, and existing artifacts.
- Inspect dependency, license, secret-scanning, and branch-protection configuration.
- Reproduce the current workflow and record its actual result before replacing it.
- Preserve any useful assets or prototypes before restructuring.

## Phase 1 — Product definition and architecture

**Status: In progress.**

Deliverables:
- Product scope and non-goals.
- Modular Android architecture and module boundaries.
- Data-flow and key hierarchy design.
- Threat model and security requirements.
- ADRs for cryptography, local storage, biometric unlock, backup, sync, and agent access.
- Acceptance criteria and release gates.

Exit criteria:
- No unresolved high-risk architectural decision is hidden behind implementation assumptions.
- Key hierarchy, recovery limitations, and zero-knowledge claims are documented and reviewable.

## Phase 2 — Android engineering foundation

Deliverables:
- Standard Gradle Android project, Kotlin, Compose, version catalog, wrapper, lint and test configuration.
- CI that builds the checked-in app and runs unit tests and static checks.
- Reproducible debug builds and documented release-signing process.
- Package identity, SDK policy, app metadata, and baseline permissions.

Exit criteria:
- Clean checkout builds through Gradle in CI.
- No generated placeholder Activity is used as the app implementation.
- Debug APK installs and launches on supported Android versions.
- Release signing material is external to the repository and never generated with a committed password.

## Phase 3 — Design system and application shell

Deliverables:
- Tokens for color, typography, spacing, shape, elevation, and motion.
- Light/dark themes, accessibility and localization foundations.
- Navigation shell, onboarding, unlock, empty states, loading and error states.
- Reusable Compose components and UI previews.

Exit criteria:
- Core screens are consistent, accessible, responsive, and usable with large text and screen readers.
- UI contains no misleading security claims.

## Phase 4 — Cryptographic core

Deliverables:
- Reviewed cryptographic dependency selection and pinned versions.
- Master-password key derivation using Argon2id with per-vault random salt and calibrated parameters.
- Random data-encryption key (DEK), separately wrapped by a key-encryption key (KEK).
- Authenticated encryption (AES-256-GCM or a reviewed equivalent) with unique nonces and authenticated metadata.
- Versioned vault format and key-rotation design.
- Secure memory and error-handling strategy appropriate to Android/JVM limitations.

Exit criteria:
- Known-answer and interoperability tests pass.
- Nonce uniqueness, tamper detection, wrong-password behavior, and format migration are tested.
- No plaintext secrets or key material are written to logs or persistent storage.
- Crypto design receives independent review before production use.

## Phase 5 — Encrypted local vault

Deliverables:
- Room persistence for encrypted records and metadata only.
- Repository and use-case layers.
- CRUD for login records, notes, identities, and payment items as scoped.
- Atomic writes, schema migration, encrypted export/import format.
- Recovery and data-loss warnings.

Exit criteria:
- Database inspection does not reveal plaintext vault fields.
- Crash/restart, corruption, migration, and backup-restore tests pass.
- No destructive migration or overwrite occurs without a safe path.

## Phase 6 — Authentication and session security

Deliverables:
- Master-password setup and unlock.
- Optional biometric convenience unlock backed by Android Keystore policy.
- Auto-lock, background/foreground handling, retry throttling and session invalidation.
- Secure screen handling and clipboard policy.

Exit criteria:
- Biometric enrollment changes, device credential changes, app process death, and lock timeout are tested.
- Biometrics do not silently replace the master-password recovery model.

## Phase 7 — Password-manager capabilities

Deliverables:
- Password generator and strength guidance.
- Search, categories, favorites, secure copy and reveal.
- Android Autofill Framework integration with origin/app association safeguards.
- Import/export from documented formats, with explicit warnings.

Exit criteria:
- Autofill never offers credentials solely on an untrusted or mismatched origin.
- Sensitive UI and clipboard behavior are verified on real devices.

## Phase 8 — Authenticator and Passkeys

Deliverables:
- TOTP generation and secure secret storage.
- Encrypted authenticator migration/backup.
- Passkey support only through supported Android Credential Manager APIs and compatible relying parties.

Exit criteria:
- RFC-compatible TOTP test vectors pass.
- Secret export is protected and user initiated.
- Passkey support is tested against real services; no unsupported promise is made.

## Phase 9 — Cloud and multi-device sync

Deliverables:
- Protocol specification, encrypted envelope format, device identity and conflict strategy.
- Server stores ciphertext and minimal operational metadata only.
- Replay protection, versioning, deletion semantics, and recovery design.
- Sync status and user-visible device management.

Exit criteria:
- Server-side inspection cannot recover vault plaintext.
- Sync, conflict, rollback, account deletion, and lost-device scenarios are tested.
- Protocol receives independent security review before launch.

## Phase 10 — Agent Gateway

Deliverables:
- Explicit capability-based permission model.
- Read/write scopes, per-operation approval, short-lived grants and revocation.
- Local IPC/API boundary with caller authentication and audit events.
- Redaction and strict prohibition on sending vault secrets to model providers by default.

Exit criteria:
- Agent cannot enumerate or export the entire vault without explicit authorization.
- Every sensitive operation is attributable, scoped, revocable, and tested.
- Prompt injection from websites/apps cannot expand privileges.

## Phase 11 — Verification and release

Deliverables:
- Unit, integration, instrumentation, UI, fuzz/property, and migration tests.
- Dependency and secret scanning, SBOM, release provenance and reproducible-build review.
- Independent security assessment and remediation.
- Privacy policy, threat model, recovery guide, release notes and incident response plan.

Exit criteria:
- All release-blocking tests pass.
- No unresolved critical/high security finding.
- Release artifact is signed with a stable, protected key and verified.
- Backup and restore are tested end to end.
- Security claims match the implementation and independent review.

## Live status board

| Phase | Status | Completed | Remaining |
|---|---|---|---|
| 0. Repository audit | Initial review done | README, build workflow and baseline risks inspected | Full tree, settings, artifacts, runtime reproduction |
| 1. Product and architecture | In progress | Roadmap and initial architectural direction documented | Threat model, ADRs, acceptance criteria |
| 2. Android foundation | Not started | — | Gradle app, CI, signing policy |
| 3. Design system | Not started | — | Tokens, Compose shell, screen designs |
| 4. Crypto core | Not started | — | Dependency review, implementation, tests, external review |
| 5. Encrypted vault | Not started | — | Schema, repositories, encrypted persistence |
| 6. Authentication | Not started | — | Master password, biometrics, session controls |
| 7. Password features | Not started | — | CRUD, generator, Autofill, import/export |
| 8. TOTP and Passkeys | Not started | — | TOTP, migration, Credential Manager |
| 9. Cloud sync | Not started | — | Protocol, backend, conflict/recovery tests |
| 10. Agent Gateway | Not started | — | Permission model, local interface, audit |
| 11. Release | Not started | — | Full test matrix, independent audit, signed release |

Last updated: 2026-11-12
