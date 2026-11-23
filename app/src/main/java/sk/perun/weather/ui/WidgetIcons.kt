package sk.perun.weather.ui

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

private val SharedIconKeys=setOf("temp","rain","snow","wind","uv","gust","cloud","humidity","dew","visibility","pressure")
private fun iconPath(d:String):Path = PathParser().parsePathString(d).toNodes().toPath()

/**
 * The icon of a quantity for every home-screen widget, in the shapes of the app: the same drawing as the rows of the
 * expanded day on the Forecast screen (see rowGlyph in DayWidget.kt). The cloud is the outline of the cloud cover tile
 * on Now (CloudFillPath in OverviewArt.kt). A quantity without such a shape keeps the icon of the notifications.
 */
internal fun widgetIconBitmap(key:String,color:Int,size:Int):Bitmap {
    if(key !in SharedIconKeys) return widgetGlyphBitmap(key,color,size)
    val px=size.coerceAtLeast(1);val c=Color(color)
    val image=ImageBitmap(px,px)
    CanvasDrawScope().draw(Density(1f),LayoutDirection.Ltr,androidx.compose.ui.graphics.Canvas(image),Size(px.toFloat(),px.toFloat())) {
        scale(px/24f,px/24f,Offset.Zero) {
            val s=Stroke(2f,cap=StrokeCap.Round,join=StrokeJoin.Round)
            when(key) {
                "temp"->drawPath(iconPath("M10 4a2 2 0 0 1 4 0v9a4 4 0 1 1-4 0Z"),c,style=s)
                "rain"->drawPath(iconPath("M12 2C12 2 5 10 5 15a7 7 0 0 0 14 0C19 10 12 2 12 2Z"),c,style=s)
                "snow"->drawPath(iconPath("M12 3v18M4.2 7.5l15.6 9M4.2 16.5l15.6-9"),c,style=s)
                "wind"->drawPath(iconPath("M3 9h11a3 3 0 1 0-3-3M3 15h15a3 3 0 1 1-3 3M3 12h7"),c,style=s)
                // The same drawing as SummaryIcon.Uv in OverviewTileKit.kt: seven rays, a small disc at 12, 7 and the letters UV below.
                "uv"->{ val sun=Stroke(1.6f,cap=StrokeCap.Round,join=StrokeJoin.Round);drawPath(iconPath("M12 1.1v1.3M6.1 7h1.3M16.6 7h1.3M7.83 2.83l0.92 0.92M16.17 2.83l-0.92 0.92M7.83 11.17l0.92-0.92M16.17 11.17l-0.92-0.92"),c,style=sun);drawCircle(c,2.4f,Offset(12f,7f),style=sun);drawPath(iconPath("M5.5 14.4v5.1a2.5 2.5 0 0 0 5 0v-5.1M13 14.4l2.75 7.6l2.75-7.6"),c,style=Stroke(1.8f,cap=StrokeCap.Round,join=StrokeJoin.Round)) }
                "gust"->drawPath(Path().apply {
                    moveTo(1.5f,9f);lineTo(12f,9f);cubicTo(16.7f,9f,16.7f,2.5f,12.9f,2.5f);cubicTo(10.9f,2.5f,10f,4f,10f,5.4f)
                    moveTo(4.5f,12f);lineTo(18.3f,12f);cubicTo(23.3f,12f,23.3f,5.2f,19.2f,5.2f);cubicTo(17.5f,5.2f,16.7f,6.6f,16.7f,8f)
                    moveTo(1.5f,15f);lineTo(17.8f,15f);cubicTo(23f,15f,23f,21.5f,19f,21.5f);cubicTo(17.1f,21.5f,16.1f,20f,16.1f,18.6f)
                },c,style=Stroke(1.8f,cap=StrokeCap.Round,join=StrokeJoin.Round))
                // The cloud of the cloud cover tile, drawn in a 130 x 100 box there, brought into the 24 x 24 box of the icons.
                "cloud"->translate(-0.93f,3.73f) { scale(0.196f,0.196f,Offset.Zero) { drawPath(iconPath("M35 77a22 22 0 0 1-4-44 31 31 0 0 1 59-7 25 25 0 0 1 10 51Z"),c,style=Stroke(9.2f,join=StrokeJoin.Round)) } }
                "humidity"->{
                    drawPath(Path().apply { moveTo(12f,2f);cubicTo(12f,2f,5f,10f,5f,15f);cubicTo(5f,24.333334f,19f,24.333334f,19f,15f);cubicTo(19f,10f,12f,2f,12f,2f);close() },c,style=Stroke(1.6f,cap=StrokeCap.Round,join=StrokeJoin.Round))
                    drawCircle(c,1.25f,Offset(9.4f,12.8f),style=Stroke(1.1f))
                    drawCircle(c,1.25f,Offset(14.6f,17.6f),style=Stroke(1.1f))
                    drawLine(c,Offset(9.5f,18.1f),Offset(14.5f,12.3f),1.4f,StrokeCap.Round)
                }
                "dew"->{
                    val thermometer=Path().apply { moveTo(7f,14.37f);lineTo(7f,5f);arcTo(Rect(7f,3f,11f,7f),180f,180f,false);lineTo(11f,14.37f);arcTo(Rect(5.2f,13.8f,12.8f,21.4f),-58.2f,296.4f,false);close() }
                    drawPath(thermometer,c,style=Stroke(1.6f,cap=StrokeCap.Round,join=StrokeJoin.Round))
                    drawLine(c,Offset(9f,17.6f),Offset(9f,10.5f),1.5f,StrokeCap.Round)
                    drawCircle(c,1.7f,Offset(9f,17.6f))
                    drawPath(Path().apply { moveTo(17.5f,4.5f);cubicTo(17.5f,4.5f,14.2f,8.2f,14.2f,11f);arcTo(Rect(14.2f,7.7f,20.8f,14.3f),180f,-180f,false);cubicTo(20.8f,8.2f,17.5f,4.5f,17.5f,4.5f);close() },c)
                }
                "visibility"->{
                    drawPath(Path().apply { moveTo(2f,12f);cubicTo(7f,2f,17f,2f,22f,12f);cubicTo(17f,22f,7f,22f,2f,12f);close() },c,style=Stroke(1.7f,cap=StrokeCap.Round,join=StrokeJoin.Round))
                    drawCircle(c,3.5f,Offset(12f,12f))
                }
                "pressure"->{
                    drawCircle(c,10f,Offset(12f,12f),style=Stroke(1.7f,cap=StrokeCap.Round,join=StrokeJoin.Round))
                    drawLine(c,Offset(6f,12f),Offset(18f,12f),1.8f,StrokeCap.Round)
                    drawLine(c,Offset(14f,8f),Offset(18f,12f),1.8f,StrokeCap.Round)
                    drawLine(c,Offset(14f,16f),Offset(18f,12f),1.8f,StrokeCap.Round)
                }
                else->Unit
            }
        }
    }
    return image.asAndroidBitmap()
}

