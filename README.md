# Solina Password Manager

<p align="center">
  <img src="assets/icon-512.png" alt="Solina Password Manager icon" width="120" height="120">
</p>

> گاوصندوق دیجیتال متن‌باز برای اندروید: رمزهای عبور، کدهای احراز هویت دومرحله‌ای و اطلاعات محرمانه، فقط در اختیار خودتان.

## ⚠️ هشدار مهم

**نسخهٔ ۱٫۰٫۰ اولین انتشار عمومی است، ولی برنامه هنوز در مرحلهٔ پیش‌آلفاست و بازبینی امنیتی مستقل ندارد.** تا زمانی که نسخهٔ پایدار و بازبینی‌شده منتشر نشود، از Solina برای نگهداری رمزهای واقعی استفاده نکنید.

## نصب

آخرین نسخه را از صفحهٔ [Releases](https://github.com/Solina-Projects/Solina-Password-Manager/releases) دانلود کنید — فایل APK را نصب کنید.

- نیازمند اندروید ۸٫۰ (API 26) یا بالاتر
- بدون نیاز به حساب کاربری؛ همهٔ داده‌ها فقط روی گوشی شما می‌مانند

## چرا Solina؟

- **Zero-Knowledge:** همهٔ داده‌ها روی دستگاه شما و پیش از هر ارسال یا ذخیره رمزگذاری می‌شوند. سرور یا سرویس ابری هیچ‌وقت متن ساده نمی‌بیند.
- **بدون وابستگی اجباری به حساب:** بدون Google و GitHub هم کامل کار می‌کند. ورود با این حساب‌ها فقط یک امکان اختیاری است و جای رمز اصلی را نمی‌گیرد.
- **همه‌چیز در یک اپ:** مدیر رمز، Authenticator (TOTP/HOTP)، کارت و هویت و یادداشت امن.
- **فارسی‌محور:** رابط کاملاً RTL، تقویم جلالی، فونت وزیرمتن، و در عین حال انگلیسی کامل.
- **بدون تبلیغ و ردیاب:** بدون Analytics، بدون SDK تبلیغاتی، و بدون ارتباط شبکه‌ای پنهان.
- **متن‌باز و قابل بررسی:** کد، مستندات امنیتی و تصمیم‌های معماری همه عمومی هستند.

## قابلیت‌ها

| حوزه | شرح |
|---|---|
| خزانه | ورودی‌های Login، کارت بانکی، هویت، یادداشت امن، جست‌وجوی سریع، پوشه و برچسب، تاریخچهٔ تغییرات |
| Authenticator | TOTP و HOTP، اسکن QR، کدهای چرخان با حلقهٔ شمارش نرم |
| Autofill | Android Autofill با تأیید دامنه و اپ برای جلوگیری از فیشینگ، پیشنهاد ذخیرهٔ رمز جدید |
| امنیت | Argon2id، AES-256-GCM، Android Keystore، بیومتریک، قفل خودکار، محافظت از اسکرین‌شات و کلیپ‌بورد |
| بکاپ و ایمپورت | فایل رمزگذاری‌شده، عبارت بازیابی ۲۴ کلمه‌ای، ایمپورت از Aegis و 2FAS و Bitwarden و Proton Pass و andOTP و Raivo و CSV |
| همگام‌سازی | اختیاری و رمزگذاری‌شده، با انتخاب محل ذخیره (Google Drive، WebDAV، پوشهٔ محلی) |
| مرکز امنیت | تشخیص رمز ضعیف، تکراری و قدیمی، و بررسی اختیاری افشا با روش k-anonymity |
| رابط کاربری | Jetpack Compose و Material 3، انیمیشن‌های نرم (فیلتر لغزنده، کد چرخان، نوار قدرت پلکانی) |

## مستندات

| فایل | محتوا |
|---|---|
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | معماری، سلسله‌مراتب کلید، فرمت داده و بکاپ، مدل تهدید، استراتژی تست |
| [`docs/DESIGN.md`](docs/DESIGN.md) | زبان بصری، توکن‌های رنگ و تایپوگرافی، طراحی صفحه‌ها، RTL، دسترس‌پذیری |
| [`docs/MASTER_PROMPT.md`](docs/MASTER_PROMPT.md) | نقشهٔ راه مرحله‌ای و دستورالعمل توسعه |
| [`SECURITY.md`](SECURITY.md) | سیاست گزارش آسیب‌پذیری |

## نقشهٔ راه

`M0` تحقیق و مستندات ← `M1` زیرساخت ← `M2` هستهٔ امنیتی ← `M3` خزانه ← `M4` Autofill ← `M5` Authenticator ← `M6` بکاپ و ایمپورت ← `M7` سخت‌سازی و بتا ← `M8` همگام‌سازی

مرحله‌های M0 تا M8 انجام شده‌اند و نسخهٔ ۱٫۰٫۰ منتشر شده است. جزئیات و معیار پذیرش هر مرحله در `docs/MASTER_PROMPT.md` است.

بعد از ۱٫۰: ورود با GitHub و سرور شخصی، پشتیبانی Passkey و اسناد رمزگذاری‌شده.

## ساخت از روی کد

```sh
gradle :app:assembleDebug
```

نیازمند Gradle 8.10.2 و Java 17. خروجی: `app/build/outputs/apk/debug/app-debug.apk`

## مشارکت

در این مرحله بیشترین کمک شما بازبینی طراحی است: مدل تهدید، سلسله‌مراتب کلید و تصمیم‌های رمزنگاری. لطفاً پیش از ارسال Pull Request یک Issue باز کنید. هر تغییر در بخش رمزنگاری باید با تست‌وکتور همراه باشد.

## مجوز

مجوز نهایی هنوز انتخاب نشده است (کاندیدها: GPL-3.0-or-later یا AGPL-3.0؛ بسته به اینکه کد پروژه‌های مرجع چگونه استفاده شود). تا آن زمان فایل `LICENSE` اضافه نشده و حقوق همهٔ شرکت‌کنندگان محفوظ است.

## گزارش آسیب‌پذیری

لطفاً مشکلات امنیتی را در Issue عمومی گزارش نکنید. روش گزارش در [`SECURITY.md`](SECURITY.md) آمده است.

---

## English summary

**Solina** is an open-source, native Android password manager with a built-in TOTP/HOTP authenticator, built on a zero-knowledge, client-side-encrypted design (Argon2id + AES-256-GCM, Android Keystore, 24-word recovery phrase). It works fully offline and without any account; Google/GitHub sign-in is optional and never replaces the master password. UI: Jetpack Compose + Material 3, Persian (RTL) and English.

**Status:** version 1.0.0 is the first public release, but the app is still pre-alpha quality with no independent security audit yet. Do not store real secrets in any build yet. See `docs/` for architecture, design and roadmap.
