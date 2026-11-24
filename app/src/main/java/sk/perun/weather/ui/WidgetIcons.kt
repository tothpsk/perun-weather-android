package sk.perun.weather.ui

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

private fun iconPath(d:String):Path = PathParser().parsePathString(d).toNodes().toPath()

internal fun widgetIconBitmap(key:String,color:Int,size:Int,dp:Float=18f):Bitmap = glyphPicture(widgetGlyph(key),color,size,dp)

/** The glyph of a quantity in the widgets and the notifications: the drawings of the app (PerunGlyphs.kt). */
internal fun widgetGlyph(key:String):Glyph = when(key) {
    "rain","precip","drop"->Glyph.Rain;"snow"->Glyph.Snow;"wind"->Glyph.Wind;"gust"->Glyph.Gust;"uv"->Glyph.Uv
    "cloud"->Glyph.Cloud;"humidity"->Glyph.Humidity;"dew"->Glyph.Dew;"visibility"->Glyph.Visibility;"pressure"->Glyph.Pressure
    "prob","umbrella"->Glyph.Umbrella
    else->Glyph.Temp
}

/** The arrow of the wind direction for the widgets: it points to where the wind blows to, in the shape of the arrows of the expanded day on the Forecast screen. An empty picture without a direction. */
internal fun widgetWindArrowBitmap(direction:Double,color:Int,size:Int,dp:Float=14f):Bitmap {
    val px=size.coerceAtLeast(1)
    val image=ImageBitmap(px,px)
    if(!direction.isFinite()) return image.asAndroidBitmap()
    CanvasDrawScope().draw(Density(1f),LayoutDirection.Ltr,androidx.compose.ui.graphics.Canvas(image),Size(px.toFloat(),px.toFloat())) {
        // The geometry of TkDirectionArrow: the line of the glyphs, at least 1.3 dp.
        val angle=(direction+180)*Math.PI/180
        val v=Offset(kotlin.math.sin(angle).toFloat(),-kotlin.math.cos(angle).toFloat())
        val stroke=maxOf(px*GlyphLine/24f,1.3f*px/dp)
        val half=(px-stroke)/2;val c=Offset(px/2f,px/2f)
        val tip=c+v*half;val base=tip-v*(half*4f/7f);val side=Offset(-v.y,v.x)*(half*3f/7f)
        val col=Color(color)
        drawLine(col,c-v*half,tip,stroke,StrokeCap.Round);drawLine(col,tip,base+side,stroke,StrokeCap.Round);drawLine(col,tip,base-side,stroke,StrokeCap.Round)
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

/** The icon of a value in a cell of a widget: the arrow of the trend for the pressure when its change is known, otherwise the icon of the quantity. */
internal fun widgetParamIcon(key:String,color:Int,size:Int,pressureChange:Double):Bitmap =
    if(key=="pressure" && pressureChange.isFinite()) widgetPressureTrendBitmap(pressureChange,color,size) else widgetIconBitmap(key,color,size)

/** The trend of the pressure as on the Now screen (TkTrendGraphic): a ring and an arrow turned up by a rise and down by a fall, 60 degrees at 3 hPa in 3 hours. */
internal fun widgetPressureTrendBitmap(change:Double,color:Int,size:Int):Bitmap =
    glyphPicture(Glyph.Trend,color,size,18f,Color(color).copy(alpha=.6f).toArgb(),(-change/3.0*60.0).coerceIn(-60.0,60.0).toFloat())
