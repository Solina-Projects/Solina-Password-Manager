# Initial Threat Model — Solina Password Manager

- Status: Draft; not a security certification
- Date: 2026-11-12

## Assets to protect

- Master password and derived keys.
- Vault data: passwords, usernames, secure notes, identities, payment data.
- TOTP seeds, recovery codes and passkey-related material.
- Backup files and sync ciphertext.
- Device/session authorization state.
- Agent capability grants and audit metadata.

## Adversaries and failure scenarios

- A person with temporary or persistent physical access to an unlocked or locked device.
- A malicious or compromised application attempting to invoke exported components, abuse Autofill, read clipboard contents, or capture UI.
- Malware on a rooted or otherwise compromised device.
- A compromised cloud account, sync server, or network path.
- A malicious website/app attempting to trick Autofill or an AI agent into disclosing credentials.
- Supply-chain compromise of build tools, dependencies, GitHub Actions, or release signing.
- Accidental disclosure through logs, crash reports, screenshots, backups, analytics, or support bundles.
- User error, forgotten master password, device loss, corrupted storage, or interrupted migration.

## Security objectives

1. Confidentiality of vault contents at rest and in transit.
2. Integrity and authenticity of encrypted records and metadata.
3. Strong separation between user-derived vault keys and device-bound convenience keys.
4. Safe lock behavior after inactivity, backgrounding, process death, and device state changes.
5. Least-privilege access for Autofill, sync and agents.
6. No plaintext secrets in server storage, telemetry or diagnostic output.
7. Predictable recovery and migration behavior with no hidden backdoor.

## Trust boundaries

- UI ↔ domain/use cases.
- Domain ↔ crypto engine.
- Crypto engine ↔ Android Keystore.
- App ↔ local database/filesystem.
- App ↔ Autofill Framework and other apps.
- App ↔ sync service/network.
- App ↔ AI agent/client process.
- Source repository/CI ↔ signed release artifact.

## Initial mitigations

- Use authenticated encryption and versioned encrypted envelopes.
- Derive keys with a reviewed password KDF and per-vault random salt.
- Generate an independent random vault data key; wrap it rather than re-encrypting every record when changing the master password.
- Use unique nonces for each encryption under a given AEAD key.
- Bind record identity, schema version and relevant metadata as authenticated associated data where appropriate.
- Keep database payloads encrypted and minimize plaintext metadata.
- Use non-exportable Android Keystore keys for device-bound protection where supported; handle hardware-backed availability as a capability, not a guarantee.
- Validate Autofill package/domain associations and require explicit user action for ambiguous matches.
- Require scoped, expiring, revocable agent permissions and approval for sensitive operations.
- Keep secrets out of logs, clipboard longer than necessary, notifications, backups and crash reporting.
- Protect signing credentials with repository/environment secrets and restrict workflow permissions.

## Residual risks and limitations

- A fully compromised/rooted OS may capture data while the vault is unlocked; root detection cannot guarantee protection.
- Screen capture and accessibility abuse cannot be completely prevented on all Android devices.
- Forgotten master password may make data unrecoverable unless the user configured a deliberately designed recovery mechanism.
- Cloud zero-knowledge claims depend on the complete client protocol, metadata design, update integrity and key lifecycle; encryption alone is not sufficient.
- AI agent permissions cannot fully neutralize prompt injection if the agent is granted broad access.
- This document is an initial design artifact, not a penetration test, cryptographic review, or proof of security.

## Required verification before release

- Crypto known-answer tests, tamper tests, wrong-key tests, nonce strategy review and fuzz/property tests.
- Database, backup, restore, migration and crash-consistency tests.
- Lock/session lifecycle tests on physical Android devices.
- Autofill origin-confusion and malicious-app tests.
- Sync replay/conflict/rollback and server plaintext inspection.
- Agent permission escalation and prompt-injection tests.
- Independent security review of cryptographic and sync protocols.
