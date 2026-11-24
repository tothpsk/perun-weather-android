package sk.perun.weather.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

// Paths transcribed from perun-android-mockup-3.html, fitted to the key shapes of PerunGlyphs.kt; no filled substitutes.
object PerunIcons {
    private fun outline(name: String, data: String): ImageVector = ImageVector.Builder(
        name = name, defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).addPath(
        pathData = PathParser().parsePathString(data).toNodes(),
        fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = GlyphLine,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
    ).build()

    val Home = outline("PerunHome", "M 3.88,10.98 l 8.12,-7.11 l 8.12,7.11 l 0,9.14 l -5.08,0 l 0,-6.09 L 8.95,14.03 l 0,6.09 L 3.88,20.12 z")
    val Chart = outline("PerunChart", "M 3.88,18.77 l 16.25,0 M 5.68,14.26 l 3.61,-4.51 l 3.61,2.71 l 5.42,-7.22")
    val Map = outline("PerunMap", "M 9.29,4.78 L 3.88,6.58 l 0,12.64 l 5.42,-1.81 l 5.42,1.81 l 5.42,-1.81 L 20.12,4.78 l -5.42,1.81 z M 9.29,4.78 l 0,12.64 M 14.71,6.58 l 0,12.64")
    val Alert = outline("PerunAlert", "M 12,4.78 l 8.12,14.44 L 3.88,19.22 z M 12,10.19 l 0,3.61 M 12,16.69 l 0,0.09")
    val More = outline("PerunMore", "M 5.55,12 a 1.34,1.34 0 0,0 -2.67,0 a 1.34,1.34 0 0,0 2.67,0 M 13.34,12 a 1.34,1.34 0 1,0 -2.67,0 a 1.34,1.34 0 1,0 2.67,0 M 21.12,12 a 1.34,1.34 0 1,0 -2.67,0 a 1.34,1.34 0 1,0 2.67,0")
    val Menu = outline("PerunMenu", "M 2.88,6.3 l 18.25,0 M 2.88,12 l 18.25,0 M 2.88,17.7 l 18.25,0")
    val Settings = outline("PerunSettings", "M 15.04,12 a 3.04,3.04 0 0,0 -6.08,0 a 3.04,3.04 0 0,0 6.08,0 M 19.1,12 a 7.1,7.1 0 0,0 -0.1,-1.22 l 2.03,-1.52 l -2.03,-3.45 l -2.33,0.91 a 7.1,7.1 0 0,0 -2.03,-1.22 L 14.23,2.88 l -4.06,0 l -0.41,2.64 a 7.1,7.1 0 0,0 -2.03,1.22 l -2.33,-0.91 l -2.03,3.45 l 2.03,1.52 A 7.1,7.1 0 0,0 4.9,12 c 0,0.41 0,0.81 0.1,1.22 l -2.03,1.52 l 2.03,3.45 l 2.33,-0.91 c 0.61,0.51 1.32,0.91 2.03,1.22 l 0.41,2.64 l 4.06,0 l 0.41,-2.64 c 0.71,-0.3 1.42,-0.71 2.03,-1.22 l 2.33,0.91 l 2.03,-3.45 l -2.03,-1.52 c 0.1,-0.41 0.1,-0.81 0.1,-1.22 Z")
    val Close = outline("PerunClose", "M 3.88,3.88 l 16.25,16.25 M 20.12,3.88 L 3.88,20.12")
    val Plus = outline("PerunPlus", "M 12,3.88 l 0,16.25 M 3.88,12 l 16.25,0")
    val Gps = outline("PerunGps", "M 17.48,12 a 5.47,5.47 0 0,0 -10.95,0 a 5.47,5.47 0 0,0 10.95,0 M 13.37,12 a 1.37,1.37 0 0,0 -2.74,0 a 1.37,1.37 0 0,0 2.74,0 M 12,2.88 l 0,2.74 M 12,18.39 l 0,2.74 M 2.88,12 l 2.74,0 M 18.39,12 l 2.74,0")
    val Up = outline("PerunUp", "M6 15l6-6 6 6")
    val Down = outline("PerunDown", "M6 9l6 6 6-6")
    val Back = outline("PerunBack", "M 16.56,2.88 l -9.12,9.12 l 9.12,9.12")
    // Six-dot drag handle from the mockup: zero-length strokes with round caps render as dots.
    val Drag = outline("PerunDrag", "M9 7h.01M15 7h.01M9 12h.01M15 12h.01M9 17h.01M15 17h.01")
    // Detailed forecast: chart axes with an hourly curve (added after the mockup).
    val Detail = outline("PerunDetail", "M 3.88,4.78 l 0,14.44 l 16.25,0 M 6.58,13.81 c 1.81,-5.42 3.61,-5.42 5.42,-0.9 s 3.61,4.51 5.42,-0.9")
    // Air quality and pollen: a leaf with its midrib (added after the mockup).
    val Air = outline("PerunAir", "M 3.88,20.12 c 0,-9.29 5.8,-16.25 16.25,-16.25 c 0,10.45 -6.96,16.25 -16.25,16.25 z M 3.88,20.12 l 9.29,-9.29")
    // Sun and Moon: a small sun with rays next to a crescent moon (added after the mockup).
    val SunMoon = outline("PerunSunMoon", "M 10.94,8.77 a 2.12,2.12 0 0,0 -4.25,0 a 2.12,2.12 0 0,0 4.25,0 M 8.82,3.88 l 0,1.22 M 3.92,8.77 l 1.22,0 M 5.39,5.34 l 0.82,0.82 M 12.24,5.34 l -0.82,0.82 M 5.39,12.2 l 0.82,-0.82 M 15.51,10.98 a 3.23,3.23 0 0,0 4.57,4.57 a 4.57,4.57 0 1,1 -4.57,-4.57 z")
    val Wave = outline("PerunWave", "M2 16c4 0 5-2 6-5 1-4 6-6 9-3-3-1-5 1-4 3 1 3 5 4 9 4 M2 20c2 0 3-1 5-1s3 1 5 1 3-1 5-1 3 1 5 1")
}
