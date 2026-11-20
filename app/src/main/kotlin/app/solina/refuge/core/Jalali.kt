package app.solina.refuge.core

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

private val JALALI_MONTH_DAYS = intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29)

/** شمارهٔ روز مطلق یک تاریخ شمسی (برای مقایسه و محاسبهٔ کبیسه). */
private fun jalaliDayNumber(jy: Int, jm: Int, jd: Int): Int {
    val jy2 = jy - 979
    var days = 365 * jy2 + (jy2 / 33) * 8 + (jy2 % 33 + 3) / 4
    for (i in 0 until jm - 1) days += JALALI_MONTH_DAYS[i]
    return days + jd - 1
}

/** آیا سال شمسی کبیسه است؟ */
fun isJalaliLeap(jy: Int): Boolean =
    jalaliDayNumber(jy + 1, 1, 1) - jalaliDayNumber(jy, 1, 1) == 366

/** طول ماه شمسی (اسفند در سال کبیسه ۳۰ روز است). */
fun jalaliMonthLength(jy: Int, jm: Int): Int =
    if (jm == 12 && isJalaliLeap(jy)) 30 else JALALI_MONTH_DAYS[jm - 1]

/** تبدیل تاریخ شمسی به میلادی (معکوس gregorianToJalali؛ الگوریتم حسابی، بدون وابستگی). */
fun jalaliToGregorian(jy: Int, jm: Int, jd: Int): Triple<Int, Int, Int> {
    var gDayNo = jalaliDayNumber(jy, jm, jd) + 79
    var gy = 1600 + 400 * (gDayNo / 146097)
    gDayNo %= 146097
    var leap = true
    if (gDayNo >= 36525) {
        gDayNo--
        gy += 100 * (gDayNo / 36524)
        gDayNo %= 36524
        if (gDayNo >= 365) gDayNo++ else leap = false
    }
    gy += 4 * (gDayNo / 1461)
    gDayNo %= 1461
    if (gDayNo >= 366) {
        leap = false
        gDayNo--
        gy += gDayNo / 365
        gDayNo %= 365
    }
    var gd = gDayNo + 1
    val gMonthDays = intArrayOf(0, 31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    var gm = 0
    while (gm < 12 && gd > gMonthDays[gm + 1]) {
        gd -= gMonthDays[gm + 1]
        gm++
    }
    return Triple(gy, gm + 1, gd)
}
