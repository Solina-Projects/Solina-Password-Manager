# Solina — Live Project Status

Last updated: 2026-11-12

## Active baseline

- Main branch: `main` — contains an older generated-placeholder APK workflow.
- Advanced prototype: `preview/ui` — 32 commits ahead of main at the time of review.
- Current audit branch: `preview/rebuild-phase-01` — created from `preview/ui`; contains the Phase 0 static-review report.
- Main has not been modified by this review.
- No changes to cryptographic or application runtime code have been made in this audit branch.

## Current execution state

| Phase | Existing implementation found | Status | Remaining to pass phase gate |
|---|---|---|---|
| M0 — Research and audit | README, architecture, design, threat model, reference audit, source tree and CI inspected | In progress | Finish full source review, verify CI baseline, repository settings, dependency/license inventory and runtime behavior |
| M1 — Android foundation | Kotlin, Compose, Gradle app module, Version Catalog, Dependabot, CI build/test/lint | Partially implemented | Add/verify Gradle Wrapper, modular boundaries, static-analysis and dependency scanning, SBOM, workflow pinning and release provenance |
| M2 — Security core | Argon2id, HKDF, AES-GCM, BIP-39, Vault Key wrapping, biometric Keystore, session lock, unit tests | Prototype implemented; not approved | Fix hostile KDF resource policy, verify secret cleanup, expand lifecycle/fuzz tests, independent crypto review |
| M3 — Vault | Room encrypted rows, repository, history, Login/Card/Note model, search/UI | Prototype implemented; not approved | Transactional batch import/restore, migration/schema export, data-model scope alignment, UI/integration tests |
| M4 — Autofill | AutofillService, parser/logic, authentication activity and tests | Prototype implemented; not approved | Full domain/app-signature review, phishing/origin-confusion tests and physical-device/browser matrix |
| M5 — Authenticator | TOTP, QR scan and import foundations | Prototype implemented; not approved | Verify HOTP scope, RFC vectors, import edge cases and secret lifecycle |
| M6 — Backup/import | Password-protected encrypted backup and multiple importers | Prototype implemented; not approved | Resolve recovery-phrase/backup format mismatch, transactional restore, parser limits/fuzz and restore-on-new-device tests |
| M7 — Hardening/beta | Some hardening tests, diagnostics and pre-alpha warnings | Not complete | MASVS mapping, accessibility, fuzzing, dependency scan, SBOM, independent security assessment and remediation |
| M8 — Sync | SAF folder-based encrypted record sync, HMAC-authenticated records and conflict handling | Prototype implemented; not approved | Crash-safe writes, concurrent-writer tests, metadata/replay/rollback review, two-device and provider matrix |
| Agent Gateway — future | No implementation found in reviewed tree | Not started | Define capabilities, caller authentication, approvals, revocation, audit and prompt-injection threat model |
| Release | Preview build workflow and unsigned release build | Not approved | All phase gates, stable protected signing, provenance, privacy/recovery docs and independent review |

## Current blockers (ordered)

1. P0: Strictly validate KDF cost parameters before Argon2id on vault unlock and backup import.
2. P1: Align the backup format with the promised recovery phrase behavior.
3. P1: Make export secret cleanup exception-safe.
4. P1: Make batch import/restore all-or-nothing.
5. P1: Make SAF sync writes resilient to interruption and concurrent writers.
6. P1: Correct UI success messages after failed storage operations.
7. P2: Reconcile planned identity item type with the implemented Login/Card/Note model.
8. P2: Improve CI supply-chain controls and verify wrapper/build reproducibility.

## CI baseline

- Run: [Build Solina APK #37](https://github.com/Solina-Projects/Solina-Password-Manager/actions/runs/43298374564)
- Trigger: push to `preview/rebuild-phase-01`.
- Initial observation: checkout and Java/Gradle setup completed; unit tests started.
- Final conclusion: pending at time of this update.

## Reporting rule

At the end of every phase, update this file and `docs/MASTER_PROMPT.md` with:
1. What was implemented.
2. What was tested and exact results.
3. What remains incomplete.
4. Open risks and decisions required.
5. The next phase recommendation.

A phase is not complete because code exists. It is complete only after its acceptance criteria pass and the evidence is recorded.
