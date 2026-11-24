package sk.perun.weather.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable internal fun HumidityGlyph(color:Color,dimension:Dp=22.dp) { GlyphIcon(Glyph.Humidity,color,dimension) }

/** The drop of the Rain icon filled from below to fraction, as on the Now screen and the Forecast cards; the longer side 40 dp. */
@Composable internal fun HumidityGraphic(fraction:Double?,color:Color,long:Dp=TileLong) {
    TkFillGraphic(Glyph.Rain,fraction ?: 0.0,color,long)
}

/** The same drop with a % sign; the sign is light above the level and dark or white below it. */
@Composable internal fun HumidityPercentDrop(fraction:Double?,color:Color) {
    TkFillGraphic(Glyph.Rain,fraction ?: 0.0,color,label="%")
}

@Composable internal fun DewPointGlyph(color:Color,dimension:Dp=22.dp) { GlyphIcon(Glyph.Dew,color,dimension) }

@Composable internal fun GustGlyph(color:Color,dimension:Dp=22.dp) { GlyphIcon(Glyph.Gust,color,dimension) }
