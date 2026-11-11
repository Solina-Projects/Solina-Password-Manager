package app.solina.passwordmanager.core

import android.icu.util.PersianCalendar
import java.util.Date

/** تاریخ شمسی با ارقام فارسی، مثلاً ۱۴۰۵/۰۸/۲۰. زمان صفر یعنی نامشخص. */
fun formatJalali(millis: Long): String {
    if (millis <= 0) return "—"
    val cal = PersianCalendar()
    cal.time = Date(millis)
    val y = cal.get(PersianCalendar.YEAR)
    val m = cal.get(PersianCalendar.MONTH) + 1
    val d = cal.get(PersianCalendar.DAY_OF_MONTH)
    return "%04d/%02d/%02d".format(y, m, d).toPersianDigits()
}
