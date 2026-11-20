package app.solina.refuge.core.storage

/**
 * آیکون خودکار هر ورودی (فاز ۴) — کاملاً آفلاین، بدون دانلود و بدون شبکه.
 *
 * فقط یک جدول کوچک داخلی از سرویس‌های شناخته‌شده (نام دامنه/عنوان ← حرف و رنگ ثابت)
 * استفاده می‌شود؛ هیچ آیکون فایلی، بستهٔ دانلودی یا فراخوانی شبکه‌ای در کار نیست.
 * اگر سرویس شناخته نشد، آواتار قبلی (حرف اول عنوان با رنگ پایدار از هش عنوان) برمی‌گردد.
 * پشتیبانی از فایل آیکون سفارشی در این فاز اضافه نشده است (نیاز به اعتبارسنجی مسیر و
 * ذخیره‌سازی امن فایل داشت و خارج از محدودهٔ فاز ۴ بود).
 */
data class ServiceStyle(val letter: String, val colorIndex: Int)

object ServiceIcon {
    /** تعداد رنگ‌های پالت سرویس‌ها (0..7)؛ پالت واقعی در ItemAvatar تعریف شده است. */
    const val PALETTE_SIZE = 8

    private val SERVICES = mapOf(
        "google" to "Google",
        "gmail" to "Gmail",
        "youtube" to "YouTube",
        "github" to "GitHub",
        "gitlab" to "GitLab",
        "microsoft" to "Microsoft",
        "outlook" to "Outlook",
        "hotmail" to "Outlook",
        "apple" to "Apple",
        "icloud" to "iCloud",
        "amazon" to "Amazon",
        "netflix" to "Netflix",
        "spotify" to "Spotify",
        "telegram" to "Telegram",
        "whatsapp" to "WhatsApp",
        "instagram" to "Instagram",
        "facebook" to "Facebook",
        "twitter" to "X",
        "linkedin" to "LinkedIn",
        "reddit" to "Reddit",
        "pinterest" to "Pinterest",
        "twitch" to "Twitch",
        "discord" to "Discord",
        "slack" to "Slack",
        "zoom" to "Zoom",
        "steam" to "Steam",
        "epicgames" to "Epic Games",
        "battlenet" to "Battle.net",
        "blizzard" to "Battle.net",
        "paypal" to "PayPal",
        "binance" to "Binance",
        "coinbase" to "Coinbase",
        "adobe" to "Adobe",
        "canva" to "Canva",
        "figma" to "Figma",
        "notion" to "Notion",
        "trello" to "Trello",
        "asana" to "Asana",
        "stackoverflow" to "Stack Overflow",
        "medium" to "Medium",
        "wikipedia" to "Wikipedia",
        "dropbox" to "Dropbox",
        "yahoo" to "Yahoo",
        "samsung" to "Samsung",
        "huawei" to "Huawei",
        "xiaomi" to "Xiaomi",
        "openai" to "OpenAI",
        "chatgpt" to "ChatGPT",
        "anthropic" to "Anthropic",
        "claude" to "Claude",
    )

    /** پسوندهای سطح دوم که دامنهٔ اصلی یک پله عقب‌تر است (مثل google.co.uk). */
    private val SECOND_LEVEL_SUFFIXES = setOf("co", "com", "org", "net", "gov", "ac")

    /** دامنهٔ اصلی از نشانی یا عنوان؛ مثلاً mail.google.com ← google. */
    fun domainKey(website: String): String? {
        val w = website.trim().lowercase()
        if (w.isEmpty()) return null
        val noScheme = w.substringAfter("://", w)
        val host = noScheme.substringBefore('/').substringBefore('?').substringBefore('#')
            .substringAfter('@').substringBefore(':').removePrefix("www.")
        if (host.isEmpty() || '.' !in host) return null
        val labels = host.split('.').filter { it.isNotEmpty() }
        if (labels.size < 2) return null
        val secondLast = labels[labels.size - 2]
        return if (secondLast in SECOND_LEVEL_SUFFIXES && labels.size >= 3) labels[labels.size - 3] else secondLast
    }

    private fun styleFor(key: String): ServiceStyle? {
        val name = SERVICES[key] ?: return null
        return ServiceStyle(
            letter = name.take(1).uppercase(),
            colorIndex = (key.hashCode() and 0x7fffffff) % PALETTE_SIZE,
        )
    }

    /**
     * سبک آیکون سرویس؛ اول از دامنهٔ وب‌سایت، بعد از عنوان.
     * null یعنی سرویس شناخته نشد (آواتار پیش‌فرض برگردد).
     */
    fun resolve(website: String, title: String): ServiceStyle? {
        domainKey(website)?.let { styleFor(it)?.let { s -> return s } }
        val normalized = title.trim().lowercase().filter { it.isLetterOrDigit() }
        if (normalized.isEmpty()) return null
        for ((key, _) in SERVICES) {
            if (key.length >= 3 && key in normalized) return styleFor(key)
        }
        return null
    }
}