/** The arrow of the wind direction for the widgets: it points to where the wind blows to, in the shape of the arrows of the expanded day on the Forecast screen. An empty picture without a direction. */
internal fun widgetWindArrowBitmap(direction:Double,color:Int,size:Int):Bitmap {
    val px=size.coerceAtLeast(1)
    val image=ImageBitmap(px,px)
    if(!direction.isFinite()) return image.asAndroidBitmap()
    CanvasDrawScope().draw(Density(1f),LayoutDirection.Ltr,androidx.compose.ui.graphics.Canvas(image),Size(px.toFloat(),px.toFloat())) {
        val a=px*0.86f;val m=px/2f
        rotate(((direction+180)%360).toFloat(),Offset(m,m)) {
            drawPath(Path().apply { moveTo(m,m+0.5f*a);lineTo(m,m-0.5f*a);moveTo(m-0.214f*a,m-0.214f*a);lineTo(m,m-0.5f*a);lineTo(m+0.214f*a,m-0.214f*a) },Color(color),style=Stroke(0.11f*a,cap=StrokeCap.Round,join=StrokeJoin.Round))
        }
    }
    return image.asAndroidBitmap()
}
/** Shows the arrow behind the value of a cell for the wind and the gusts, and hides it for every other value. */
internal fun widgetWindArrowView(v:android.widget.RemoteViews,id:Int,key:String,direction:Double,color:Int) {
    val on=(key=="wind" || key=="gust") && direction.isFinite()
    v.setViewVisibility(id,if(on) android.view.View.VISIBLE else android.view.View.GONE)
    if(on) v.setImageViewBitmap(id,widgetWindArrowBitmap(direction,color,42))
}

