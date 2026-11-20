package app.solina.refuge.core.storage

/** فیلدهایی که بین دو نسخهٔ یک ورودی تغییر کرده‌اند (فقط نام فیلد، هرگز مقدار). */
enum class ChangedField {
    Title, Username, Password, Website, Note, Category, Tags, Favorite, Totp, Card, Identity,
}

/**
 * تفاوت سطح فیلد بین یک نسخهٔ قدیمی و نسخهٔ فعلی، برای نمایش در تاریخچه.
 * برای رمز عبور فقط نام فیلد «رمز عبور» برگردانده می‌شود؛ مقدار قدیم/جدید هیچ‌جا
 * در فهرست تفاوت نمایش داده نمی‌شود.
 */
object HistoryDiff {
    fun diffFields(old: ItemPayload, new: ItemPayload): List<ChangedField> {
        val out = ArrayList<ChangedField>()
        if (old.title != new.title) out += ChangedField.Title
        if (old.username != new.username) out += ChangedField.Username
        if (old.password != new.password) out += ChangedField.Password
        if (old.website != new.website) out += ChangedField.Website
        if (old.note != new.note) out += ChangedField.Note
        if (old.category != new.category) out += ChangedField.Category
        if (old.tags != new.tags) out += ChangedField.Tags
        if (old.favorite != new.favorite) out += ChangedField.Favorite
        if (old.totpSecret != new.totpSecret || old.otpType != new.otpType ||
            old.totpDigits != new.totpDigits || old.totpAlgorithm != new.totpAlgorithm
        ) out += ChangedField.Totp
        if (old.cardNumber != new.cardNumber || old.bankName != new.bankName ||
            old.cardHolder != new.cardHolder || old.cardExpiry != new.cardExpiry ||
            old.cardCvv != new.cardCvv || old.cardPin != new.cardPin
        ) out += ChangedField.Card
        if (old.fullName != new.fullName || old.email != new.email || old.phone != new.phone ||
            old.nationalId != new.nationalId || old.company != new.company ||
            old.address != new.address || old.city != new.city ||
            old.postalCode != new.postalCode || old.country != new.country
        ) out += ChangedField.Identity
        return out
    }
}
