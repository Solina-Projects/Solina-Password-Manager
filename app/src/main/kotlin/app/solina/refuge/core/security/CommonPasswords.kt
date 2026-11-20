package app.solina.refuge.core.security

/**
 * فهرست آفلاین رمزهای بسیار رایج (فاز ۴) — بررسی کاملاً محلی، هیچ رمزی از دستگاه خارج نمی‌شود.
 *
 * منبع: فهرست‌های عمومی «پرمصرف‌ترین رمزهای عبور» — NordPass Top 200 (سال ۲۰۲۴) و
 * SecLists ‎(10k-most-common، ردیف‌های بالای فهرست). این‌ها رمزهای عمومی و شناخته‌شده‌اند،
 * نه دادهٔ هیچ کاربری؛ فهرست کوچک نگه داشته شده (~۳۷۰ مورد، حدود ۴ کیلوبایت) تا روی
 * حجم APK اثر چشمگیری نگذارد. جایگزین آفلاین بررسی نشت (BreachChecker با k-anonymity
 * همچنان برای بررسی آنلاین اختیاری موجود است).
 *
 * بررسی: تطابق دقیق، و همچنین بدون حساسیت به بزرگی/کوچکی حروف (چون «Password» هم به‌اندازهٔ
 * «password» رایج و قابل‌حدس است).
 */
object CommonPasswords {
    // فهرست با فاصله جدا شده؛ هیچ رمزی در آن فاصله ندارد.
    private const val RAW =
        "123456 123456789 12345678 12345 1234567 1234567890 123123 111111 1234 12345678910 " +
        "password qwerty abc123 123321 654321 11111111 12345678901 1qaz2wsx qwerty123 000000 " +
        "iloveyou football monkey dragon master sunshine shadow ashley bailey passw0rd " +
        "superman michael ninja mustang password1 qwertyuiop 123qwe admin welcome login " +
        "admin123 letmein 123abc 121212 7777777 555555 123654 666666 112233 999999 333333 " +
        "123455 987654321 777777 11111 987654 6543210 00000 123456a a123456 1q2w3e4r " +
        "qazwsx 1q2w3e 1234qwer qwer1234 zxcvbnm zxcvbn qwerty1 qwerty12 aaaa1111 " +
        "pass123 123pass password123 pass1234 123456789a 1qazxsw2 1234abcd abcd1234 " +
        "charlie thomas jordan tigger robert daniel jessica hunter michelle andrew " +
        "joshua buster george asshole pepper 12341234 123123123 11223344 12344321 " +
        "princess maggie soccer chelsea brian william anthony justin diamond " +
        "secret computer michelle1 samantha patrick peanut ginger jackson oliver " +
        "compaq 123456q qwerty123456 freedom 1111111111 aaaaaaaa 00000000 123789 " +
        "samsung 12345a 123321123 qwertyu 1111111 qqqqqq 1234567899 102030 " +
        "google 123456789123 1122334455 a12345 asdfghjkl asdfgh 1qaz2wsx3edc " +
        "a1b2c3 159753 357951 147258 258369 12369874 741852963 9876543210 " +
        "root toor test test123 demo guest user pass passw0rd1 changeme default " +
        "love god sex secret1 god123 money 12345678901234567890 qwertz azerty " +
        "football1 baseball hockey basketball tennis golf soccer1 " +
        "liverpool chelsea1 arsenal manchester barcelona realmadrid juventus " +
        "superman1 batman spiderman starwars pokemon pikachu naruto dragonball " +
        "hello world hello123 123hello summer winter spring autumn " +
        "january february march april may june july august september october november december " +
        "jan123 feb123 mar123 abcdef ghijkl mnopqr 123abc123 000123 123000 " +
        "a1a1a1 zzzzzz xxxxxxxx yyyyyyyy 111222 222222 444444 888888 999999999 " +
        "147852369 963852741 789456123 456789 789456 321654 159357 " +
        "q1w2e3r4 q1w2e3 qwe123 qweasdzxc 1234qwerasdf zxc123 123zxc " +
        "password12 password1234 admin1 administrator root123 " +
        "welcome1 welcome123 login123 user123 guest123 " +
        "dragon123 monkey123 shadow123 sunshine1 master123 " +
        "killer marlboro yamaha harley ferrari porsche mercedes " +
        "snoopy peanut1 mickey donald goofy pluto daisy " +
        "flower rose lily violet tulip jasmine " +
        "cookie chocolate candy sweetheart baby " +
        "angel devil demon ghost vampire zombie " +
        "warrior knight ranger hunter1 soldier " +
        "london paris berlin madrid tokyo osaka " +
        "apple orange banana grape lemon cherry " +
        "red blue green black white pink purple " +
        "fire water earth air wind storm thunder " +
        "gold silver bronze diamond1 ruby pearl " +
        "india china japan korea russia brazil " +
        "john mike david chris alex sam tom bob " +
        "mary anna emma lisa sarah karen nancy " +
        "family friend happy lucky winner champion " +
        "qwertyui qwertyuio 1q2w3e4r5t 1q2w3e4r5t6y " +
        "poiuyt lkjhgfdsa asdfg asdfghjk " +
        "13579 24680 112233445566 123123123123 " +
        "myspace facebook twitter instagram " +
        "google123 youtube netflix spotify " +
        "aaaaaa bbbbbb cccccc dddddd eeeeee ffffff"

    val WORDS: Set<String> = RAW.split(' ').filter { it.isNotEmpty() }.toSet()

    /** آیا رمز دقیقاً (یا بدون حساسیت به حروف) در فهرست رمزهای رایج است؟ */
    fun isCommon(password: String): Boolean {
        if (password.isEmpty()) return false
        return password in WORDS || password.lowercase() in WORDS
    }
}
