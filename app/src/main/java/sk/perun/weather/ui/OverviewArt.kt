package sk.perun.weather.ui

import sk.perun.weather.R

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

// Colours and scales are taken from the desktop chart.html.
internal val ForecastPink = Color(0xFFFF9696)
internal val RainBlue = Color(0xFF4FC3F7)
internal val ForecastGreen = Color(0xFF88DCA1)
internal val ForecastMuted = Color(0xFFBBC4D2)
private val Gold = Color(0xFFFFD166)
private val MoonTint = Color(0xFFD2DEF5)
private val CloudTint = Color(0xFFCBD7E7)
private val SnowTint = Color(0xFFE8EAF0)
private val DropTint = Color(0xFF70D0FF)

private fun parsed(d: String): Path = PathParser().parsePathString(d).toNodes().toPath()

private val RaysPath = parsed("M50 8v10m0 64v10M8 50h10m64 0h10M20 20l7 7m46 46l7 7M20 80l7-7m46-46l7-7")
private val MoonPath = parsed("M70 15A38 38 0 1 0 85 72 34 34 0 0 1 70 15")
private val CloudPath = parsed("M23 73a18 18 0 0 1-2-36 27 27 0 0 1 51-5 21 21 0 0 1 7 41Z")
private val DropsPath = parsed("M28 75l-5 12m28-12l-5 12m28-12l-5 12")
private val BoltPath = parsed("M52 60L35 83h15l-6 16 26-27H54l8-12Z")
private val FogPath = parsed("M12 82h75M18 92h63")

private enum class Kind { Clear, Part, Cloud, Rain, Snow, Storm, Fog }

private fun kindOf(code: Int): Kind = when {
    code == 0 -> Kind.Clear
    code in 1..2 -> Kind.Part
    code == 3 -> Kind.Cloud
    code == 45 || code == 48 -> Kind.Fog
    code >= 95 -> Kind.Storm
    code in listOf(71, 73, 75, 77, 85, 86) -> Kind.Snow
    code >= 51 -> Kind.Rain
    else -> Kind.Cloud
}

@Composable
private fun animationsEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }
}

private fun DrawScope.sunShape(rotation: Float, pulse: Float) {
    rotate(rotation, Offset(50f, 50f)) { drawPath(RaysPath, Gold, style = Stroke(4f, cap = StrokeCap.Round)) }
    drawCircle(Gold, 23f + pulse, Offset(50f, 50f))
}

private fun DrawScope.drawWeather(kind: Kind, day: Boolean, t: Float, animate: Boolean) {
    val k = size.width / 100f
    val two = (2.0 * PI * t).toFloat()
    val drift = if (animate) sin(two) * 2f else 0f
    scale(k, k, Offset.Zero) {
        when (kind) {
            Kind.Clear -> if (day) sunShape(if (animate) t * 45f else 0f, if (animate) sin(two) * 1.2f else 0f) else drawPath(MoonPath, MoonTint)
            Kind.Part -> {
                scale(.75f, .75f, Offset.Zero) {
                    if (day) sunShape(if (animate) t * 45f else 0f, 0f) else drawPath(MoonPath, MoonTint)
                }
                translate(15f + drift, 20f) { scale(.85f, .8f, Offset.Zero) { drawPath(CloudPath, CloudTint) } }
            }
            Kind.Cloud -> translate(drift, 0f) { drawPath(CloudPath, CloudTint) }
            Kind.Rain -> {
                translate(drift, -12f) { drawPath(CloudPath, CloudTint) }
                val phase = if (animate) (t * 4f) % 1f else 0f
                translate(0f, phase * 8f) {
                    drawPath(DropsPath, DropTint.copy(alpha = if (animate) 1f - phase * .8f else 1f), style = Stroke(5f, cap = StrokeCap.Round))
                }
            }
            Kind.Snow -> {
                translate(drift, 0f) { drawPath(CloudPath, CloudTint) }
                listOf(27f, 50f, 73f).forEachIndexed { i, x ->
                    val y = (if (i == 1) 91f else 88f) + (if (animate) sin(two + i) * 2f else 0f)
                    drawCircle(SnowTint, 4f, Offset(x, y))
                }
            }
            Kind.Storm -> {
                translate(drift, 0f) { drawPath(CloudPath, CloudTint) }
                drawPath(BoltPath, Gold.copy(alpha = if (animate) .65f + .35f * abs(sin(two * 2f)) else 1f))
            }
            Kind.Fog -> {
                translate(drift, 0f) { drawPath(CloudPath, CloudTint) }
                translate(if (animate) sin(two) * 3f else 0f, 0f) {
                    drawPath(FogPath, CloudTint, style = Stroke(4f, cap = StrokeCap.Round))
                }
            }
        }
    }
}

