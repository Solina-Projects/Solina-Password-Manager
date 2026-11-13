# ADR-001: Strict KDF resource limits for untrusted input

- Status: Accepted for the pre-alpha format and importer implementation
- Date: 2026-11-13
- Scope: Vault headers, Solina backups, and encrypted third-party imports

## Context

KDF parameters embedded in vault headers and backup files are attacker-controlled until authentication succeeds. Third-party export files are also untrusted. Passing their declared parameters directly to Argon2id, scrypt, or PBKDF2 can cause excessive CPU or memory consumption before the file is authenticated.

## Decision

Apply hard preflight limits before invoking any KDF:

| KDF/input | Maximum |
|---|---:|
| Solina Argon2id (vault and backup) | 64 MiB, 4 iterations, 4 lanes |
| Bitwarden Argon2id import | 64 MiB, 4 iterations, 4 lanes |
| Bitwarden PBKDF2-HMAC-SHA256 import | 1,000,000 iterations |
| Aegis scrypt import | 64 MiB estimated memory, r ≤ 16, p ≤ 4, power-of-two N |
| 2FAS PBKDF2 | Fixed at 10,000 iterations; shared PBKDF2 guard applies |

Validation is performed in the low-level crypto helpers as defense in depth, and also at importer parsing boundaries to return a clear format/import error. Arithmetic for memory estimates uses Long to avoid integer overflow.

## Compatibility and migration

The application is pre-alpha and the existing KDF calibrator emits at most 64 MiB / 3 iterations / 4 lanes. The new limits therefore cover current generated vaults. Files created by external password managers with costs above these limits will be rejected with a clear message rather than being allowed to exhaust device resources. Supporting higher-cost foreign formats later requires an explicit user-controlled, device-tested policy; never silently relax limits based on file contents.

The stricter Solina header limit means any previously hand-crafted or experimental vault using parameters above the new cap will be rejected. No migration is provided in this pre-alpha phase. Before stable release, format-version and migration policy must be documented.

## Security tests

- Reject out-of-range vault KDF values at KdfParams construction, before Argon2id.
- Reject malicious backup headers before KDF execution.
- Reject excessive PBKDF2, Argon2id and scrypt import parameters.
- Keep normal generated vault, backup and supported importer test vectors passing.
