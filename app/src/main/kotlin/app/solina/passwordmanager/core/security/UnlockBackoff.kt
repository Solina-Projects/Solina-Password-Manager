package app.solina.passwordmanager.core.security

/**
 * تأخیر فزاینده بعد از تلاش‌های ناموفق. این فقط جلوی حدس‌زدن از طریق خود اپ را می‌گیرد.
 * کسی که فایل خزانه را کپی کند آن را دور می‌زند؛ در آن حالت فقط Argon2id و قدرت رمز محافظ است.
 */
object UnlockBackoff {
    private const val FREE_ATTEMPTS = 4
    private const val BASE_MILLIS = 2_000L
    private const val MAX_MILLIS = 15 * 60_000L

    fun delayMillis(failedAttempts: Int): Long {
        if (failedAttempts < FREE_ATTEMPTS) return 0
        val exponent = (failedAttempts - FREE_ATTEMPTS).coerceAtMost(20)
        return (BASE_MILLIS shl exponent).coerceAtMost(MAX_MILLIS)
    }
}
