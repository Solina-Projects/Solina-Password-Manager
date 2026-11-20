package app.solina.refuge.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * ذخیره‌سازی رمزگذاری‌شده با کلید AES-256-GCM داخل Android Keystore (TEE/StrongBox)
 * برای نگهداری امن تنظیمات حساس جانبی (اعتبارنامهٔ WebDAV و فرادادهٔ حساب‌های خارجی).
 * هیچ داده‌ای به‌صورت متن خام در SharedPreferences ذخیره نمی‌شود.
 */
class KeystoreSecretStore(context: Context, prefName: String = "solina_secure_store") {
    private val prefs = context.applicationContext.getSharedPreferences(prefName, Context.MODE_PRIVATE)

    companion object {
        private const val PROVIDER = "AndroidKeyStore"
        private const val ALIAS = "solina_config_aes_gcm_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
        private const val TAG_BITS = 128
    }

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        gen.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    fun putString(key: String, value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ct = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val packed = ByteArray(iv.size + ct.size)
        System.arraycopy(iv, 0, packed, 0, iv.size)
        System.arraycopy(ct, 0, packed, iv.size, ct.size)
        val encoded = Base64.getEncoder().encodeToString(packed)
        prefs.edit().putString(key, encoded).apply()
    }

    fun getString(key: String): String? {
        val encoded = prefs.getString(key, null) ?: return null
        return runCatching {
            val packed = Base64.getDecoder().decode(encoded)
            if (packed.size <= IV_SIZE) return null
            val iv = packed.copyOfRange(0, IV_SIZE)
            val ct = packed.copyOfRange(IV_SIZE, packed.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        }.getOrNull()
    }

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
