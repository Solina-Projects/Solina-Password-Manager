# ADR-0001: Android platform and modular architecture

- Status: Proposed
- Date: 2026-11-12
- Scope: Initial technical direction for Solina Password Manager

## Context

The repository currently does not expose a conventional checked-in Gradle Android application in the standard paths inspected. The existing workflow creates a small Java Activity during CI rather than compiling a maintained application source tree. The product requires a maintainable Android client with a high-assurance local vault and optional future sync and agent integrations.

## Decision

Adopt the following proposed baseline:

- Kotlin for application code.
- Android Gradle Plugin and Gradle Wrapper for reproducible builds.
- Jetpack Compose and Material 3 for UI.
- Clean Architecture boundaries with feature-oriented modules where module boundaries provide real isolation.
- Room for persistence of encrypted records and non-secret metadata.
- Kotlin Coroutines and Flow for asynchronous operations.
- Android Keystore and BiometricPrompt for device-bound key protection and biometric-gated convenience unlock.
- WorkManager only for deferrable background work; never use it as a security boundary.
- GitHub Actions to build the checked-in Gradle project, run tests and static checks.
- Feature branches and reviewed pull requests; no direct implementation commits to main.

## Security constraints

- No plaintext passwords, TOTP seeds, recovery codes, master passwords, or cryptographic keys in logs, analytics, crash reports, clipboard history, or unencrypted database columns.
- The server, if introduced, must not receive vault plaintext or user-held decryption keys.
- Android Keystore protects device-bound key material but does not replace a user-derived vault key or a documented recovery design.
- Biometric authentication is an unlock convenience, not a replacement for the master password.
- Cryptographic libraries and parameters remain provisional until dependency, maintenance, platform compatibility, and security review are completed.
- Do not claim AES-256-GCM, Argon2id, zero-knowledge, hardware-backed protection, or passkey support until the implementation and tests prove the claim.

## Proposed module boundaries

- :app — application composition, navigation and dependency wiring.
- :core:common — shared primitives without secret-bearing global state.
- :core:designsystem — theme, tokens and reusable Compose components.
- :core:crypto — key derivation, authenticated encryption and vault format.
- :core:security — session, lock policy, secure window and device-bound key operations.
- :core:database — Room entities and migrations; encrypted payloads only.
- :domain — entities, repository contracts and use cases.
- :data — repository implementations and persistence adapters.
- :feature:onboarding, :feature:auth, :feature:vault, :feature:generator, :feature:autofill, :feature:authenticator, :feature:settings.
- Future isolated components: sync protocol/client and agent gateway.

The project may begin as a small number of Gradle modules and split further when build time, dependency direction, or security isolation justify it. Avoid creating many empty modules prematurely.

## Open decisions — must be resolved before implementation

1. Minimum supported Android API and target SDK policy.
2. Argon2id implementation and native/JVM dependency, including supply-chain review.
3. AEAD implementation and keyset format.
4. Biometric unlock design, including device credential fallback and invalidation behavior.
5. Vault recovery model and explicit consequences of forgotten master password.
6. Whether sync is self-hosted, hosted, or both; server protocol and metadata minimization.
7. Passkey scope: relying-party credential use through Credential Manager versus password-manager credential provider responsibilities.
8. Agent interface: local-only IPC/API, caller identity, permission grants and user approvals.
9. App package ID, branding assets, license and distribution channels.

## Consequences

Positive:
- Standard Android tooling and maintainable source tree.
- Testable security boundaries.
- Clear separation between UI, business rules, persistence and cryptography.
- CI can validate the actual application rather than a generated placeholder.

Trade-offs:
- More initial setup than a one-file demo.
- Native cryptographic dependencies may require ABI and device testing.
- High-assurance sync and agent features require separate protocol and threat reviews.
