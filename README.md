# Solina Password Manager

> گاوصندوق دیجیتال متن‌باز برای اندروید: رمزهای عبور، کدهای احراز هویت دومرحله‌ای و اطلاعات محرمانه، فقط در اختیار خودتان.

**وضعیت: Pre-alpha، مرحلهٔ طراحی.** هنوز نسخهٔ قابل نصب منتشر نشده است. تا زمانی که نسخهٔ پایدار و بازبینی امنیتی منتشر نشود، از Solina برای نگهداری رمزهای واقعی استفاده نکنید.

---

## چرا Solina؟

- **Zero-Knowledge:** همهٔ داده‌ها روی دستگاه شما و پیش از هر ارسال یا ذخیره رمزگذاری می‌شوند. سرور یا سرویس ابری هیچ‌وقت متن ساده نمی‌بیند.
- **بدون وابستگی اجباری به حساب:** بدون Google و GitHub هم کامل کار می‌کند. ورود با این حساب‌ها فقط یک امکان اختیاری است و جای رمز اصلی را نمی‌گیرد.
- **همه‌چیز در یک اپ:** مدیر رمز، Authenticator (TOTP/HOTP)، کارت و هویت و یادداشت امن.
- **فارسی‌محور:** رابط کاملاً RTL، تقویم جلالی، فونت وزیرمتن، و در عین حال انگلیسی کامل.
- **بدون تبلیغ و ردیاب:** بدون Analytics، بدون SDK تبلیغاتی، و بدون ارتباط شبکه‌ای پنهان.
- **متن‌باز و قابل بررسی:** کد، مستندات امنیتی و تصمیم‌های معماری همه عمومی هستند.

## قابلیت‌های هدف (نسخهٔ ۱٫۰)

| حوزه | شرح |
|---|---|
| خزانه | ورودی‌های Login، کارت بانکی، هویت، یادداشت امن، جست‌وجوی سریع، پوشه و برچسب، تاریخچهٔ تغییرات |
| Autofill | Android Autofill با تأیید دامنه و اپ برای جلوگیری از فیشینگ، پیشنهاد ذخیرهٔ رمز جدید |
| Authenticator | TOTP و HOTP، اسکن QR، ورود از Aegis و Google Authenticator |
| امنیت | Argon2id، AES-256-GCM، Android Keystore، بیومتریک، قفل خودکار، محافظت از اسکرین‌شات و کلیپ‌بورد |
| بکاپ | فایل رمزگذاری‌شده، عبارت بازیابی ۲۴ کلمه‌ای، بازیابی روی گوشی جدید بدون هیچ حساب خارجی |
| همگام‌سازی | اختیاری و رمزگذاری‌شده، با انتخاب محل ذخیره (Google Drive، WebDAV، پوشهٔ محلی) |
| مرکز امنیت | تشخیص رمز ضعیف، تکراری و قدیمی، و بررسی اختیاری افشا با روش k-anonymity |

بعد از ۱٫۰: ورود با GitHub و سرور شخصی، پشتیبانی Passkey و اسناد رمزگذاری‌شده.

## مستندات

| فایل | محتوا |
|---|---|
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | معماری، سلسله‌مراتب کلید، فرمت داده و بکاپ، مدل تهدید، استراتژی تست |
| [`docs/DESIGN.md`](docs/DESIGN.md) | زبان بصری، توکن‌های رنگ و تایپوگرافی، طراحی صفحه‌ها، RTL، دسترس‌پذیری |
| [`docs/MASTER_PROMPT.md`](docs/MASTER_PROMPT.md) | نقشهٔ راه مرحله‌ای و دستورالعمل توسعه |
| [`SECURITY.md`](SECURITY.md) | سیاست گزارش آسیب‌پذیری |

## نقشهٔ راه

`M0` تحقیق و مستندات ← `M1` زیرساخت ← `M2` هستهٔ امنیتی ← `M3` خزانه ← `M4` Autofill ← `M5` Authenticator ← `M6` بکاپ و ایمپورت (**آلفا**) ← `M7` سخت‌سازی و بتا ← `M8` همگام‌سازی (**۱٫۰**)

جزئیات و معیار پذیرش هر مرحله در `docs/MASTER_PROMPT.md` است.

## مشارکت

در این مرحله بیشترین کمک شما بازبینی طراحی است: مدل تهدید، سلسله‌مراتب کلید و تصمیم‌های رمزنگاری. لطفاً پیش از ارسال Pull Request یک Issue باز کنید. هر تغییر در بخش رمزنگاری باید با تست‌وکتور همراه باشد.

## مجوز

مجوز نهایی هنوز انتخاب نشده است (کاندیدها: GPL-3.0-or-later یا AGPL-3.0؛ بسته به اینکه کد پروژه‌های مرجع چگونه استفاده شود). تا آن زمان فایل `LICENSE` اضافه نشده و حقوق همهٔ شرکت‌کنندگان محفوظ است.

## گزارش آسیب‌پذیری

لطفاً مشکلات امنیتی را در Issue عمومی گزارش نکنید. روش گزارش در [`SECURITY.md`](SECURITY.md) آمده است.

---

## English summary

**Solina** is a planned open-source, native Android password manager with a built-in TOTP/HOTP authenticator, built on a zero-knowledge, client-side-encrypted design (Argon2id + AES-256-GCM, Android Keystore, 24-word recovery phrase). It works fully offline and without any account; Google/GitHub sign-in is optional and never replaces the master password. UI: Jetpack Compose + Material 3, Persian (RTL) and English.

**Status:** pre-alpha, design phase. Do not store real secrets in any build yet. See `docs/` for architecture, design and roadmap.
