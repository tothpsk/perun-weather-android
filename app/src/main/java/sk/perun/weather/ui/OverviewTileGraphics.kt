package sk.perun.weather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.Instant
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// Reserves only the visible part of a graphic so its drawn shape touches the right and bottom edges.
@Composable internal fun TkFlush(width:Dp,height:Dp,insetRight:Dp,insetBottom:Dp,content:@Composable ()->Unit) {
    Box(Modifier.size(width-insetRight,height-insetBottom)) {
        Box(Modifier.wrapContentSize(Alignment.TopStart,unbounded=true)) {
            Box(Modifier.size(width,height)) { content() }
        }
    }
}

@Composable internal fun TkRiseArrow(up:Boolean,color:Color) {
    Canvas(Modifier.width(12.dp).height(15.dp)) {
        val cx=size.width/2f
        val stroke=2.2.dp.toPx()
        val top=1.dp.toPx()
        val bottom=size.height-1.dp.toPx()
        val tip=if(up) top else bottom
        val tail=if(up) bottom else top
        val head=3.5.dp.toPx()
        val dir=if(up) 1f else -1f
        drawLine(color,Offset(cx,tip),Offset(cx,tail),stroke,StrokeCap.Round)
        drawLine(color,Offset(cx,tip),Offset(cx-head,tip+dir*head),stroke,StrokeCap.Round)
        drawLine(color,Offset(cx,tip),Offset(cx+head,tip+dir*head),stroke,StrokeCap.Round)
    }
}

@Composable internal fun TkSunArc(sunrise:Instant?,sunset:Instant?,now:Instant,modifier:Modifier) {
    Canvas(modifier.aspectRatio(1.6f)) {
        val w=size.width
        val h=size.height
        val base=h-1.dp.toPx()
        val r=min(w/2f-4.dp.toPx(),base-8.dp.toPx())
        val cx=w/2f-0.5.dp.toPx()
        drawLine(Color(0xFF8C9BB2),Offset(1.dp.toPx(),base),Offset(w-1.dp.toPx(),base),1.6.dp.toPx(),StrokeCap.Round)
        drawArc(Color(0xFF4D5668),180f,180f,false,topLeft=Offset(cx-r,base-r),size=Size(2*r,2*r),style=Stroke(1.6.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(),3.dp.toPx()))))
        if(sunrise!=null && sunset!=null && sunset.isAfter(sunrise)) {
            val p=(now.epochSecond-sunrise.epochSecond).toDouble()/(sunset.epochSecond-sunrise.epochSecond)
            val day=p in 0.0..1.0
            val a=PI*p.coerceIn(0.0,1.0)
            val pos=Offset((cx-r*cos(a)).toFloat(),(base-r*sin(a)).toFloat())
            if(day) {
                drawCircle(Color(0xFFFFD166),6.dp.toPx(),pos)
                drawCircle(Color(0xFF8A6D1F),6.dp.toPx(),pos,style=Stroke(1.4.dp.toPx()))
            } else {
                drawCircle(Color(0xFF2B3550),5.dp.toPx(),pos)
                drawCircle(Color(0xFF8C9BB2),5.dp.toPx(),pos,style=Stroke(1.4.dp.toPx()))
            }
        }
    }
}

@Composable private fun TkParticles(color:Color,modifier:Modifier) {
    Canvas(modifier) {
        val k=size.width/24f
        scale(k,k,Offset.Zero) {
            drawCircle(color,3f,Offset(6f,16f))
            drawCircle(color,4.5f,Offset(13f,8f))
            drawCircle(color,2.5f,Offset(19f,17f))
            drawCircle(color,2f,Offset(11f,19f))
            drawCircle(color,2f,Offset(20f,6f))
        }
    }
}

@Composable internal fun TkScaleGraphic(air:Boolean,value:Double,max:Double,colorAt:(Double)->Color,tint:Color,modifier:Modifier) {
    Column(modifier,horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
        if(air) TkParticles(tint,Modifier.size(26.dp)) else SummaryGlyph(SummaryIcon.Uv,tint,26.dp)
        Canvas(Modifier.fillMaxWidth().height(10.dp)) {
            val r=size.height/2f
            val stops=(0..12).map { colorAt(max*it/12.0) }
            drawRoundRect(brush=Brush.horizontalGradient(stops),cornerRadius=CornerRadius(r,r))
            if(value.isFinite()) {
                val x=((value/max).coerceIn(0.0,1.0).toFloat()*size.width).coerceIn(r,size.width-r)
                drawCircle(Color.White,r,Offset(x,size.height/2f))
                drawCircle(colorAt(value),r-1.5.dp.toPx(),Offset(x,size.height/2f))
            }
        }
    }
}
