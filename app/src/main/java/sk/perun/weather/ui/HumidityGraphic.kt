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

@Composable internal fun HumidityGlyph(color:Color,dimension:Dp=22.dp) {
    Canvas(Modifier.size(dimension)) {
        val k=size.width/24f
        scale(k,k,Offset.Zero) {
            val stroke=Stroke(1.6f,cap=StrokeCap.Round,join=StrokeJoin.Round)
            drawPath(humidityDropPath(),color,style=stroke)
            drawCircle(color,1.25f,Offset(9.4f,12.8f),style=Stroke(1.1f))
            drawCircle(color,1.25f,Offset(14.6f,17.6f),style=Stroke(1.1f))
            drawLine(color,Offset(9.5f,18.1f),Offset(14.5f,12.3f),1.4f,StrokeCap.Round)
        }
    }
}

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

@Composable internal fun DewPointGlyph(color:Color,dimension:Dp=22.dp) {
    Canvas(Modifier.size(dimension)) {
        val k=size.width/24f
        scale(k,k,Offset.Zero) {
            val thermometer=Path().apply {
                moveTo(7f,14.37f)
                lineTo(7f,5f)
                arcTo(androidx.compose.ui.geometry.Rect(7f,3f,11f,7f),180f,180f,false)
                lineTo(11f,14.37f)
                arcTo(androidx.compose.ui.geometry.Rect(5.2f,13.8f,12.8f,21.4f),-58.2f,296.4f,false)
                close()
            }
            drawPath(thermometer,color,style=Stroke(1.6f,cap=StrokeCap.Round,join=StrokeJoin.Round))
            drawLine(color,Offset(9f,17.6f),Offset(9f,10.5f),1.5f,StrokeCap.Round)
            drawCircle(color,1.7f,Offset(9f,17.6f))
            val drop=Path().apply {
                moveTo(17.5f,4.5f)
                cubicTo(17.5f,4.5f,14.2f,8.2f,14.2f,11f)
                arcTo(androidx.compose.ui.geometry.Rect(14.2f,7.7f,20.8f,14.3f),180f,-180f,false)
                cubicTo(20.8f,8.2f,17.5f,4.5f,17.5f,4.5f)
                close()
            }
            drawPath(drop,color)
        }
    }
}

@Composable internal fun GustGlyph(color:Color,dimension:Dp=22.dp) {
    Canvas(Modifier.size(dimension)) {
        val k=size.width/24f
        scale(k,k,Offset.Zero) {
            val p=Path().apply {
                moveTo(1.5f,9f);lineTo(12f,9f)
                cubicTo(16.7f,9f,16.7f,2.5f,12.9f,2.5f)
                cubicTo(10.9f,2.5f,10f,4f,10f,5.4f)
                moveTo(4.5f,12f);lineTo(18.3f,12f)
                cubicTo(23.3f,12f,23.3f,5.2f,19.2f,5.2f)
                cubicTo(17.5f,5.2f,16.7f,6.6f,16.7f,8f)
                moveTo(1.5f,15f);lineTo(17.8f,15f)
                cubicTo(23f,15f,23f,21.5f,19f,21.5f)
                cubicTo(17.1f,21.5f,16.1f,20f,16.1f,18.6f)
            }
            drawPath(p,color,style=Stroke(1.8f,cap=StrokeCap.Round,join=StrokeJoin.Round))
        }
    }
}
