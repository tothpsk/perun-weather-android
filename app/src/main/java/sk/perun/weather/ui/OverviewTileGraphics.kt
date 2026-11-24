package sk.perun.weather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import java.time.Instant
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// Graphics of the tiles of the Now screen and of the day cards of the Forecast screen. The longer side of a drawing, with its
// line, is TileLong: the height of a tall drawing, the width of a wide one. The line is that of an icon of TileLong
// (GlyphLine of the 24 grid), the same for every drawing; the drawings fill their area, so they touch its edges.
internal val TileLong=40.dp
internal val TileLine=TileLong*(GlyphLine/24f)

// Exact bounds of the drawings in the 24 grid, without the line (measured from their paths).
private fun tileBounds(g:Glyph):Rect=when(g) {
    Glyph.Cloud->Rect(2.87f,6.06f,21.12f,17.95f)
    Glyph.Rain->Rect(5.61f,2.88f,18.39f,21.13f)
    Glyph.Snow->Rect(4.96f,3.88f,19.04f,20.13f)
    Glyph.EyeTile->Rect(3.09f,4.88f,20.91f,19.12f)
    Glyph.Trend->Rect(2.61f,2.61f,21.39f,21.39f)
    Glyph.Uv->Rect(6.3f,2.79f,17.7f,21.13f)
    Glyph.Temp->Rect(8.06f,2.87f,15.96f,21.1f)
    else->Rect(0f,0f,24f,24f)
}

private fun fitSize(b:Rect,long:Dp,line:Dp):DpSize {
    val s=(long-line)/max(b.width,b.height)
    return DpSize(s*b.width+line,s*b.height+line)
}

// Draws content (in the 24 grid) so that the bounds b with the line fill the draw area; line in pixels.
private inline fun DrawScope.fitted(b:Rect,line:Float,content:DrawScope.(k:Float)->Unit) {
    val k=(max(size.width,size.height)-line)/max(b.width,b.height)
    translate(line/2f-b.left*k,line/2f-b.top*k) { scale(k,k,Offset.Zero) { content(k) } }
}

/** A glyph drawn over its own bounds: the longer side, with the line, is long. */
@Composable internal fun TkGlyph(g:Glyph,color:Color,long:Dp=TileLong,line:Dp=TileLine,muted:Color=color,turn:Float=0f) {
    val b=tileBounds(g)
    Canvas(Modifier.size(fitSize(b,long,line))) {
        val l=line.toPx()
        fitted(b,l) { k-> drawGlyphLayers(g,color,muted,turn,l/k) }
    }
}

/** The outline of a glyph (Cloud, Rain) filled from below to fraction, the empty part dark; label in its round part. */
@Composable internal fun TkFillGraphic(g:Glyph,fraction:Double,color:Color,long:Dp=TileLong,label:String?=null) {
    val b=tileBounds(g)
    val shape=g.layers.first().path
    val measurer=rememberTextMeasurer()
    Canvas(Modifier.size(fitSize(b,long,TileLine))) {
        val l=TileLine.toPx()
        val f=(fraction.takeIf { it.isFinite() } ?: 0.0).coerceIn(0.0,1.0).toFloat()
        val level=b.bottom-f*b.height
        fitted(b,l) { k->
            drawPath(shape,Color(0xFF1A1D24))
            clipPath(shape) { drawRect(color,Offset(b.left-1f,level),Size(b.width+2f,b.bottom+1f-level)) }
            drawPath(shape,ForecastMuted,style=Stroke(l/k,join=StrokeJoin.Round))
        }
        if(label!=null) {
            val k=(max(size.width,size.height)-l)/max(b.width,b.height)
            val y0=l/2f-b.top*k
            val text=measurer.measure(label,TextStyle(fontSize=(8f*k).toSp(),fontWeight=FontWeight.Bold))
            val topLeft=Offset(size.width/2f-text.size.width/2f,y0+15f*k-text.size.height/2f)
            val cut=y0+level*k
            val onFill=if(color.luminance()>0.5f) Color(0xFF101820) else Color.White
            clipRect(0f,0f,size.width,cut) { drawText(text,color=Color(0xFFE8EAF0),topLeft=topLeft) }
            clipRect(0f,cut,size.width,size.height) { drawText(text,color=onFill,topLeft=topLeft) }
        }
    }
}

