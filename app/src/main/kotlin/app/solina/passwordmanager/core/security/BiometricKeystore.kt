package app.solina.passwordmanager.core.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * «نسخهٔ دستگاه» Vault Key برای باز کردن با اثر انگشت (ARCHITECTURE.md §۲).
 * کلید AES در Android Keystore ساخته می‌شود، نیازمند احراز هویت بیومتریک برای هر استفاده است،
 * و با افزودن اثر انگشت جدید باطل می‌شود. بیومتریک فقط میان‌بر است؛ رمز اصلی همیشه معتبر می‌ماند.
 *
 * قالب blob ذخیره‌شده: iv(12) ‖ ciphertext(+tag).
 */
object BiometricKeystore {
    private const val ALIAS = "solina_vault_biometric_v1"
    private const val PROVIDER = "AndroidKeyStore"
    private const val IV_SIZE = 12

    private fun keyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    fun hasKey(): Boolean = runCatching { keyStore().containsAlias(ALIAS) }.getOrDefault(false)

    fun deleteKey() {
        runCatching { keyStore().deleteEntry(ALIAS) }
    }

    private fun createKey(): SecretKey {
        val builder = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= 30) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(builder.build())
        return generator.generateKey()
    }

    /** رمزگذار برای فعال‌سازی: کلید تازه می‌سازد. باید به BiometricPrompt.CryptoObject داده شود. */
    fun newEnrollCipher(): Cipher {
        deleteKey()
        val key = createKey()
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
    }

    /** پس از تأیید بیومتریک: Vault Key را با cipher آماده‌شده می‌پیچد. */
    fun finishEnroll(cipher: Cipher, vaultKey: ByteArray): ByteArray = cipher.iv + cipher.doFinal(vaultKey)

    /**
     * رمزگشا برای باز کردن.
     * @throws KeyPermanentlyInvalidatedException اگر اثر انگشت جدیدی اضافه شده؛ باید blob پاک شود.
     */
    fun newUnlockCipher(blob: ByteArray): Cipher {
        require(blob.size > IV_SIZE) { "bad blob" }
        val key = keyStore().getKey(ALIAS, null) as? SecretKey ?: throw IllegalStateException("no key")
        val iv = blob.copyOfRange(0, IV_SIZE)
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        }
    }

    fun finishUnlock(cipher: Cipher, blob: ByteArray): ByteArray =
        cipher.doFinal(blob, IV_SIZE, blob.size - IV_SIZE)

    fun isInvalidated(e: Throwable): Boolean = e is KeyPermanentlyInvalidatedException
}