@Composable
internal fun WeatherSymbol(code: Int, dimension: Dp, isDay: Boolean = true, animated: Boolean = false) {
    if (code < 0) {
        Spacer(Modifier.size(dimension))
        return
    }
    val kind = kindOf(code)
    val description = overviewCondition(code)
    if (animated && animationsEnabled()) {
        val phase = rememberInfiniteTransition(label = "weatherIcon").animateFloat(
            0f, 1f, infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Restart), label = "phase",
        )
        Canvas(Modifier.size(dimension).semantics { contentDescription = description }) { drawWeather(kind, isDay, phase.value, true) }
    } else {
        Canvas(Modifier.size(dimension).semantics { contentDescription = description }) { drawWeather(kind, isDay, 0f, false) }
    }
}

// ---- Semantic scales (same thresholds as chart.html) ----

private val UvBands = listOf(
    11.0 to Color(0xFF998CFF), 10.0 to Color(0xFFB54CFF), 9.0 to Color(0xFFFF0099), 8.0 to Color(0xFFD8001D),
    7.0 to Color(0xFFE82C0E), 6.0 to Color(0xFFF85900), 5.0 to Color(0xFFF88700), 4.0 to Color(0xFFF8B600),
    3.0 to Color(0xFFF7E400), 2.0 to Color(0xFFA0CE00), 0.0 to Color(0xFF4EB400),
)

internal fun uvColor(v: Double): Color = if (!v.isFinite() || v < 0) Color(0xFF5B6577) else UvBands.first { v >= it.first }.second

internal fun uvLabel(v: Double): String = when {
    !v.isFinite() -> uiText(R.string.ui_udaj_nedostupny_8bbc9c94)
    v < 3 -> uiText(R.string.ui_nizka_992049fb)
    v < 6 -> uiText(R.string.ui_mierna_5f589b5b)
    v < 8 -> uiText(R.string.ui_vysoka_35517062)
    v < 11 -> uiText(R.string.ui_velmi_vysoka_dd415a6d)
    else -> uiText(R.string.ui_extremna_c1ea86db)
}

internal data class AqiBand(val upper: Double, val color: Color, val name: String)

private val AqiBands = listOf(
    AqiBand(20.0, Color(0xFF50F0E6), uiText(R.string.ui_dobra_19daeeb7)), AqiBand(40.0, Color(0xFF50CCAA), uiText(R.string.ui_prijatelna_47496cf3)),
    AqiBand(60.0, Color(0xFFF0E641), uiText(R.string.ui_mierna_5f589b5b)), AqiBand(80.0, Color(0xFFFF5050), uiText(R.string.ui_zla_258323d1)),
    AqiBand(100.0, Color(0xFF960032), uiText(R.string.ui_velmi_zla_ec30c594)), AqiBand(Double.POSITIVE_INFINITY, Color(0xFF7D2181), uiText(R.string.ui_extremne_zla_9c6c07ef)),
)

internal fun aqiBand(v: Double): AqiBand? = if (!v.isFinite() || v < 0) null else AqiBands.first { v <= it.upper }

// Null below 50 km/h, exactly like weatherSummaryWindColor.
internal fun windAlert(kmh: Double): Color? = when {
    !kmh.isFinite() || kmh < 50 -> null
    kmh < 62 -> Color(0xFFFDD835)
    kmh < 89 -> Color(0xFFFB8C00)
    else -> Color(0xFFE53935)
}

internal fun tempDotColor(t: Double): Color {
    if (!t.isFinite()) return Color(0xFF5B6577)
    val stops = listOf(
        -40.0 to intArrayOf(110, 30, 160), -25.0 to intArrayOf(70, 60, 200), -10.0 to intArrayOf(60, 140, 220),
        0.0 to intArrayOf(90, 190, 210), 10.0 to intArrayOf(110, 190, 110), 20.0 to intArrayOf(210, 200, 70),
        28.0 to intArrayOf(235, 150, 50), 35.0 to intArrayOf(230, 80, 60), 45.0 to intArrayOf(170, 30, 60),
    )
    val c = t.coerceIn(-40.0, 45.0)
    var lo = stops.first()
    var hi = stops.last()
    for (i in 0 until stops.size - 1) {
        if (c >= stops[i].first && c <= stops[i + 1].first) {
            lo = stops[i]
            hi = stops[i + 1]
            break
        }
    }
    val f = (c - lo.first) / (hi.first - lo.first)
    fun channel(i: Int) = (lo.second[i] + (hi.second[i] - lo.second[i]) * f).roundToInt()
    return Color(channel(0), channel(1), channel(2))
}

