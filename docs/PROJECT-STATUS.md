# Solina — Live Project Status

Last updated: 2026-11-13

## Active baseline

- Main branch: `main` — contains an older generated-placeholder APK workflow.
- Advanced prototype: `preview/ui` — 32 commits ahead of main at the time of review.
- Current audit branch: `preview/rebuild-phase-01` — created from `preview/ui`; contains the Phase 0 static-review report.
- Main has not been modified by this review.
- Runtime security changes have been made on this branch: strict KDF bounds and exception-safe password-array cleanup. Main remains unchanged.

## Current execution state

| Phase | Existing implementation found | Status | Remaining to pass phase gate |
|---|---|---|---|
| M0 — Research and audit | README, architecture, design, threat model, reference audit, source tree and CI inspected | Audit baseline recorded; broader governance review remains | Finish remaining source review, repository settings, dependency/license inventory and runtime behavior |
| M1 — Android foundation | Kotlin, Compose, Gradle app module, Version Catalog, Dependabot, CI build/test/lint | Partially implemented | Add/verify Gradle Wrapper, modular boundaries, static-analysis and dependency scanning, SBOM, workflow pinning and release provenance |
| M2 — Security core | Argon2id, HKDF, AES-GCM, BIP-39, Vault Key wrapping, biometric Keystore, session lock, strict KDF bounds | Prototype implemented; not approved | Expand lifecycle/fuzz tests, verify on physical devices, independent crypto review |
| M3 — Vault | Room encrypted rows, repository, history, Login/Card/Note model, search/UI | Prototype implemented; not approved | Transactional batch import/restore, migration/schema export, data-model scope alignment, UI/integration tests |
| M4 — Autofill | AutofillService, parser/logic, authentication activity and tests | Prototype implemented; not approved | Full domain/app-signature review, phishing/origin-confusion tests and physical-device/browser matrix |
| M5 — Authenticator | TOTP, QR scan and import foundations | Prototype implemented; not approved | Verify HOTP scope, RFC vectors, import edge cases and secret lifecycle |
| M6 — Backup/import | SLNB v1/v2 codec and UI; batch import uses one Room transaction; BIP-39/v1/v2 regression tests and rollback contract test | CI run #43370732657 passed; partially implemented; not approved | Parser fuzz/size-bound tests, real Room instrumentation rollback test, new-device workflow and physical-device tests |
| M7 — Hardening/beta | Some hardening tests, diagnostics and pre-alpha warnings | Not complete | MASVS mapping, accessibility, fuzzing, dependency scan, SBOM, independent security assessment and remediation |
| M8 — Sync | SAF folder-based encrypted record sync, HMAC-authenticated records and conflict handling; staged write/verify/rename and retry repair for partial immutable files | Hardening implementation added; phase 6 CI passed; not approved | Verify provider rename behavior on physical devices, cross-device concurrent writers, metadata/replay/rollback review and provider matrix |
| Agent Gateway — future | No implementation found in reviewed tree | Not started | Define capabilities, caller authentication, approvals, revocation, audit and prompt-injection threat model |
| Release | Preview build workflow and unsigned release build | Not approved | All phase gates, stable protected signing, provenance, privacy/recovery docs and independent review |

## Current blockers (ordered)

1. P1: Complete SLNB v2 verification: parser fuzz/size limits, Room transaction instrumentation, new-device and physical-device tests; SLNB v1 remains password-only.
3. P1: Verify the new all-or-nothing batch persistence contract against the real Room database and failure injection.
4. P1: Verify staged SAF sync writes on actual providers and test cross-device concurrent writers; provider rename guarantees vary.
5. P1: Verify truthful failure feedback for all remaining storage and file operations.
6. P1: Enforce PSL/eTLD+1 and package-signature checks in Autofill; remove unknown-app all-credential fallback.
7. P2: Do not report Autofill save success while save is unsupported.
8. P2: Align HOTP claims with actual TOTP-only implementation.
9. P2: Reconcile planned identity item type with the implemented Login/Card/Note model.
10. P2: Improve CI supply-chain controls and verify wrapper/build reproducibility.

## CI baseline

- KDF hardening run #55: **success** — unit tests, debug APK, unsigned/minified release build, Android Lint and artifact uploads all passed.
- Backup/password-cleanup fix run #66: **success** — unit tests, debug APK, unsigned/minified release build, Android Lint and artifact uploads all passed.
- [Run #55](https://github.com/Solina-Projects/Solina-Password-Manager/actions/runs/43340163866) · [Run #66](https://github.com/Solina-Projects/Solina-Password-Manager/actions/runs/43346057262).
- This validates the current branch build and automated checks; it is not a physical-device security assessment.
- Phase 2 is closed. SLNB v2 codec and Android UI integration are implemented on `preview/rebuild-phase-01`; CI run #43367372453 passed for codec and run #43369298095 passed for UI integration. Phase 5 backup/restore regression tests and transactional batch-import changes passed CI run #43370732657. Phase 6 adds staged SAF writes and retry repair; CI run #43372548383 passed. M6/M8 and release remain unapproved.
- CI currently installs Gradle 8.10.2 directly; no Gradle Wrapper files were found.

## Repository governance observations



- Branches API reports `main` and `preview/ui` as unprotected; repository rulesets endpoint returned an empty list.
- No published releases were found.
- The connected GitHub integration could not read the detailed branch-protection endpoint (403), so exact protection settings remain unverified.
- Gradle Wrapper files were not found in the preview branch; CI installs Gradle 8.10.2 directly.
- The app is a single Gradle module (`:app`), despite the multi-module target architecture.
- The workflow references GitHub Actions by mutable major tags (`@v4`) rather than immutable full commit SHAs.
- The version catalog includes AndroidX, Compose, Room, Bouncy Castle, ZXing and CameraX; a machine-readable dependency vulnerability/SBOM report has not yet been generated.
- The checked-in `LICENSE` is GPL-3.0 text; confirm the intended copyright holder and align README notices that claim GPL-3.0-or-later before release.

## Phase 7 — SAF permission persistence and header error reporting

- `SyncManager.persist()` requires a durable Android read/write URI grant before saving the selected tree configuration. Existing persisted flags are reused; missing flags are acquired explicitly.
- Configuration uses synchronous `commit()` and reports failure instead of silently applying a folder URI. Newly acquired flags are released if saving preferences fails.
- `pushHeader()` returns an explicit result, logs failures and exposes a warning in sync status; the password-reset flow surfaces that warning.
- Final CI run #43429214991 for `0eb1c32981e9ce02cdfe6a43cd12f3eb56fa4ba0`: **success** — unit tests, debug APK, unsigned/minified release build, Android Lint and artifact uploads passed.
- Remaining: verify URI grants and provider behavior with real Android SAF providers/devices; this phase is code/CI-validated, not device-validated.

## Reporting rule

At the end of every phase, update this file and `docs/MASTER_PROMPT.md` with:
1. What was implemented.
2. What was tested and exact results.
3. What remains incomplete.
4. Open risks and decisions required.
5. The next phase recommendation.

A phase is not complete because code exists. It is complete only after its acceptance criteria pass and the evidence is recorded.


## Phase 8 — truthful vault-operation feedback (in progress)

- Delete and history-restore callbacks now show success only after the repository operation returns successfully.
- Exceptions are logged and surfaced as explicit failure messages; failed deletion keeps the detail screen open.
- Automated CI validation is pending. UI behavior still needs manual verification on an Android device.
