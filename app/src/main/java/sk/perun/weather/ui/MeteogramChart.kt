package sk.perun.weather.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/** A step of 1, 2 or 5 times a power of ten that is not smaller than the given value. */
internal fun meteoNiceStep(x:Double):Double {
    if(!x.isFinite() || x<=0.0) return 1.0
    val base=10.0.pow(floor(log10(x)))
    return listOf(1.0,2.0,5.0,10.0).first { it*base>=x*0.999999 }*base
}
/** Lowest value and step of an axis that covers min..max in the given number of steps. Zero based axes start at 0 and reach at least the given value; the others start at a whole number and use whole steps. */
internal fun meteoAxis(min:Double,max:Double,zeroBased:Boolean,steps:Int=4,least:Double=0.0):Pair<Double,Double> {
    if(zeroBased) return 0.0 to meteoNiceStep(max(max,least)/steps)
    val lo=floor(min)
    return lo to ceil((max-lo)/steps).coerceAtLeast(1.0)
}

private class MeteoAxis(val key:String,val color:Int,val lo:Double,val step:Double,val decimals:Int,val suffix:String)

/**
 * The chart of the Meteogram widget. Every shown quantity has its own value axis in its colour, with its icon on top:
 * temperature and wind on the left, precipitation and its probability on the right. The time axis shows the hours and,
 * at midnight, the day. Nothing is labelled inside the plot.
 */
