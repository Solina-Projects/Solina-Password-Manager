# تحلیل پروژه‌های مرجع و بررسی مجوزها (Phase 1)

پیش از طراحی و توسعهٔ قابلیت‌های جامع **Solina Password Manager**، ساختار فنی، الگوریتم‌ها، معماری و مجوزهای چهار پروژهٔ مرجع به‌صورت مستقیم بررسی شدند.

## ۱. جدول تطبیق مجوزها (License Compliance)

مجوز مخزن **Solina Password Manager**: **GNU GPL v3.0** (`LICENSE`).

| پروژهٔ مرجع | مخزن | مجوز رسمی (SPDX) | سازگاری با GPL-3.0 در Solina | رویکرد مهندسی در Solina |
|---|---|---|---|---|
| **Bitwarden Android** | `bitwarden/android` | `GPL-3.0` | سازگار با حفظ copyleft | الگوبرداری از معماری Autofill، Inline Suggestions، تفکیک Vault/Authenticator و سیاست‌های Zero-Knowledge |
| **Proton Pass Android** | `ProtonPass/android-pass` | `GPL-3.0` | سازگار با حفظ copyleft | الگوبرداری از مدل چندنوعی خزانه (Login/Card/Identity/Note)، تجربهٔ کاربری مدرن Compose و همگام‌سازی آفلاین-محور |
| **Ente** | `ente-io/ente` | `AGPL-3.0` | **ناسازگار برای کپی مستقیم** در پروژهٔ صرفاً GPL-3.0 | **پیاده‌سازی کاملاً مستقل (Clean-Room)** با الهام از طراحی Zero-Knowledge، کلید خزانهٔ مستقل از رمز اصلی و عبارت بازیابی ۲۴ کلمه‌ای |
| **Aegis Authenticator** | `beemdevelopment/Aegis` | `GPL-3.0` | سازگار با حفظ copyleft | الگوبرداری از پشتیبانی کامل TOTP/HOTP، شمارش معکوس، گروه‌بندی، ایمپورت/اکسپورت رمزنگاری‌شده و پشتیبانی از فرمت‌های استاندارد |

> **اصل کلیدی حقوقی و فنی:** هیچ فایل یا کد کپی‌شدهٔ مستقیمی از پروژه‌های مرجع وارد Solina نشده است؛ تمام ماژول‌ها به‌صورت مستقل و بومی (Clean-Room Kotlin) متناسب با معماری یکپارچهٔ Solina نوشته و با بردارهای رسمی RFC (RFC 9106، RFC 5869، RFC 6238، RFC 4226، RFC 7636) تست شده‌اند.

## ۲. قابلیت‌ها و الگوهای استخراج‌شده از هر مرجع

### الف) Bitwarden (`bitwarden/android`)
- **Android Autofill Framework & Inline Suggestions:** پشتیبانی هم‌زمان از نمایش Dropdown کلاسیک (API 26+) و پیشنهادهای درون‌صفحه‌کلید (`InlinePresentation` روی Android 11+ / API 30+).
- **Trusted Browser Verification:** تأیید نام پکیج مرورگر پیش از اعتماد به `webDomain` برای جلوگیری از حملات Spoofing.
- **Vault Health & k-Anonymity Breach Check:** تشخیص رمزهای ضعیف، تکراری، HTTP ناامن و بررسی اختیاری افشای رمز عبور با پروتکل k-Anonymity (ارسال فقط ۵ کاراکتر اول هش SHA-1).
- **SaveInfo Flow:** پیشنهاد ذخیرهٔ خودکار ورودی‌های جدید از طریق `AutofillService.onSaveRequest`.

### ب) Proton Pass (`ProtonPass/android-pass`)
- **مدل غنی اقلام خزانه:** پوشش کامل ورودها (Login)، کارت‌های بانکی (Card)، هویت و نشانی (Identity) و یادداشت‌های محرمانه (Note) به همراه دسته‌بندی، آیکون اختصاصی، برچسب‌ها، علاقه‌مندی‌ها و تاریخ ایجاد/ویرایش.
- **تجربهٔ کاربری مدرن Material 3:** رابط سریع با Compose، تم‌های روشن، تاریک و AMOLED، و دوزبانه (فارسی RTL + انگلیسی LTR).
- **ایمپورت سازگار:** پشتیبانی از خروجی‌های ZIP/JSON/CSV سایر مدیرهای رمز.

### ج) Ente (`ente/ente`)
- **معماری Zero-Knowledge واقعی:** کلید اصلی خزانه (`VaultKey`) به‌صورت تصادفی تولید و با دو KEK مستقل (رمز اصلی با Argon2id و عبارت بازیابی ۲۴ کلمه‌ای BIP-39) بسته‌بندی می‌شود؛ تغییر رمز اصلی بدون تغییر هویت خزانه و بدون ارسال هیچ رازی به سرور انجام می‌گیرد.
- **کلید مجزا برای هر آیتم (Envelope Encryption):** هر ورودی با کلید تصادفی ۲۵۶بیتی خودش و AES-256-GCM با AAD مقید به `(itemId, rev, schema)` رمز می‌شود.
- **چرخش کلید (Key Rotation):** قابلیت تولید کلید خزانهٔ جدید و بازبسته‌بندی کلید آیتم‌ها (`rotateVaultKey` / `rewrapItemKey`).

### د) Aegis (`beemdevelopment/Aegis`)
- **پشتیبانی کامل از TOTP (RFC 6238) و HOTP (RFC 4226):** الگوریتم‌های SHA-1/SHA-256/SHA-512، کدهای ۶ و ۸ رقمی، شمارش معکوس زنده و شمارندهٔ افزایشی HOTP.
- **مدیریت پیشرفتهٔ Authenticator:** جست‌وجو، دسته‌بندی، کپی سریع، اسکن QR (دوربین و گالری)، ورود دستی، پشتیبانی از `otpauth://totp` و `otpauth://hotp` و Google Authenticator migration.
- **بکاپ و بازیابی مستقل Authenticator:** خروجی رمزنگاری‌شدهٔ اختصاصی فقط برای حساب‌های احراز هویت به همراه اعتبارسنجی پیش از بازیابی.