// The arrows of the rise and the set beside the times (20 sp): the line of the icons of size M.
@Composable internal fun TkRiseArrow(up:Boolean,color:Color) {
    Canvas(Modifier.width(12.dp).height(15.dp)) {
        val cx=size.width/2f
        val stroke=1.6.dp.toPx()
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
// 64 x 40 dp; the dots, the Sun with its glow and the horizon grow with the width (drawn first for 58 dp), the track has
// the line of the tile graphics.
@Composable internal fun TkSunArc(sunrise:Instant?,sunset:Instant?,now:Instant) {
    Canvas(Modifier.size(64.dp,40.dp)) {
        val w=size.width
        val q=w/58.dp.toPx()
        val base=size.height-3.dp.toPx()*q
        val r=min(w/2f-5.dp.toPx()*q,base-7.5.dp.toPx()*q).coerceAtLeast(1f)
        val cx=w/2f
        val thick=TileLine.toPx()
        val corner=Offset(cx-r,base-r)
        val box=Size(2*r,2*r)
        drawArc(Color(0x33FFFFFF),180f,180f,false,topLeft=corner,size=box,style=Stroke(thick,cap=StrokeCap.Round))
        drawLine(Color(0xFFBBC4D2),Offset(cx-r-5.dp.toPx()*q,base),Offset(cx+r+5.dp.toPx()*q,base),maxOf(1.3.dp.toPx(),1.dp.toPx()*q),StrokeCap.Round)
        // The dots of the sunrise and the sunset first, so that the Sun is drawn over them.
        drawCircle(Color(0xFF66D17A),2.5.dp.toPx()*q,Offset(cx-r,base))
        drawCircle(Color(0xFFFF8F6B),2.5.dp.toPx()*q,Offset(cx+r,base))
        if(sunrise!=null && sunset!=null && sunset.isAfter(sunrise) && !now.isBefore(sunrise) && !now.isAfter(sunset)) {
            val f=((now.epochSecond-sunrise.epochSecond).toFloat()/(sunset.epochSecond-sunrise.epochSecond)).coerceIn(0f,1f)
            drawArc(Color(0xFFFFD166),180f,180f*f,false,topLeft=corner,size=box,style=Stroke(thick,cap=StrokeCap.Round))
            val a=PI*(1-f)
            val pos=Offset((cx+r*cos(a)).toFloat(),(base-r*sin(a)).toFloat())
            val d=maxOf(3.5.dp.toPx()*q,r*0.13f)
            drawCircle(Color(0x55FFD166),d*1.9f,pos)
            drawCircle(Color(0xFFFFD166),d,pos)
        }
    }
}

// Particles of the air quality tile: filled circles, fitted like the glyphs (the bounds include the circles).
private val ParticleBounds=Rect(3f,3.5f,22f,21f)
@Composable private fun TkParticles(color:Color,long:Dp) {
    Canvas(Modifier.size(fitSize(ParticleBounds,long,0.dp))) {
        fitted(ParticleBounds,0f) {
            drawCircle(color,3f,Offset(6f,16f))
            drawCircle(color,4.5f,Offset(13f,8f))
            drawCircle(color,2.5f,Offset(19f,17f))
            drawCircle(color,2f,Offset(11f,19f))
            drawCircle(color,2f,Offset(20f,6f))
        }
    }
}

// Pollen of the pollen tile: filled circles, fitted like the glyphs.
private val PollenBounds=Rect(2.1f,2.1f,21.9f,21.9f)
@Composable internal fun TkPollenGraphic(color:Color) {
    Canvas(Modifier.size(fitSize(PollenBounds,TileLong,0.dp))) {
        fitted(PollenBounds,0f) {
            drawCircle(color,3f,Offset(12f,12f))
            for(i in 0 until 6) {
                val a=i*PI/3
                drawCircle(color.copy(alpha=.75f),2.4f,Offset((12+7.5*cos(a)).toFloat(),(12+7.5*sin(a)).toFloat()))
            }
        }
    }
}

// 64 x 40 dp: the icon 26 dp high, 4 dp, the scale 10 dp.
@Composable internal fun TkScaleGraphic(air:Boolean,value:Double,max:Double,colorAt:(Double)->Color,tint:Color) {
    Column(Modifier.width(64.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
        if(air) TkParticles(tint,26.dp) else TkGlyph(Glyph.Uv,tint,26.dp,26.dp*(GlyphLine/24f))
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