internal fun meteogramBitmap(c:Context,d:OverviewData,u:WeatherUnits,hours:Int,show:List<String>,muted:Int,dark:Boolean,wPx:Int,hPx:Int):Bitmap {
    val bmp=Bitmap.createBitmap(wPx,hPx,Bitmap.Config.ARGB_8888);val cv=Canvas(bmp)
    val s=d.series.take(if(hours==48) 48 else 24)
    if(s.size<2) return bmp
    val dn=c.resources.displayMetrics.density
    val p=Paint(Paint.ANTI_ALIAS_FLAG)
    val tempColor=0xFFEF5350.toInt();val windColor=0xFF80CBC4.toInt();val rainColor=0xFF4FC3F7.toInt();val probColor=0xFFB39DDB.toInt()
    val strong=if(dark) 0xFF14161A.toInt() else 0xFFFFFFFF.toInt()
    val grid=if(dark) 0x1F000000 else 0x1FFFFFFF
    val midnight=if(dark) 0x40000000 else 0x40FFFFFF
    fun finite(v:List<Double>):List<Double> = v.filter { it.isFinite() }
    fun decimalsOf(step:Double):Int = if(step>=1.0) 0 else if(step>=0.1) 1 else 2
    fun isDay(t:LocalDateTime):Boolean {
        if(d.sunTimes.isEmpty()) return t.hour in 7..19
        val at=t.atZone(d.zone).toInstant()
        return d.sunTimes.any { at>=it.first && at<it.second }
    }
    val temps=s.map { u.temperature.fromCelsius(it.temperature) }
    val rain=s.map { u.precipitation.fromMillimetres(it.precipitation) }
    val wind=s.map { u.wind.fromKmh(it.wind) };val gust=s.map { u.wind.fromKmh(it.gust) }
    val prob=s.map { it.probability }
    // Inner axes first: temperature and precipitation next to the plot, wind and probability outside them.
    val left=ArrayList<MeteoAxis>();val right=ArrayList<MeteoAxis>()
    if("temp" in show && finite(temps).isNotEmpty()) {
        val f=finite(temps);val axis=meteoAxis(f.minOrNull() ?: 0.0,f.maxOrNull() ?: 0.0,false)
        left+=MeteoAxis("temp",tempColor,axis.first,axis.second,0,"\u00B0")
    }
    val windValues=(if("wind" in show) finite(wind) else emptyList())+(if("gust" in show) finite(gust) else emptyList())
    if(windValues.isNotEmpty()) {
        val axis=meteoAxis(0.0,windValues.maxOrNull() ?: 0.0,true,4,u.wind.fromKmh(20.0))
        left+=MeteoAxis("wind",windColor,axis.first,axis.second,decimalsOf(axis.second),"")
    }
    if("precip" in show && finite(rain).isNotEmpty()) {
        val axis=meteoAxis(0.0,finite(rain).maxOrNull() ?: 0.0,true,4,u.precipitation.fromMillimetres(2.0))
        right+=MeteoAxis("rain",rainColor,axis.first,axis.second,decimalsOf(axis.second),"")
    }
    if("prob" in show && finite(prob).isNotEmpty()) right+=MeteoAxis("prob",probColor,0.0,25.0,0,"")

    val axisW=20*dn
    val plotL=left.size*axisW+2*dn;val plotR=wPx-right.size*axisW-2*dn
    // At two cells the row of weather icons is left out and the axis icons take its place.
    val small=hPx<96*dn
    val iconPx=(18*dn).roundToInt();val glyph=(10*dn).roundToInt()
    val headH=if(small) glyph+2*dn else iconPx+3*dn
    val cloudH=if("cloud" in show) 5*dn else 0f
    val top=headH+cloudH+5*dn;val bottom=hPx-13*dn
    val plotH=max(bottom-top,8*dn)
    val stride=if(plotH<56*dn) 2 else 1
    val step=(plotR-plotL)/(s.size-1)
    fun x(i:Int):Float = plotL+step*i
    fun y(a:MeteoAxis,v:Double):Float = (bottom-(v-a.lo)/(a.step*4)*plotH).toFloat()
    val every=if(s.size>30) 6 else 3

    // Day and night as a faint tint of the plot, so that they take no height.
    p.style=Paint.Style.FILL
    p.color=if(dark) 0x12000000 else 0x0DFFFFFF
    s.forEachIndexed { i,h -> if(isDay(h.time)!=dark) cv.drawRect((x(i)-step/2).coerceAtLeast(plotL),top,(x(i)+step/2).coerceAtMost(plotR),bottom,p) }
    // A very faint grid: the value steps and the labelled hours; midnight is a little stronger.
    val hair=max(1f,0.5f*dn)
    p.color=grid
    for(k in 0..4 step stride) { val gy=bottom-plotH*k/4;cv.drawRect(plotL,gy,plotR,gy+hair,p) }
    s.forEachIndexed { i,h -> if(h.time.hour%every==0) { p.color=if(h.time.hour==0) midnight else grid;cv.drawRect(x(i),top,x(i)+hair,bottom,p) } }
    if("cloud" in show) {
        val rgb=if(dark) 0x5A6478 else 0xC8D2E1
        s.forEachIndexed { i,h ->
            if(h.cloud.isFinite()) {
                p.color=((25+h.cloud*1.9).toInt().coerceIn(0,255) shl 24) or rgb
                cv.drawRect((x(i)-step/2).coerceAtLeast(plotL),headH,(x(i)+step/2).coerceAtMost(plotR),headH+4*dn,p)
            }
        }
    }
    val rainAxis=right.firstOrNull { it.key=="rain" }
    if(rainAxis!=null) s.forEachIndexed { i,h ->
        val v=rain[i]
        if(v.isFinite() && v>0.0) {
            val chance=if(h.probability.isFinite()) h.probability else 100.0
            p.color=android.graphics.Color.argb((90+chance*1.6).toInt().coerceIn(0,255),79,195,247)
            cv.drawRect((x(i)-step*0.35f).coerceAtLeast(plotL),y(rainAxis,v).coerceAtLeast(top),(x(i)+step*0.35f).coerceAtMost(plotR),bottom,p)
        }
    }
    fun line(values:List<Double>,axis:MeteoAxis,color:Int,dashed:Boolean,width:Float) {
        val path=Path();var started=false
        values.forEachIndexed { i,v -> if(v.isFinite()) { if(!started) { path.moveTo(x(i),y(axis,v));started=true } else path.lineTo(x(i),y(axis,v)) } else started=false }
        p.style=Paint.Style.STROKE;p.strokeWidth=width;p.strokeJoin=Paint.Join.ROUND;p.strokeCap=Paint.Cap.ROUND;p.color=color
        p.pathEffect=if(dashed) DashPathEffect(floatArrayOf(4*dn,3*dn),0f) else null
        cv.drawPath(path,p);p.pathEffect=null;p.style=Paint.Style.FILL
    }
    right.firstOrNull { it.key=="prob" }?.let { line(prob,it,probColor,true,1.3f*dn) }
    left.firstOrNull { it.key=="wind" }?.let { axis ->
        if("gust" in show) line(gust,axis,windColor,true,1.4f*dn)
        if("wind" in show) line(wind,axis,windColor,false,1.6f*dn)
    }
    left.firstOrNull { it.key=="temp" }?.let { line(temps,it,tempColor,false,2.2f*dn) }

    // Value axes: numbers in the colour of the quantity, its icon on top.
    p.style=Paint.Style.FILL;p.textSize=9*dn
    fun axisColumn(a:MeteoAxis,edge:Float,centre:Float,align:Paint.Align) {
        p.color=a.color;p.textAlign=align
        for(k in 0..4 step stride) cv.drawText(weatherUnitNumber(a.lo+a.step*k,a.decimals)+a.suffix,edge,bottom-plotH*k/4+3.2f*dn,p)
        cv.drawBitmap(widgetGlyphBitmap(a.key,a.color,glyph),centre-glyph/2f,(headH-glyph)/2f,null)
    }
    left.forEachIndexed { j,a -> axisColumn(a,plotL-3*dn-j*axisW,plotL-j*axisW-axisW/2,Paint.Align.RIGHT) }
    right.forEachIndexed { j,a -> axisColumn(a,plotR+3*dn+j*axisW,plotR+j*axisW+axisW/2,Paint.Align.LEFT) }

    // Time axis: hours, and the day in place of midnight.
    val dayName=DateTimeFormatter.ofPattern("EEE",appLocale());val hour=DateTimeFormatter.ofPattern("HH")
    p.textAlign=Paint.Align.LEFT
    var lastEnd=-1000f
    s.forEachIndexed { i,h ->
        if(h.time.hour%every==0) {
            val text=if(h.time.hour==0) h.time.format(dayName) else h.time.format(hour)
            val tw=p.measureText(text)
            val at=(x(i)-tw/2).coerceIn(plotL,(plotR-tw).coerceAtLeast(plotL))
            if(at>=lastEnd+3*dn) { p.color=if(h.time.hour==0) strong else muted;cv.drawText(text,at,hPx-3*dn,p);lastEnd=at+tw }
            if(!small) cv.drawBitmap(widgetWeatherBitmap(h.code,isDay(h.time),iconPx),(x(i)-iconPx/2f).coerceIn(plotL,(plotR-iconPx).coerceAtLeast(plotL)),1*dn,null)
        }
    }
    return bmp
}