// The weather symbols of the app: the shapes and colours of WeatherSymbol in OverviewArt.kt, drawn still (no animation) in a 100 x 100 box.
private val SymGold=Color(0xFFFFD166)
private val SymMoon=Color(0xFFD2DEF5)
private val SymCloud=Color(0xFFCBD7E7)
private val SymSnow=Color(0xFFE8EAF0)
private val SymDrop=Color(0xFF70D0FF)
private val SymRays by lazy { iconPath("M50 8v10m0 64v10M8 50h10m64 0h10M20 20l7 7m46 46l7 7M20 80l7-7m46-46l7-7") }
private val SymMoonPath by lazy { iconPath("M70 15A38 38 0 1 0 85 72 34 34 0 0 1 70 15") }
private val SymCloudPath by lazy { iconPath("M23 73a18 18 0 0 1-2-36 27 27 0 0 1 51-5 21 21 0 0 1 7 41Z") }
private val SymDrops by lazy { iconPath("M28 75l-5 12m28-12l-5 12m28-12l-5 12") }
private val SymBolt by lazy { iconPath("M52 60L35 83h15l-6 16 26-27H54l8-12Z") }
private val SymFog by lazy { iconPath("M12 82h75M18 92h63") }

private fun androidx.compose.ui.graphics.drawscope.DrawScope.symbolSun() {
    drawPath(SymRays,SymGold,style=Stroke(4f,cap=StrokeCap.Round))
    drawCircle(SymGold,23f,Offset(50f,50f))
}

/** The weather symbol of a WMO code for the home-screen widgets, the same drawing as WeatherSymbol in the app. A negative code gives an empty picture. */
internal fun widgetSymbolBitmap(code:Int,day:Boolean,size:Int):Bitmap {
    val px=size.coerceAtLeast(1)
    val image=ImageBitmap(px,px)
    if(code<0) return image.asAndroidBitmap()
    CanvasDrawScope().draw(Density(1f),LayoutDirection.Ltr,androidx.compose.ui.graphics.Canvas(image),Size(px.toFloat(),px.toFloat())) {
        scale(px/100f,px/100f,Offset.Zero) {
            when {
                code==0 -> if(day) symbolSun() else drawPath(SymMoonPath,SymMoon)
                code in 1..2 -> {
                    scale(.75f,.75f,Offset.Zero) { if(day) symbolSun() else drawPath(SymMoonPath,SymMoon) }
                    translate(15f,20f) { scale(.85f,.8f,Offset.Zero) { drawPath(SymCloudPath,SymCloud) } }
                }
                code==45 || code==48 -> { drawPath(SymCloudPath,SymCloud);drawPath(SymFog,SymCloud,style=Stroke(4f,cap=StrokeCap.Round)) }
                code>=95 -> { drawPath(SymCloudPath,SymCloud);drawPath(SymBolt,SymGold) }
                code in listOf(71,73,75,77,85,86) -> {
                    drawPath(SymCloudPath,SymCloud)
                    listOf(27f,50f,73f).forEachIndexed { i,x -> drawCircle(SymSnow,4f,Offset(x,if(i==1) 91f else 88f)) }
                }
                code>=51 -> {
                    translate(0f,-12f) { drawPath(SymCloudPath,SymCloud) }
                    drawPath(SymDrops,SymDrop,style=Stroke(5f,cap=StrokeCap.Round))
                }
                else -> drawPath(SymCloudPath,SymCloud)
            }
        }
    }
    return image.asAndroidBitmap()
}
