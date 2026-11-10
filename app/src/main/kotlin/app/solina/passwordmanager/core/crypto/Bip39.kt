package app.solina.passwordmanager.core.crypto

import java.security.MessageDigest

class InvalidRecoveryPhraseException(message: String) : IllegalArgumentException(message)

/** BIP-39 برای ۲۵۶ بیت آنتروپی (۲۴ کلمه). */
object Bip39 {
    const val ENTROPY_BYTES = 32
    const val WORD_COUNT = 24

    private val words = Bip39Words.ENGLISH
    private val index: Map<String, Int> = words.withIndex().associate { it.value to it.index }

    fun entropyToWords(entropy: ByteArray): List<String> {
        require(entropy.size == ENTROPY_BYTES) { "entropy must be 32 bytes" }
        val checksum = MessageDigest.getInstance("SHA-256").digest(entropy)[0].toInt() and 0xff
        val bits = BooleanArray(264)
        for (i in 0 until 256) bits[i] = (entropy[i / 8].toInt() shr (7 - i % 8)) and 1 == 1
        for (i in 0 until 8) bits[256 + i] = (checksum shr (7 - i)) and 1 == 1
        return List(WORD_COUNT) { w ->
            var n = 0
            for (b in 0 until 11) n = (n shl 1) or (if (bits[w * 11 + b]) 1 else 0)
            words[n]
        }
    }

    /** عبارت را نرمال می‌کند (فاصله‌ها و حروف بزرگ)، اعتبارسنجی می‌کند و آنتروپی را برمی‌گرداند. */
    fun wordsToEntropy(input: String): ByteArray {
        val list = input.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (list.size != WORD_COUNT) throw InvalidRecoveryPhraseException("expected $WORD_COUNT words")
        val bits = BooleanArray(264)
        list.forEachIndexed { w, word ->
            val n = index[word] ?: throw InvalidRecoveryPhraseException("unknown word")
            for (b in 0 until 11) bits[w * 11 + b] = (n shr (10 - b)) and 1 == 1
        }
        val entropy = ByteArray(ENTROPY_BYTES)
        for (i in 0 until 256) if (bits[i]) entropy[i / 8] = (entropy[i / 8].toInt() or (1 shl (7 - i % 8))).toByte()
        val expected = MessageDigest.getInstance("SHA-256").digest(entropy)[0].toInt() and 0xff
        var actual = 0
        for (i in 0 until 8) actual = (actual shl 1) or (if (bits[256 + i]) 1 else 0)
        if (expected != actual) throw InvalidRecoveryPhraseException("checksum mismatch")
        return entropy
    }
}
