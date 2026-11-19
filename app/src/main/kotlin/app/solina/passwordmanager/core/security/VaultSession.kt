package app.solina.passwordmanager.core.security

import app.solina.passwordmanager.core.crypto.wipe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * نشست باز بودن خزانه. Vault Key فقط در حافظه نگه داشته می‌شود و با قفل شدن صفر می‌شود.
 * ساعت پیش‌فرض یکنواخت است (در اندروید SystemClock.elapsedRealtime تزریق می‌شود) تا
 * تغییر ساعت دیواری دستگاه نتواند مهلت قفل خودکار را دور بزند.
 */
class VaultSession(private val clock: () -> Long = { System.nanoTime() / 1_000_000L }) {
    private var vaultKey: ByteArray? = null
    private var backgroundedAt: Long? = null

    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    @Synchronized
    fun unlock(key: ByteArray) {
        vaultKey?.wipe()
        vaultKey = key.copyOf()
        key.wipe()
        backgroundedAt = null
        _unlocked.value = true
    }

    @Synchronized
    fun lock() {
        vaultKey?.wipe()
        vaultKey = null
        backgroundedAt = null
        _unlocked.value = false
    }

    /** کپی کلید برای عملیات؛ فراخواننده باید بعد از استفاده wipe کند. */
    @Synchronized
    fun keyCopy(): ByteArray? = vaultKey?.copyOf()

    /** وقتی اپ به پس‌زمینه رفت. اگر قبلاً ثبت شده باشد، زمان اولیه حفظ می‌شود. */
    @Synchronized
    fun onBackgrounded() {
        if (_unlocked.value && backgroundedAt == null) backgroundedAt = clock()
    }

    /** وقتی اپ برگشت: اگر بیش از مهلت در پس‌زمینه بوده یا ساعت به عقب پریده، قفل می‌شود. */
    @Synchronized
    fun onForegrounded(timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS) {
        val since = backgroundedAt ?: return
        backgroundedAt = null
        val elapsed = clock() - since
        if (timeoutMillis == 0L || elapsed < 0L || elapsed >= timeoutMillis) lock()
    }

    /** بررسی مهلت بدون تغییر وضعیت پس‌زمینه؛ برای سرویس Autofill و تایمر قفل خودکار. */
    @Synchronized
    fun enforceTimeout(timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS) {
        val since = backgroundedAt ?: return
        val elapsed = clock() - since
        if (_unlocked.value && (timeoutMillis == 0L || elapsed < 0L || elapsed >= timeoutMillis)) lock()
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 60_000L
    }
}
