package app.solina.passwordmanager.core.security

import app.solina.passwordmanager.core.crypto.Argon2id
import app.solina.passwordmanager.core.crypto.KdfParams

/**
 * انتخاب پارامتر Argon2id متناسب با سرعت همان گوشی: قوی‌ترین گزینه‌ای که از هدف زمانی بیشتر نشود.
 * هدف: باز شدن خزانه حدود یک ثانیه. زمان‌سنج تزریق می‌شود تا منطق قابل تست باشد.
 */
object KdfCalibrator {
    /** از قوی به ضعیف. آخرین گزینه حداقل مجاز است (طبق ARCHITECTURE.md §۲). */
    val CANDIDATES = listOf(
        KdfParams(memoryKiB = 64 * 1024, iterations = 3, parallelism = 4),
        KdfParams(memoryKiB = 48 * 1024, iterations = 3, parallelism = 4),
        KdfParams(memoryKiB = 32 * 1024, iterations = 3, parallelism = 4),
        KdfParams(memoryKiB = 19 * 1024, iterations = 2, parallelism = 1),
    )

    const val TARGET_MILLIS = 1_200L

    fun pick(measureMillis: (KdfParams) -> Long): KdfParams {
        for (candidate in CANDIDATES.dropLast(1)) {
            if (measureMillis(candidate) <= TARGET_MILLIS) return candidate
        }
        return CANDIDATES.last()
    }

    /** اندازه‌گیری واقعی روی دستگاه (چند ثانیه طول می‌کشد؛ در پس‌زمینه اجرا شود). */
    fun pickOnDevice(): KdfParams = pick { params ->
        val start = System.nanoTime()
        Argon2id.derive("calibration".toByteArray(), ByteArray(16) { it.toByte() }, params)
        (System.nanoTime() - start) / 1_000_000
    }
}
