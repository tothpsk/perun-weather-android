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

// The day arc of the Sun and Moon widget (arc in SkyWidget.kt), with its colours and proportions: a faint arc over the horizon
// from the sunrise on the left to the sunset on the right. The part of the day that has passed is in the colour of the Sun
// and the Sun stands at its end; at night the arc stays empty.
@Composable internal fun TkSunArc(sunrise:Instant?,sunset:Instant?,now:Instant,modifier:Modifier) {
    Canvas(modifier.aspectRatio(1.6f)) {
        val w=size.width
        val base=size.height-3.dp.toPx()
        val r=min(w/2f-5.dp.toPx(),base-7.5.dp.toPx()).coerceAtLeast(1f)
        val cx=w/2f
        val thick=maxOf(2.dp.toPx(),r*0.09f)
        val corner=Offset(cx-r,base-r)
        val box=Size(2*r,2*r)
        drawArc(Color(0x33FFFFFF),180f,180f,false,topLeft=corner,size=box,style=Stroke(thick,cap=StrokeCap.Round))
        drawLine(Color(0xFFBBC4D2),Offset(cx-r-5.dp.toPx(),base),Offset(cx+r+5.dp.toPx(),base),1.dp.toPx(),StrokeCap.Round)
        // The dots of the sunrise and the sunset first, so that the Sun is drawn over them.
        drawCircle(Color(0xFF66D17A),2.5.dp.toPx(),Offset(cx-r,base))
        drawCircle(Color(0xFFFF8F6B),2.5.dp.toPx(),Offset(cx+r,base))
        if(sunrise!=null && sunset!=null && sunset.isAfter(sunrise) && !now.isBefore(sunrise) && !now.isAfter(sunset)) {
            val f=((now.epochSecond-sunrise.epochSecond).toFloat()/(sunset.epochSecond-sunrise.epochSecond)).coerceIn(0f,1f)
            drawArc(Color(0xFFFFD166),180f,180f*f,false,topLeft=corner,size=box,style=Stroke(thick,cap=StrokeCap.Round))
            val a=PI*(1-f)
            val pos=Offset((cx+r*cos(a)).toFloat(),(base-r*sin(a)).toFloat())
            val d=maxOf(3.5.dp.toPx(),r*0.13f)
            drawCircle(Color(0x55FFD166),d*1.9f,pos)
            drawCircle(Color(0xFFFFD166),d,pos)
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
