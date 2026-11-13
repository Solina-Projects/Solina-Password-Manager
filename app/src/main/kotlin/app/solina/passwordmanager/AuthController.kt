package app.solina.passwordmanager

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import app.solina.passwordmanager.core.crypto.AuthenticationFailedException
import app.solina.passwordmanager.core.crypto.CorruptVaultException
import app.solina.passwordmanager.core.crypto.CreatedVault
import app.solina.passwordmanager.core.crypto.InvalidRecoveryPhraseException
import app.solina.passwordmanager.core.crypto.VaultCrypto
import app.solina.passwordmanager.core.crypto.wipe
import app.solina.passwordmanager.core.security.BiometricKeystore
import app.solina.passwordmanager.core.security.KdfCalibrator
import app.solina.passwordmanager.core.security.UnlockBackoff
import app.solina.passwordmanager.core.security.UnlockState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface UnlockResult {
    data object Success : UnlockResult
    data object WrongPassword : UnlockResult
    data object InvalidPhrase : UnlockResult
    data object Corrupt : UnlockResult
    data class Wait(val millis: Long) : UnlockResult
}

/** منطق ساخت، باز کردن و بازیابی خزانه؛ رابط فقط این کلاس را صدا می‌زند. */
class AuthController(private val container: AppContainer) {
    private val files get() = container.files
    private val session get() = container.session

    fun hasVault() = files.hasVault()
    fun hasBiometric() = files.hasBiometric() && BiometricKeystore.hasKey()

    fun remainingWaitMillis(now: Long = System.currentTimeMillis()): Long {
        val state = files.readUnlockState()
        val delay = UnlockBackoff.delayMillis(state.failedAttempts)
        return (state.lastFailureAt + delay - now).coerceAtLeast(0)
    }

    private fun recordFailure() {
        val s = files.readUnlockState()
        files.writeUnlockState(UnlockState(s.failedAttempts + 1, System.currentTimeMillis()))
    }

    private fun recordSuccess() = files.writeUnlockState(UnlockState(0, 0))

    /** ساخت خزانه در حافظه. روی دیسک نوشته نمی‌شود تا کاربر عبارت بازیابی را تأیید کند ([commit]). */
    suspend fun create(password: CharArray): CreatedVault = withContext(Dispatchers.Default) {
        VaultCrypto.create(password, KdfCalibrator.pickOnDevice())
    }

    /** نوشتن هدر روی دیسک و باز کردن نشست. کلید ورودی تحویل نشست می‌شود و پاک می‌شود. */
    fun commit(created: CreatedVault) {
        files.writeHeader(created.header)
        recordSuccess()
        session.unlock(created.vaultKey)
    }

    suspend fun unlockWithPassword(password: CharArray): UnlockResult = withContext(Dispatchers.Default) {
        val wait = remainingWaitMillis()
        if (wait > 0) return@withContext UnlockResult.Wait(wait)
        val header = try {
            files.readHeader()
        } catch (e: CorruptVaultException) {
            return@withContext UnlockResult.Corrupt
        }
        try {
            session.unlock(VaultCrypto.unlockWithPassword(header, password))
            recordSuccess()
            UnlockResult.Success
        } catch (e: AuthenticationFailedException) {
            recordFailure()
            UnlockResult.WrongPassword
        }
    }

    suspend fun unlockWithRecovery(phrase: String): UnlockResult = withContext(Dispatchers.Default) {
        val wait = remainingWaitMillis()
        if (wait > 0) return@withContext UnlockResult.Wait(wait)
        val header = try {
            files.readHeader()
        } catch (e: CorruptVaultException) {
            return@withContext UnlockResult.Corrupt
        }
        try {
            session.unlock(VaultCrypto.unlockWithRecovery(header, phrase), requireReset = true)
            disableBiometric()
            recordSuccess()
            UnlockResult.Success
        } catch (e: InvalidRecoveryPhraseException) {
            UnlockResult.InvalidPhrase
        } catch (e: AuthenticationFailedException) {
            recordFailure()
            UnlockResult.WrongPassword
        }
    }

    /** تعیین رمز اصلی جدید (مثلاً بعد از بازیابی). فقط Vault Key دوباره پیچیده می‌شود. */
    suspend fun setNewPassword(newPassword: CharArray): Boolean = withContext(Dispatchers.Default) {
        val vk = session.keyCopy() ?: return@withContext false
        try {
            val updated = VaultCrypto.changePassword(files.readHeader(), vk, newPassword, KdfCalibrator.pickOnDevice())
            files.writeHeader(updated)
            true
        } finally {
            vk.wipe()
        }
    }

    // ───── بیومتریک ─────

    fun biometricAvailable(activity: FragmentActivity): Boolean =
        BiometricManager.from(activity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS

    fun enableBiometric(activity: FragmentActivity, onDone: (Boolean) -> Unit) {
        val vk = session.keyCopy() ?: return onDone(false)
        val cipher = try {
            BiometricKeystore.newEnrollCipher()
        } catch (e: Exception) {
            vk.wipe()
            return onDone(false)
        }
        prompt(activity, "فعال‌سازی اثر انگشت", cipher, onFail = { vk.wipe(); onDone(false) }) { c ->
            try {
                files.writeBiometric(BiometricKeystore.finishEnroll(c, vk))
                onDone(true)
            } catch (e: Exception) {
                BiometricKeystore.deleteKey()
                onDone(false)
            } finally {
                vk.wipe()
            }
        }
    }

    fun disableBiometric() {
        files.clearBiometric()
        BiometricKeystore.deleteKey()
    }

    /** @param onResult true اگر باز شد؛ false در لغو یا خطا (رمز اصلی همیشه در دسترس است). */
    fun unlockWithBiometric(activity: FragmentActivity, onResult: (Boolean) -> Unit) {
        val blob = files.readBiometric() ?: return onResult(false)
        val cipher = try {
            BiometricKeystore.newUnlockCipher(blob)
        } catch (e: Exception) {
            // اثر انگشت جدید اضافه شده یا کلید از بین رفته: میان‌بر غیرفعال می‌شود، رمز اصلی می‌ماند.
            disableBiometric()
            return onResult(false)
        }
        prompt(activity, "باز کردن Solina", cipher, onFail = { onResult(false) }) { c ->
            try {
                session.unlock(BiometricKeystore.finishUnlock(c, blob))
                onResult(true)
            } catch (e: Exception) {
                onResult(false)
            }
        }
    }

    private fun prompt(
        activity: FragmentActivity,
        title: String,
        cipher: javax.crypto.Cipher,
        onFail: () -> Unit,
        onSuccess: (javax.crypto.Cipher) -> Unit,
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val c = result.cryptoObject?.cipher
                if (c == null) onFail() else onSuccess(c)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFail()
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setNegativeButtonText("استفاده از رمز اصلی")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        BiometricPrompt(activity, executor, callback).authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }
}
