package sk.perun.weather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private fun humidityDropPath()=Path().apply {
    moveTo(12f,2f)
    cubicTo(12f,2f,5f,10f,5f,15f)
    cubicTo(5f,24.333334f,19f,24.333334f,19f,15f)
    cubicTo(19f,10f,12f,2f,12f,2f)
    close()
}

@Composable internal fun HumidityGlyph(color:Color,dimension:Dp=22.dp) { GlyphIcon(Glyph.Humidity,color,dimension) }

@Composable internal fun HumidityGraphic(fraction:Double?,color:Color,modifier:Modifier) {
    Canvas(modifier.aspectRatio(1f)) {
        val k=size.width/24f
        scale(k,k,Offset.Zero) {
            val drop=Path().apply {
                moveTo(12f,2f)
                cubicTo(12f,2f,5f,10f,5f,15f)
                arcTo(androidx.compose.ui.geometry.Rect(5f,8f,19f,22f),180f,-180f,false)
                cubicTo(19f,10f,12f,2f,12f,2f)
                close()
            }
            val f=fraction?.takeIf { it.isFinite() } ?: 0.0
            val y=22f-20f*f.coerceIn(0.0,1.0).toFloat()
            drawPath(drop,Color(0xFF1A1D24))
            clipPath(drop) { drawRect(color,Offset(0f,y),Size(24f,24f-y)) }
            drawPath(drop,Color(0xFFBBC4D2),style=Stroke(1.2f))
        }
    }
}

@Composable internal fun HumidityPercentDrop(fraction:Double?,color:Color,modifier:Modifier=Modifier) {
    val measurer=rememberTextMeasurer()
    Canvas(modifier.aspectRatio(1f)) {
        val k=size.width/24f
        val f=(fraction?.takeIf { it.isFinite() } ?: 0.0).coerceIn(0.0,1.0).toFloat()
        val level=(22f-20f*f)*k
        scale(k,k,Offset.Zero) {
            val drop=Path().apply {
                moveTo(12f,2f)
                cubicTo(12f,2f,5f,10f,5f,15f)
                arcTo(androidx.compose.ui.geometry.Rect(5f,8f,19f,22f),180f,-180f,false)
                cubicTo(19f,10f,12f,2f,12f,2f)
                close()
            }
            val y=22f-20f*f
            drawPath(drop,Color(0xFF1A1D24))
            clipPath(drop) { drawRect(color,Offset(0f,y),Size(24f,24f-y)) }
            drawPath(drop,Color(0xFFBBC4D2),style=Stroke(1.2f))
        }
        val label=measurer.measure("%",TextStyle(fontSize=(9f*k).toSp(),fontWeight=FontWeight.Bold))
        val topLeft=Offset(12f*k-label.size.width/2f,15.3f*k-label.size.height/2f)
        val onFill=if(color.luminance()>0.5f) Color(0xFF101820) else Color.White
        clipRect(0f,0f,size.width,level) { drawText(label,color=Color(0xFFE8EAF0),topLeft=topLeft) }
        clipRect(0f,level,size.width,size.height) { drawText(label,color=onFill,topLeft=topLeft) }
    }
}

@Composable internal fun DewPointGlyph(color:Color,dimension:Dp=22.dp) { GlyphIcon(Glyph.Dew,color,dimension) }

@Composable internal fun GustGlyph(color:Color,dimension:Dp=22.dp) { GlyphIcon(Glyph.Gust,color,dimension) }
