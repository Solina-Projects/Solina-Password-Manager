# معماری سیستم Solina Password Manager (Phase 2)

Solina با معماری لایه‌ای (**Clean Architecture + MVVM**) طراحی شده است تا هستهٔ رمزنگاری و منطق دامنه کاملاً مستقل از Android Framework و UI باقی بمانند و روی JVM به‌سرعت تست شوند.

## ۱. لایه‌های معماری

```
┌──────────────────────────────────────────────────────────────────────┐
│ Presentation Layer (Jetpack Compose + Material 3 + MVVM)             │
│  • SolinaViewModel (StateFlow UI state orchestration)                │
│  • Bilingual i18n (Persian RTL / English LTR)                        │
│  • Themes: System / Light / Dark / Pure-Black AMOLED                 │
│  • Screens: Home, Vault, Codes (TOTP/HOTP), Generator, Detail, Edit, │
│             Data/Backup, External Accounts, Sync/WebDAV, Settings    │
├──────────────────────────────────────────────────────────────────────┤
│ Android Platform & Services Layer                                    │
│  • Autofill: SolinaAutofillService (Inline API 30+ & Dropdown),      │
│              AutofillParser, TrustedBrowsers, AutofillAuthActivity   │
│  • Auth/OAuth: ExternalAccountManager (Google OIDC & GitHub OAuth    │
│                2.0 + PKCE, Keystore-encrypted local state)           │
│  • Sync Transports: FolderRemote (SAF), WebDavRemote (HTTPS-only)    │
│  • Security: BiometricKeystore, KeystoreSecretStore, SecurityPrefs   │
├──────────────────────────────────────────────────────────────────────┤
│ Pure Kotlin Domain & Crypto Core (Zero Android Dependencies)         │
│  • Crypto: Argon2id (BouncyCastle), HKDF-SHA256, AES-256-GCM, BIP-39 │
│  • Vault: VaultCrypto, VaultRepository, SecurityAnalyzer,            │
│           BreachChecker (k-Anonymity), PasswordGenerator             │
│  • Authenticator: Totp (RFC 6238 TOTP + RFC 4226 HOTP), OtpImport    │
│  • Sync Engine: Immutable HMAC-SHA256 records (.slni) +              │
│                 Signed Monotonic Manifest (manifest.slnm)            │
│  • Backup & Importers: .slnb v1 + pre-restore inspect(), 15+ formats │
├──────────────────────────────────────────────────────────────────────┤
│ Persistence Layer (Room SQLite + Atomic Files)                       │
│  • SolinaDatabase (PRAGMA secure_delete = ON, VACUUM on wipe)        │
│  • VaultFiles (Atomic write + fsync for vault.hdr & unlock.state)    │
└──────────────────────────────────────────────────────────────────────┘
```

## ۲. سلسله‌مراتب کلیدهای رمزنگاری (Zero-Knowledge Key Hierarchy)

1. **Master Password** ──(NFKC + Argon2id calibrated on-device)──▶ **Password KEK (256-bit)** ──▶ wraps **VaultKey (256-bit random)**.
2. **24-word BIP-39 Recovery Phrase** ──(256-bit entropy + HKDF-SHA256)──▶ **Recovery KEK (256-bit)** ──▶ wraps **VaultKey**.
3. **Biometric Keystore Key (TEE/StrongBox)** ──(invalidated on enrollment change)──▶ wraps **VaultKey** locally only.
4. **VaultKey** ──▶ wraps per-item random **ItemKey (256-bit)** (`AAD = "solina/itemkey|" + itemId`).
5. **ItemKey** ──▶ encrypts **ItemPayload JSON** via AES-256-GCM (`AAD = itemId + rev + schema`).
6. **Sync MAC Key** = `HKDF-SHA256(VaultKey, info="solina/sync/mac/v1")` ──▶ authenticates every `.slni` record.
7. **Sync Manifest Key** = `HKDF-SHA256(VaultKey, info="solina/sync/manifest/v1")` ──▶ authenticates `manifest.slnm` with monotonic `epoch` against rollback attacks.

## ۳. جداسازی حساب‌های خارجی (Google / GitHub OAuth 2.0 + PKCE)

- ورود با Google (OIDC) یا GitHub (OAuth 2.0) صرفاً لایهٔ اختیاری هویت و اتصال سرویس‌های خارجی است.
- Master Password، VaultKey، Recovery Phrase و محتوای رمزگشایی‌شدهٔ خزانه **هرگز** به Google یا GitHub ارسال نمی‌شوند.
- قطع اتصال یا حذف هر حساب خارجی هیچ تأثیری بر داده‌های خزانهٔ محلی ندارد.
- کل جریان OAuth از RFC 7636 PKCE (`S256`)، `state` تصادفی و Redirect URI اختصاصی (`solina-auth://oauth/callback`) با شبکهٔ صرفاً HTTPS استفاده می‌کند.

## ۴. همگام‌سازی چندپلتفرمه و آفلاین-محور

- **حالت ۱: همگام‌سازی پوشه‌ای (SAF):** سازگار با Google Drive، Nextcloud Files، Syncthing و حافظهٔ خارجی.
- **حالت ۲: سرور اختصاصی WebDAV (HTTPS-only):** سازگار با Nextcloud، ownCloud، Synology و هر سرور WebDAV استاندارد.
- تمام رکوردها پیش از ارسال در سمت کلاینت رمز و امضا می‌شوند؛ سرور هیچ دیدی به رمزها ندارد.
