package app.solina.passwordmanager.core.security

import app.solina.passwordmanager.core.crypto.wipe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * نشست باز بودن خزانه. Vault Key فقط در حافظه نگه داشته می‌شود و با قفل شدن صفر می‌شود.
 * (JVM تضمین نمی‌کند هیچ نسخه‌ای در حافظه نماند؛ این کاهش ریسک است، نه حذف مطلق.)
 */
class VaultSession(private val clock: () -> Long = System::currentTimeMillis) {
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

    /** وقتی اپ به پس‌زمینه رفت. */
    @Synchronized
    fun onBackgrounded() {
        if (_unlocked.value) backgroundedAt = clock()
    }

    /** وقتی اپ برگشت: اگر بیش از مهلت در پس‌زمینه بوده، قفل می‌شود. */
    @Synchronized
    fun onForegrounded(timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS) {
        val since = backgroundedAt ?: return
        backgroundedAt = null
        if (timeoutMillis == 0L || clock() - since >= timeoutMillis) lock()
    }

    /** بررسی مهلت بدون تغییر وضعیت پس‌زمینه؛ برای سرویس Autofill و تایمر قفل خودکار. */
    @Synchronized
    fun enforceTimeout(timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS) {
        val since = backgroundedAt ?: return
        if (_unlocked.value && clock() - since >= timeoutMillis) lock()
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 60_000L
    }
}
