package app.solina.passwordmanager.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** آیکون‌های خطی ۲۴×۲۴ (بدون وابستگی به کتابخانهٔ material-icons-extended). */
object SolinaIcons {
    private fun build(name: String, vararg paths: String): ImageVector {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        for (d in paths) {
            builder.addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }

    val Home by lazy { build("Home", "M3 11l9-8 9 8v9a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z") }
    val Lock by lazy {
        build(
            "Lock",
            "M4 12a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2z",
            "M8 10V7a4 4 0 0 1 8 0v3",
        )
    }
    val Clock by lazy { build("Clock", "M20 13a8 8 0 1 1-16 0a8 8 0 1 1 16 0", "M12 9v4l2 2M9 2h6") }
    val Sliders by lazy {
        build(
            "Sliders",
            "M4 6h10M18 6h2M4 12h4M12 12h8M4 18h12",
            "M18 6a2 2 0 1 1-4 0a2 2 0 1 1 4 0",
            "M12 12a2 2 0 1 1-4 0a2 2 0 1 1 4 0",
            "M20 18a2 2 0 1 1-4 0a2 2 0 1 1 4 0",
        )
    }
    val Search by lazy { build("Search", "M18 11a7 7 0 1 1-14 0a7 7 0 1 1 14 0", "M20 20l-4-4") }
    val Plus by lazy { build("Plus", "M12 5v14M5 12h14") }
    val Copy by lazy {
        build(
            "Copy",
            "M11 9h7a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2h-7a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2z",
            "M5 15V6a2 2 0 0 1 2-2h9",
        )
    }
    val Eye by lazy {
        build(
            "Eye",
            "M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12z",
            "M15 12a3 3 0 1 1-6 0a3 3 0 1 1 6 0",
        )
    }
    val EyeOff by lazy {
        build(
            "EyeOff",
            "M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12z",
            "M15 12a3 3 0 1 1-6 0a3 3 0 1 1 6 0",
            "M3 3l18 18",
        )
    }
    val ChevronRight by lazy { build("ChevronRight", "M9 6l6 6-6 6") }
    val Refresh by lazy { build("Refresh", "M21 12a9 9 0 1 1-3-6.7L21 8M21 3v5h-5") }
    val Warning by lazy { build("Warning", "M12 3l10 18H2z", "M12 10v5M12 18h0") }
    val Edit by lazy { build("Edit", "M4 20h4L19 9l-4-4L4 16z") }
    val Key by lazy { build("Key", "M12 15a4 4 0 1 1-8 0a4 4 0 1 1 8 0", "M11 12l9-9M16 7l3 3") }
    val Qr by lazy {
        build(
            "Qr",
            "M3 3h7v7H3z",
            "M14 3h7v7h-7z",
            "M3 14h7v7H3z",
            "M14 14h3v3h-3zM14 20h7M20 14v3",
        )
    }
    val Fingerprint by lazy {
        build(
            "Fingerprint",
            "M12 11v3a6 6 0 0 0 1 3",
            "M8 11a4 4 0 0 1 8 0v1",
            "M5 12a7 7 0 0 1 14 0c0 2 0 3-.5 5",
            "M8.5 20c.8-1.6 1-3 1-6",
        )
    }
    val Shield by lazy { build("Shield", "M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z", "M12 11.5V16") }
    val Card by lazy {
        build(
            "Card",
            "M3 8a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z",
            "M3 10h18",
        )
    }
    val Note by lazy {
        build(
            "Note",
            "M6 3h9l4 4v13a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z",
            "M9 12h6M9 16h6",
        )
    }
    val Check by lazy { build("Check", "M5 12l5 5 9-10") }
}
