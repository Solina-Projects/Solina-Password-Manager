package app.solina.passwordmanager.data

/** داده‌های ساختگی فقط برای پیش‌نمایش رابط. هیچ‌کدام واقعی نیستند و هیچ‌چیز ذخیره نمی‌شود. */
enum class ItemKind { Login, Card, Note }

enum class ItemFlag { TwoFa, Weak, Reused }

data class VaultItem(
    val id: Int,
    val title: String,
    val username: String,
    val kind: ItemKind = ItemKind.Login,
    val flag: ItemFlag? = null,
    val website: String = "",
    val password: String = "demo-password-0000",
    val note: String = "",
    val created: String = "۱۴۰۴/۱۱/۰۳",
    val edited: String = "۱۴۰۵/۰۸/۱۹",
    val totpSecret: String? = null,
)

data class TotpEntry(
    val id: Int,
    val issuer: String,
    val account: String,
    val secret: ByteArray,
)

object DemoData {
    val items = listOf(
        VaultItem(1, "GitHub", "n••••@mail.com", flag = ItemFlag.TwoFa, website = "github.com", password = "kPq7vM#xR42wL!z9Hd", note = "حساب کاری. کدهای بازیابی در یادداشت امن جداگانه ذخیره شده است.", totpSecret = "GITHUB"),
        VaultItem(2, "Google", "n••••@gmail.com", flag = ItemFlag.TwoFa, website = "google.com", password = "Tw8!nQ3#yB6rZk", totpSecret = "GOOGLE"),
        VaultItem(3, "دیجی‌کالا", "0912•••••78", flag = ItemFlag.Weak, website = "digikala.com", password = "123456"),
        VaultItem(4, "اسنپ", "0912•••••78", flag = ItemFlag.Reused, website = "snapp.ir", password = "123456"),
        VaultItem(5, "Dropbox", "n••••@mail.com", flag = ItemFlag.TwoFa, website = "dropbox.com", password = "mV5\$cJ9!pXe2Ua", totpSecret = "DROPBOX"),
        VaultItem(6, "کارت شخصی", "•••• 4417", kind = ItemKind.Card),
        VaultItem(7, "یادداشت امن", "کدهای بازیابی", kind = ItemKind.Note),
    )

    /** رازهای ساختگی فقط برای نمایش کد زنده؛ به هیچ سرویس واقعی مربوط نیستند. */
    val totp = listOf(
        TotpEntry(1, "GitHub", "n••••@mail.com", "demo-secret-github-0001".toByteArray()),
        TotpEntry(2, "Google", "n••••@gmail.com", "demo-secret-google-0002".toByteArray()),
        TotpEntry(3, "Dropbox", "n••••@mail.com", "demo-secret-dropbox-0003".toByteArray()),
        TotpEntry(4, "Netflix", "f••••@mail.com", "demo-secret-netflix-0004".toByteArray()),
    )

    fun item(id: Int): VaultItem = items.firstOrNull { it.id == id } ?: items.first()
}