internal fun precipitationType(rain: Double, showers: Double, snow: Double): Color? {
    val wet = rain > 0 || showers > 0
    return when {
        wet && snow > 0 -> Color(0xFFB388FF)
        snow > 0 -> Color(0xFFE8EAF0)
        rain > 0 -> RainBlue
        showers > 0 -> Color(0xFF2196C9)
        else -> null
    }
}

// Same interpolation as probabilityColour(Math.round(35 + 0.65 * probability)).
internal fun probabilityColor(probability: Double): Color {
    if (!probability.isFinite()) return Color(0xFF5B6577)
    val v = (35 + 0.65 * probability).roundToInt()
    fun channel(a: Int, b: Int) = (a + (b - a) * v / 100.0).roundToInt()
    return Color(channel(48, 79), channel(56, 195), channel(69, 247))
}

internal val PollenNames = mapOf("alder" to uiText(R.string.ui_jelsa_50b3fc2b), "birch" to uiText(R.string.ui_breza_274e3ab2), "grass" to uiText(R.string.ui_trava_30b5b0b5), "mugwort" to uiText(R.string.ui_palina_6c25bcd3), "ragweed" to uiText(R.string.ui_ambrozia_5cdaf78e), "olive" to uiText(R.string.ui_oliva_3462b6a4))
private val PollenThresholds = mapOf(
    "alder" to (50.0 to 200.0), "birch" to (50.0 to 400.0), "grass" to (20.0 to 50.0),
    "mugwort" to (20.0 to 50.0), "ragweed" to (10.0 to 50.0), "olive" to (50.0 to 200.0),
)
internal val PollenLevelColors = listOf(Color(0xFF66D17A), Color(0xFFFFB74D), Color(0xFFFF6B6B))
internal val PollenLevelNames = listOf(uiText(R.string.ui_nizka_7bf8cc6a), uiText(R.string.ui_zvysena_efd7af63), uiText(R.string.ui_vysoka_a43ddc85))

internal fun pollenLevel(key: String, v: Double): Int {
    val t = PollenThresholds[key] ?: return 0
    return if (v >= t.second) 2 else if (v >= t.first) 1 else 0
}

internal fun pollenRatio(key: String, v: Double): Double = v / (PollenThresholds[key]?.second ?: 1.0)

// ---- Graphics ----

internal fun cardinal(degrees: Double): String =
    if (!degrees.isFinite()) "—" else listOf(uiText(R.string.ui_s_8de0b3c4), uiText(R.string.ui_sv_a4cd8b4d), uiText(R.string.ui_v_de5a6f78), uiText(R.string.ui_jv_002b53e7), uiText(R.string.ui_j_6da43b94), uiText(R.string.ui_jz_f296f166), uiText(R.string.ui_z_bbeebd87), uiText(R.string.ui_sz_c29f2aa3))[((degrees / 45.0 + 0.5).toInt() % 8 + 8) % 8]

// line: the outline of the tile graphics; null: the line of an icon of this size (GlyphLine of the 24 grid, at least
// GlyphMinDp), as on the Sun and Moon screen and in the help. The disc fills the area and its outline is drawn over the lit part.
@Composable
internal fun MoonGraphic(illumination: Int, waxing: Boolean, southern: Boolean, modifier: Modifier = Modifier, line: Dp? = null) {
    Canvas(modifier.aspectRatio(1f)) {
        val k = size.width / 64f
        val stroke = (line?.toPx() ?: maxOf(size.width * GlyphLine / 24f, GlyphMinDp.dp.toPx())) / k
        scale(k, k, Offset.Zero) {
            val r = 32f - stroke / 2f
            val cx = 32f
            val cy = 32f
            drawCircle(Color(0xFF252D3D), r, Offset(cx, cy))
            val f = (illumination / 100f).coerceIn(0f, 1f)
            if (f > 0.01f) {
                val rx = maxOf(abs(r * (1 - 2 * f)), 0.01f)
                val lit = Path().apply {
                    moveTo(cx, cy - r)
                    arcTo(Rect(cx - r, cy - r, cx + r, cy + r), -90f, 180f, false)
                    arcTo(Rect(cx - rx, cy - r, cx + rx, cy + r), 90f, if (f < 0.5f) -180f else 180f, false)
                    close()
                }
                val mirror = !waxing != southern
                if (mirror) scale(-1f, 1f, Offset(cx, cy)) { drawPath(lit, Color(0xFFD8E0F0)) } else drawPath(lit, Color(0xFFD8E0F0))
            }
            drawCircle(Color(0xFF8C9BB2), r, Offset(cx, cy), style = Stroke(stroke))
        }
    }
}

@Composable
internal fun Umbrella(color: Color, dimension: Dp = 22.dp) { GlyphIcon(Glyph.Umbrella, color, dimension) }
