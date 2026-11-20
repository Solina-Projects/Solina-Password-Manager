package app.solina.passwordmanager.core.security

import android.content.Context
import android.os.SystemClock
import app.solina.passwordmanager.core.crypto.Aead
import app.solina.passwordmanager.core.crypto.Argon2id
import app.solina.passwordmanager.core.crypto.AuthenticationFailedException
import app.solina.passwordmanager.core.crypto.Hkdf
import app.solina.passwordmanager.core.crypto.KdfParams
import app.solina.passwordmanager.core.crypto.randomBytes
import app.solina.passwordmanager.core.crypto.wipe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Base64

/**
 * باز کردن خزانه با پین (۴ تا ۶ رقم) — طراحی امنیتی:
 *
 * ۱) رمز اصلی تنها KDF خزانه است. پین هرگز جایگزین آن نمی‌شود و هرگز مستقیم دادهٔ خزانه
 *    را رمزگشایی نمی‌کند: کلید مشتق‌شده از پین فقط Vault Key را باز می‌کند (یک لایه پیچش)،
 *    و خود Vault Key است که ورودی‌ها را رمزگشایی می‌کند.
 *
 * ۲) راه‌اندازی پین فقط وقتی خزانه باز است انجام می‌شود (Vault Key در حافظه است):
 *    salt تصادفی ۱۶ بایتی + KEK = Argon2id(PIN, salt, KDF پیش‌فرض) ← HKDF با info جدا
 *    («solina/wrap/pin/v1») → wrapped = AES-256-GCM(KEK, VaultKey, AAD «solina/pin/v1»).
 *    رشتهٔ «saltB64.wrappedB64» با کلید AES-256-GCM داخل Android Keystore رمز می‌شود
 *    (همان سازوکار KeystoreSecretStore برای اعتبارنامهٔ WebDAV) و در SharedPreferences
 *    خصوصی می‌نشیند. پس کپیِ صرفِ فایل‌ها برای حملهٔ آفلاین به پین کافی نیست؛ مهاجم
 *    به Keystore سخت‌افزاریِ همان دستگاه هم نیاز دارد.
 *
 * ۳) تلاش ناموفق: شمارنده + تأخیر فزاینده با همان سیاست UnlockBackoff رمز اصلی.
 *    پس از ۵ تلاش ناموفق، blob پین و شمارنده پاک می‌شوند (پین غیرفعال) و فقط رمز اصلی
 *    (یا ۲۴ کلمهٔ بازیابی) می‌ماند.
 *
 * ۴) محدودیت صادقانه: روی دستگاه روت‌شده/به‌خطرافتاده، مهاجمی که با هویت اپ اجرا می‌شود
 *    می‌تواند از Keystore رمزگشایی کند و بعد پین ۴ تا ۶ رقمی را آفلاین حدس بزند؛ پس پین
 *    «میان‌بر راحت» است، نه هم‌ارز رمز اصلی. رمز اصلی و عبارت بازیابی همیشه معتبر می‌مانند.
 *
 * ۵) تغییر رمز اصلی Vault Key را عوض نمی‌کند، پس پین سر جایش می‌ماند. چرخش کامل کلید
 *    خزانه (Re-Key) کلید تازه می‌سازد و blob پین نامعتبر می‌شود؛ پین باید دوباره تنظیم شود.
 *    پاک‌سازی کامل خزانه (wipeLocalVaultSecurely) پین را هم پاک می‌کند.
 */
object PinCrypto {
    const val SALT_SIZE = 16
    const val MIN_PIN_LENGTH = 4
    const val MAX_PIN_LENGTH = 6

    /** پس از این تعداد تلاش ناموفق، پین خودکار غیرفعال و پاک می‌شود. */
    const val MAX_PIN_ATTEMPTS = 5

    /** همان پارامترهای پیش‌فرض Argon2id رمز اصلی؛ پین کوتاه با KDF ضعیف‌تر هرگز پذیرفته نمی‌شود. */
    val PIN_KDF: KdfParams = KdfParams.DEFAULT

    private val PIN_INFO = "solina/wrap/pin/v1".toByteArray(Charsets.US_ASCII)
    private val PIN_AAD = "solina/pin/v1".toByteArray(Charsets.US_ASCII)

