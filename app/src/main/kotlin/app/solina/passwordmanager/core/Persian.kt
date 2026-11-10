package app.solina.passwordmanager.core

/** تبدیل ارقام لاتین به فارسی، فقط برای نمایش در رابط (نه برای رمزها و کدها). */
fun String.toPersianDigits(): String =
    map { if (it in '0'..'9') '۰' + (it - '0') else it }.joinToString("")

fun Int.fa(): String = toString().toPersianDigits()
