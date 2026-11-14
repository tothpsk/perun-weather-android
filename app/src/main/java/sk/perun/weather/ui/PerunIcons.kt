package sk.perun.weather.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

// Paths transcribed from perun-android-mockup-3.html; no filled substitutes.
object PerunIcons {
    private fun outline(name: String, data: String): ImageVector = ImageVector.Builder(
        name = name, defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).addPath(
        pathData = PathParser().parsePathString(data).toNodes(),
        fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.7f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
    ).build()

    val Home = outline("PerunHome", "M4 11l8-7 8 7v9h-5v-6H9v6H4z")
    val Chart = outline("PerunChart", "M3 20h18M5 15l4-5 4 3 6-8")
    val Map = outline("PerunMap", "M9 4L3 6v14l6-2 6 2 6-2V4l-6 2z M9 4v14 M15 6v14")
    val Alert = outline("PerunAlert", "M12 4l9 16H3z M12 10v4 M12 17.2v.1")
    val More = outline("PerunMore", "M6.2 12a1.2 1.2 0 1 0-2.4 0a1.2 1.2 0 1 0 2.4 0 M13.2 12a1.2 1.2 0 1 0-2.4 0a1.2 1.2 0 1 0 2.4 0 M20.2 12a1.2 1.2 0 1 0-2.4 0a1.2 1.2 0 1 0 2.4 0")
    val Menu = outline("PerunMenu", "M4 7h16M4 12h16M4 17h16")
    val Settings = outline("PerunSettings", "M15 12a3 3 0 1 0-6 0a3 3 0 1 0 6 0 M19 12a7 7 0 0 0-.1-1.2l2-1.5-2-3.4-2.3.9a7 7 0 0 0-2-1.2L14.2 3h-4l-.4 2.6a7 7 0 0 0-2 1.2l-2.3-.9-2 3.4 2 1.5A7 7 0 0 0 5 12c0 .4 0 .8.1 1.2l-2 1.5 2 3.4 2.3-.9c.6.5 1.3.9 2 1.2l.4 2.6h4l.4-2.6c.7-.3 1.4-.7 2-1.2l2.3.9 2-3.4-2-1.5c.1-.4.1-.8.1-1.2Z")
    val Close = outline("PerunClose", "M6 6l12 12M18 6L6 18")
    val Plus = outline("PerunPlus", "M12 5v14M5 12h14")
    val Gps = outline("PerunGps", "M18 12a6 6 0 1 0-12 0a6 6 0 1 0 12 0 M13.5 12a1.5 1.5 0 1 0-3 0a1.5 1.5 0 1 0 3 0 M12 2v3M12 19v3M2 12h3M19 12h3")
    val Up = outline("PerunUp", "M6 15l6-6 6 6")
    val Down = outline("PerunDown", "M6 9l6 6 6-6")
    val Back = outline("PerunBack", "M15 6l-6 6 6 6")
    // Six-dot drag handle from the mockup: zero-length strokes with round caps render as dots.
    val Drag = outline("PerunDrag", "M9 7h.01M15 7h.01M9 12h.01M15 12h.01M9 17h.01M15 17h.01")
    // Detailed forecast: chart axes with an hourly curve (added after the mockup).
    val Detail = outline("PerunDetail", "M3 4v16h18 M6 14c2-6 4-6 6-1s4 5 6-1")
    // Air quality and pollen: a leaf with its midrib (added after the mockup).
    val Air = outline("PerunAir", "M5 19c0-8 5-14 14-14 0 9-6 14-14 14z M5 19l8-8")
    // Sun and Moon: a small sun with rays next to a crescent moon (added after the mockup).
    val SunMoon = outline("PerunSunMoon", "M10.6 8a2.6 2.6 0 1 0-5.2 0 2.6 2.6 0 1 0 5.2 0 M8 2v1.5 M2 8h1.5 M3.8 3.8l1 1 M12.2 3.8l-1 1 M3.8 12.2l1-1 M16.2 10.7a3.7 3.7 0 0 0 5.6 5.6 5.6 5.6 0 1 1-5.6-5.6z")
    val Wave = outline("PerunWave", "M2 16c4 0 5-2 6-5 1-4 6-6 9-3-3-1-5 1-4 3 1 3 5 4 9 4 M2 20c2 0 3-1 5-1s3 1 5 1 3-1 5-1 3 1 5 1")
}
