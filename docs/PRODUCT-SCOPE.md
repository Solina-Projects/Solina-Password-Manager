# Product Scope — Solina Password Manager

- Status: Proposed baseline for review
- Date: 2026-11-12

## Product vision

Solina is an Android-first, privacy-focused password and digital-identity manager. It should provide a dependable offline vault first, then add optional encrypted multi-device sync and carefully permissioned AI-agent workflows. Security and recoverability take precedence over feature count.

## Target users and primary jobs

- People who need to store, retrieve and generate credentials securely on Android.
- Users who want an offline-capable vault and control over when data leaves the device.
- Advanced users who want authenticator, Autofill, passkeys and multi-device workflows.
- Users who want an AI agent to perform narrowly scoped tasks without granting unrestricted access to all secrets.

## Product principles

1. Offline-first: core vault operations work without a network connection.
2. User custody: the user controls the master secret and recovery choices.
3. Least privilege: features, agents and integrations receive only the access they need.
4. Honest security: UI and marketing claims must match tested implementation.
5. Portable data: encrypted backup and documented import/export are first-class.
6. No hidden telemetry of vault content.
7. Progressive complexity: a simple secure default, with advanced controls when needed.

## Release scope

### MVP (first usable local release)
- First-run setup and master-password creation.
- Local encrypted vault.
- Unlock, lock, auto-lock and session lifecycle.
- Login records with title, username, password, URI/domain, notes and timestamps.
- Search, categories, favorites, reveal and copy.
- Password/passphrase generator.
- Encrypted backup and restore with explicit recovery warnings.
- Basic security settings and clear data-loss messaging.
- Accessibility, light/dark theme and localization foundations.

### Post-MVP
- Android Autofill Framework.
- TOTP authenticator.
- Security health checks (reuse/weakness/age, without falsely claiming breach detection).
- More item types such as secure notes and identities.
- Passkeys through supported Android credential APIs.
- Optional zero-knowledge multi-device sync.
- Permissioned agent gateway.

### Explicit non-goals for MVP
- No mandatory account or cloud registration.
- No server-side password storage.
- No AI model access to vault contents.
- No browser extension or desktop client until the Android security model is stable.
- No promise of recovery if the master password is forgotten unless a separately reviewed recovery design is implemented.
- No automatic scraping or breach monitoring without a privacy-preserving, clearly scoped design.

## Key user journeys

1. Create vault → understand recovery warning → set master password → unlock.
2. Add credential → validate required fields → encrypt → persist atomically.
3. Find credential → authenticate/unlock → reveal/copy with a short exposure window.
4. Lock app → background/process death/timeout → clear in-memory session material as far as platform allows.
5. Export backup → re-authenticate → create encrypted archive → verify archive → user selects destination.
6. Restore backup → inspect format/version → verify integrity → preview conflict policy → restore atomically.

## MVP acceptance criteria

- A fresh checkout builds through the checked-in Gradle project.
- A user can create, unlock, lock and delete a vault.
- Credential data is encrypted before persistence; database inspection shows no plaintext secret fields.
- Wrong master password does not unlock the vault.
- Ciphertext modification is detected and never silently accepted.
- Backup can be restored on a second compatible installation using the documented secret/recovery process.
- App lock and process-death behavior are covered by tests.
- No secrets appear in logs, crash output, analytics or notifications.
- The app makes no unsupported security claims.

## Product metrics (privacy-preserving)

- Crash-free sessions and build/test reliability.
- Time to unlock and complete common vault tasks (locally aggregated only if user opts in).
- Backup creation and restore success rates (without collecting backup contents).
- Autofill match correctness after that feature ships.
- Security test coverage and unresolved vulnerability counts.

Never collect passwords, usernames, URLs, notes, TOTP seeds, master-password-derived material, or vault contents as product analytics.