    fun isValidPin(pin: CharArray): Boolean =
        pin.size in MIN_PIN_LENGTH..MAX_PIN_LENGTH && pin.all { it in '0'..'9' }

    fun isValidPin(pin: String): Boolean =
        pin.length in MIN_PIN_LENGTH..MAX_PIN_LENGTH && pin.all { it in '0'..'9' }

    /** بایت‌های ASCII پین؛ فقط ارقام پذیرفته می‌شوند (بدون ساخت String واسط). */
    fun pinToBytes(pin: CharArray): ByteArray {
        require(isValidPin(pin)) { "PIN must be 4-6 digits" }
        return ByteArray(pin.size) { pin[it].code.toByte() }
    }

    /** مشتق جداگانه از پین با salt خودش؛ این کلید هرگز مستقیم دادهٔ خزانه را باز نمی‌کند. */
    fun derivePinKek(pinBytes: ByteArray, salt: ByteArray): ByteArray {
        val master = Argon2id.derive(pinBytes, salt, PIN_KDF)
        try {
            return Hkdf.derive(master, null, PIN_INFO)
        } finally {
            master.wipe()
        }
    }

    fun wrapVaultKey(pinKek: ByteArray, vaultKey: ByteArray): ByteArray =
        Aead.seal(pinKek, vaultKey, PIN_AAD)

    /** @throws AuthenticationFailedException اگر پین نادرست باشد یا blob دست‌کاری شده باشد. */
    fun unwrapVaultKey(pinKek: ByteArray, wrapped: ByteArray): ByteArray =
        Aead.open(pinKek, wrapped, PIN_AAD)

    fun shouldWipePin(failedAttempts: Int): Boolean = failedAttempts >= MAX_PIN_ATTEMPTS
}

sealed interface PinResult {
    /** Vault Key باز شد؛ فراخواننده باید آن را به VaultSession بدهد (session.unlock خودش wipe می‌کند). */
    data class Success(val vaultKey: ByteArray) : PinResult

    /** پین نادرست؛ remaining تلاش مانده تا پاک‌شدن خودکار، و waitMillis تأخیر تا تلاش بعدی. */
    data class Wrong(val remaining: Int, val waitMillis: Long) : PinResult

    /** پس از ۵ تلاش ناموفق پین پاک شد؛ فقط رمز اصلی می‌ماند. */
    data object Wiped : PinResult

    /** هنوز در مهلت تأخیر فزاینده هستیم. */
    data class Wait(val millis: Long) : PinResult

    /** اصلاً پینی تنظیم نشده است. */
    data object NoPin : PinResult

    /** خطای غیرمنتظره (مثلاً Keystore یا دادهٔ خراب). */
    data object Error : PinResult
}

/** ذخیره‌سازی و بررسی پین؛ لایهٔ خالص رمزنگاری در [PinCrypto] است تا قابل تست بماند. */
class PinUnlockManager(context: Context) {
    private val store = KeystoreSecretStore(context, "pin_unlock_store")
    private val prefs = context.applicationContext.getSharedPreferences("pin_unlock_prefs", Context.MODE_PRIVATE)

    private val _pinEnabled = MutableStateFlow(hasPin())
    val pinEnabled: StateFlow<Boolean> = _pinEnabled.asStateFlow()

    fun hasPin(): Boolean = store.getString(KEY_BLOB) != null

    /** تنظیم یا تغییر پین؛ فقط وقتی صدا زده می‌شود که Vault Key در دسترس است (خزانه باز). */
    fun setupPin(pin: CharArray, vaultKey: ByteArray): Boolean {
        if (!PinCrypto.isValidPin(pin)) return false
        return runCatching {
            val salt = randomBytes(PinCrypto.SALT_SIZE)
            val pinBytes = PinCrypto.pinToBytes(pin)
            val kek = try {
                PinCrypto.derivePinKek(pinBytes, salt)
            } finally {
                pinBytes.wipe()
            }
            try {
                val wrapped = PinCrypto.wrapVaultKey(kek, vaultKey)
                val packed = Base64.getEncoder().encodeToString(salt) + "." +
                    Base64.getEncoder().encodeToString(wrapped)
                store.putString(KEY_BLOB, packed)
                prefs.edit().putInt(KEY_ATTEMPTS, 0).remove(KEY_LAST_FAIL_ELAPSED).apply()
                _pinEnabled.value = true
                true
            } finally {
                kek.wipe()
            }
        }.getOrDefault(false)
    }

