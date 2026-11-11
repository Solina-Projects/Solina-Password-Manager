package app.solina.passwordmanager.core

import java.time.Instant
import java.time.ZoneId

/** تبدیل تاریخ میلادی به شمسی (الگوریتم حسابی؛ بدون وابستگی به کتابخانهٔ دستگاه). */
fun gregorianToJalali(gy: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
    val cum = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
    val gy2 = if (gm > 2) gy + 1 else gy
    var days = 355666 + 365 * gy + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400 + gd + cum[gm - 1]
    var jy = -1595 + 33 * (days / 12053)
    days %= 12053
    jy += 4 * (days / 1461)
    days %= 1461
    if (days > 365) {
        jy += (days - 1) / 365
        days = (days - 1) % 365
    }
    val jm = if (days < 186) 1 + days / 31 else 7 + (days - 186) / 30
    val jd = 1 + if (days < 186) days % 31 else (days - 186) % 30
    return Triple(jy, jm, jd)
}

/** تاریخ شمسی با ارقام فارسی، مثلاً ۱۴۰۵/۰۸/۲۰. زمان صفر یعنی نامشخص. */
fun formatJalali(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    if (millis <= 0) return "—"
    val d = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    val (y, m, day) = gregorianToJalali(d.year, d.monthValue, d.dayOfMonth)
    return "%04d/%02d/%02d".format(y, m, day).toPersianDigits()
}