    /** بررسی پین بدون تغییر نشست؛ فقط برای تأیید پین فعلی هنگام تغییر پین (از داخل خزانهٔ باز). */
    fun verifyPin(pin: CharArray): Boolean {
        val packed = store.getString(KEY_BLOB) ?: return false
        val (salt, wrapped) = parseBlob(packed) ?: return false
        val pinBytes = runCatching { PinCrypto.pinToBytes(pin) }.getOrNull() ?: return false
        val kek = try {
            PinCrypto.derivePinKek(pinBytes, salt)
        } finally {
            pinBytes.wipe()
        }
        return try {
            PinCrypto.unwrapVaultKey(kek, wrapped).wipe()
            true
        } catch (e: Exception) {
            false
        } finally {
            kek.wipe()
        }
    }

    fun unlockWithPin(pin: CharArray): PinResult {
        val packed = store.getString(KEY_BLOB) ?: return PinResult.NoPin
        val attempts = prefs.getInt(KEY_ATTEMPTS, 0)
        val delay = UnlockBackoff.delayMillis(attempts)
        if (delay > 0L) {
            val lastFail = prefs.getLong(KEY_LAST_FAIL_ELAPSED, 0L)
            if (lastFail > 0L) {
                val remaining = delay - (SystemClock.elapsedRealtime() - lastFail)
                if (remaining > 0L) return PinResult.Wait(remaining)
            }
        }
        val (salt, wrapped) = parseBlob(packed) ?: return PinResult.Error
        val pinBytes = runCatching { PinCrypto.pinToBytes(pin) }.getOrNull()
            ?: return recordFailure(attempts)
        val kek = try {
            PinCrypto.derivePinKek(pinBytes, salt)
        } finally {
            pinBytes.wipe()
        }
        try {
            val vk = PinCrypto.unwrapVaultKey(kek, wrapped)
            prefs.edit().putInt(KEY_ATTEMPTS, 0).remove(KEY_LAST_FAIL_ELAPSED).apply()
            _pinEnabled.value = true
            return PinResult.Success(vk)
        } catch (e: AuthenticationFailedException) {
            return recordFailure(attempts)
        } catch (e: Exception) {
            return PinResult.Error
        } finally {
            kek.wipe()
        }
    }

    private fun recordFailure(attempts: Int): PinResult {
        val next = attempts + 1
        prefs.edit().putInt(KEY_ATTEMPTS, next)
            .putLong(KEY_LAST_FAIL_ELAPSED, SystemClock.elapsedRealtime()).apply()
        if (PinCrypto.shouldWipePin(next)) {
            disablePin()
            return PinResult.Wiped
        }
        return PinResult.Wrong(
            remaining = PinCrypto.MAX_PIN_ATTEMPTS - next,
            waitMillis = UnlockBackoff.delayMillis(next),
        )
    }

    fun disablePin() {
        store.remove(KEY_BLOB)
        prefs.edit().clear().apply()
        _pinEnabled.value = false
    }

    private fun parseBlob(packed: String): Pair<ByteArray, ByteArray>? = runCatching {
        val parts = packed.split(".")
        if (parts.size != 2) return null
        val salt = Base64.getDecoder().decode(parts[0])
        val wrapped = Base64.getDecoder().decode(parts[1])
        if (salt.size != PinCrypto.SALT_SIZE) return null
        if (wrapped.size != Aead.NONCE_SIZE + Aead.KEY_SIZE + Aead.TAG_BITS / 8) return null
        salt to wrapped
    }.getOrNull()

    companion object {
        private const val KEY_BLOB = "pin_blob"
        private const val KEY_ATTEMPTS = "pin_attempts"
        private const val KEY_LAST_FAIL_ELAPSED = "pin_last_fail_elapsed"
    }
}
