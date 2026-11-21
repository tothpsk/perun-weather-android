package sk.perun.weather.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
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
/** Lowest value and step of an axis that does not start at zero and may need steps below one, such as pressure. */
internal fun meteoSpanAxis(min:Double,max:Double,minStep:Double,steps:Int=4):Pair<Double,Double> {
    var step=if(max>min) max(meteoNiceStep((max-min)/steps),minStep) else minStep
    var lo=floor(min/step)*step
    var guard=0
    while(lo+step*steps<max-1e-9 && guard++<8) { step=meteoNiceStep(step*1.5);lo=floor(min/step)*step }
    return lo to step
}

/** Quantities of the Meteogram widget that the overview does not load by the hour, with their Open-Meteo names. */
internal val MeteoExtraKeys=linkedMapOf("feels" to "apparent_temperature","dew" to "dew_point_2m","humidity" to "relative_humidity_2m","pressure" to "pressure_msl","uv" to "uv_index","visibility" to "visibility")

/** Hourly values of the extra quantities by local time; loaded only for a meteogram that shows one of them. What the model lacks is taken from best_match. Empty when the request fails. */
internal suspend fun loadMeteogramExtra(l:Location,model:String,keys:List<String>):Map<String,Map<LocalDateTime,Double>> = withContext(Dispatchers.IO) {
    val wanted=keys.filter { it in MeteoExtraKeys }
    if(wanted.isEmpty()) return@withContext emptyMap<String,Map<LocalDateTime,Double>>()
    fun read(url:String):Map<String,Map<LocalDateTime,Double>> {
        val h=JSONObject(String(fetchBytes(url),Charsets.UTF_8)).getJSONObject("hourly")
        val t=h.getJSONArray("time")
        val out=HashMap<String,Map<LocalDateTime,Double>>()
        for((key,name) in MeteoExtraKeys) {
            val a=h.optJSONArray(name) ?: continue
            val m=HashMap<LocalDateTime,Double>()
            for(i in 0 until t.length()) {
                val v=a.optDouble(i,Double.NaN)
                val at=runCatching { LocalDateTime.parse(t.getString(i)) }.getOrNull()
                if(v.isFinite() && at!=null) m[at]=v
            }
            if(m.isNotEmpty()) out[key]=m
        }
        return out
    }
    try {
        val tz=URLEncoder.encode(l.timezone.ifBlank { "auto" },"UTF-8")
        val elevation=l.elevation?.let { "&elevation=$it" } ?: ""
        val base="https://api.open-meteo.com/v1/forecast?latitude=${l.latitude}&longitude=${l.longitude}&timezone=$tz$elevation&forecast_hours=50&hourly="+MeteoExtraKeys.values.joinToString(",")
        val own=read(if(model=="best_match") base else base+"&models="+URLEncoder.encode(model,"UTF-8"))
        if(model!="best_match" && wanted.any { it !in own }) {
            val fallback=try { read(base) } catch(e:CancellationException) { throw e } catch(e:Exception) { emptyMap() }
            fallback+own
        } else own
    } catch(e:CancellationException) { throw e } catch(e:Exception) { emptyMap() }
}

private class MeteoLine(val key:String,val color:Int,val values:List<Double>,val dashed:Boolean,val width:Float)
private class MeteoAxis(val icon:String,val color:Int,val lo:Double,val step:Double,val decimals:Int,val suffix:String,val lines:List<MeteoLine>,val bars:Boolean=false) { var w=0f }

/**
 * The chart of the Meteogram widget. Quantities of the same unit share a value axis; every axis has the colour and the icon
 * of its first quantity. Temperatures and wind are on the left, precipitation and percentages on the right, the remaining
 * axes go to the side with fewer of them. The time axis shows the hours and, at midnight, the day. Nothing is labelled
 * inside the plot.
 */
internal fun meteogramBitmap(c:Context,d:OverviewData,u:WeatherUnits,hours:Int,show:List<String>,muted:Int,dark:Boolean,wPx:Int,hPx:Int,more:Map<String,Map<LocalDateTime,Double>> = emptyMap()):Bitmap {
    val bmp=Bitmap.createBitmap(wPx,hPx,Bitmap.Config.ARGB_8888);val cv=Canvas(bmp)
    val s=d.series.take(if(hours==48) 48 else 24)
    if(s.size<2) return bmp
    val dn=c.resources.displayMetrics.density
    val p=Paint(Paint.ANTI_ALIAS_FLAG)
    val cTemp=0xFFEF5350.toInt();val cFeels=0xFFFFAB91.toInt();val cDew=0xFFAED581.toInt();val cWind=0xFF80CBC4.toInt();val cRain=0xFF4FC3F7.toInt()
    val cProb=0xFFB39DDB.toInt();val cHumid=0xFFF48FB1.toInt();val cPress=0xFFFFD54F.toInt();val cUv=0xFFFFB74D.toInt()
    val strong=if(dark) 0xFF14161A.toInt() else 0xFFFFFFFF.toInt()
    val grid=if(dark) 0x1F000000 else 0x1FFFFFFF
    val midnight=if(dark) 0x40000000 else 0x40FFFFFF
    fun decimalsOf(step:Double):Int = if(step>=1.0) 0 else if(step>=0.1) 1 else 2
    fun isDay(t:LocalDateTime):Boolean {
        if(d.sunTimes.isEmpty()) return t.hour in 7..19
        val at=t.atZone(d.zone).toInstant()
        return d.sunTimes.any { at>=it.first && at<it.second }
    }
    fun extra(key:String,convert:(Double)->Double):List<Double> = s.map { h -> more[key]?.get(h.time)?.let(convert) ?: Double.NaN }
    fun line(key:String,color:Int,values:List<Double>,dashed:Boolean,width:Float):MeteoLine? = if(key in show && values.any { it.isFinite() }) MeteoLine(key,color,values,dashed,width*dn) else null
    fun low(lines:List<MeteoLine>):Double = lines.flatMap { it.values }.filter { it.isFinite() }.minOrNull() ?: 0.0
    fun high(lines:List<MeteoLine>):Double = lines.flatMap { it.values }.filter { it.isFinite() }.maxOrNull() ?: 0.0
    val rain=s.map { u.precipitation.fromMillimetres(it.precipitation) }
    // Inner axes first: temperature and precipitation next to the plot, the others outside them.
    val left=ArrayList<MeteoAxis>();val right=ArrayList<MeteoAxis>()
    val tempLines=listOfNotNull(
        line("temp",cTemp,s.map { u.temperature.fromCelsius(it.temperature) },false,2.2f),
        line("feels",cFeels,extra("feels") { u.temperature.fromCelsius(it) },true,1.5f),
        line("dew",cDew,extra("dew") { u.temperature.fromCelsius(it) },false,1.4f),
    )
    if(tempLines.isNotEmpty()) {
        val axis=meteoAxis(low(tempLines),high(tempLines),false)
        left+=MeteoAxis(tempLines[0].key,tempLines[0].color,axis.first,axis.second,0,"\u00B0",tempLines)
    }
    val windLines=listOfNotNull(line("wind",cWind,s.map { u.wind.fromKmh(it.wind) },false,1.6f),line("gust",cWind,s.map { u.wind.fromKmh(it.gust) },true,1.4f))
    if(windLines.isNotEmpty()) {
        val axis=meteoAxis(0.0,high(windLines),true,4,u.wind.fromKmh(20.0))
        left+=MeteoAxis(windLines[0].key,cWind,axis.first,axis.second,decimalsOf(axis.second),"",windLines)
    }
    if("precip" in show && rain.any { it.isFinite() }) {
        val axis=meteoAxis(0.0,rain.filter { it.isFinite() }.maxOrNull() ?: 0.0,true,4,u.precipitation.fromMillimetres(2.0))
        right+=MeteoAxis("rain",cRain,axis.first,axis.second,decimalsOf(axis.second),"",emptyList(),true)
    }
    val percentLines=listOfNotNull(line("prob",cProb,s.map { it.probability },true,1.3f),line("humidity",cHumid,extra("humidity") { it },false,1.4f))
    if(percentLines.isNotEmpty()) right+=MeteoAxis(percentLines[0].key,percentLines[0].color,0.0,25.0,0,"",percentLines)
    val own=ArrayList<MeteoAxis>()
    line("pressure",cPress,extra("pressure") { u.pressure.fromHpa(it) },false,1.4f)?.let {
        val axis=meteoSpanAxis(low(listOf(it)),high(listOf(it)),meteoNiceStep(u.pressure.fromHpa(1.0)))
        own+=MeteoAxis("pressure",cPress,axis.first,axis.second,decimalsOf(axis.second),"",listOf(it))
    }
    line("uv",cUv,extra("uv") { it },false,1.4f)?.let {
        val axis=meteoAxis(0.0,high(listOf(it)),true,4,4.0)
        own+=MeteoAxis("uv",cUv,axis.first,axis.second,decimalsOf(axis.second),"",listOf(it))
    }
    line("visibility",muted,extra("visibility") { it/1000.0 },false,1.4f)?.let {
        val axis=meteoAxis(0.0,high(listOf(it)),true,4,10.0)
        own+=MeteoAxis("visibility",muted,axis.first,axis.second,decimalsOf(axis.second),"",listOf(it))
    }
    own.forEach { if(left.size<=right.size) left+=it else right+=it }

    // Every axis is as wide as its longest number.
    p.textSize=9*dn
    // The axis icons: 10 dp at two cells. Otherwise as tall as the row of weather icons when the cloud band gives the room below them, a little smaller without it.
    val glyph=((if(hPx<96*dn) 10 else if("cloud" in show) 18 else 16)*dn).roundToInt()
    fun labels(a:MeteoAxis):List<String> = (0..4).map { weatherUnitNumber(a.lo+a.step*it,a.decimals)+a.suffix }
    (left+right).forEach { a -> a.w=max(labels(a).maxOf { p.measureText(it) }+5*dn,glyph+4*dn) }
    val plotL=left.sumOf { it.w.toDouble() }.toFloat()+2*dn
    val plotR=max(wPx-right.sumOf { it.w.toDouble() }.toFloat()-2*dn,plotL+24*dn)
    // At two cells the row of weather icons is left out; the axis icons then stand beside the cloud band.
    val small=hPx<96*dn
    val iconPx=(18*dn).roundToInt()
    val headH=if(small) 0f else iconPx.toFloat()
    val cloudOn="cloud" in show
    val bandH=6*dn
    // The plot starts below the axis icons, so that the top number of an axis does not touch its icon.
    val top=max(headH+(if(cloudOn) bandH+1*dn else 0f)+3*dn,if(small) glyph+4.5f*dn else 0f);val bottom=hPx-13*dn
    // At two cells the band is centred between the place in the header and the plot; the header keeps about 5 dp free below its text.
    val bandTop=if(small) ((top-bandH-5*dn)/2).coerceAtLeast(0f) else headH
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
    // Cloud cover: a faint track that is always there, so that a clear sky still shows the band.
    if(cloudOn) {
        val rgb=if(dark) 0x5A6478 else 0xC8D2E1
        p.color=(0x30 shl 24) or rgb
        cv.drawRect(plotL,bandTop,plotR,bandTop+bandH,p)
        s.forEachIndexed { i,h ->
            if(h.cloud.isFinite()) {
                p.color=((30+h.cloud*2.0).toInt().coerceIn(0,255) shl 24) or rgb
                cv.drawRect((x(i)-step/2).coerceAtLeast(plotL),bandTop,(x(i)+step/2).coerceAtMost(plotR),bandTop+bandH,p)
            }
        }
    }
    right.firstOrNull { it.bars }?.let { axis ->
        s.forEachIndexed { i,h ->
            val v=rain[i]
            if(v.isFinite() && v>0.0) {
                val chance=if(h.probability.isFinite()) h.probability else 100.0
                p.color=android.graphics.Color.argb((90+chance*1.6).toInt().coerceIn(0,255),79,195,247)
                cv.drawRect((x(i)-step*0.35f).coerceAtLeast(plotL),y(axis,v).coerceAtLeast(top),(x(i)+step*0.35f).coerceAtMost(plotR),bottom,p)
            }
        }
    }
    fun draw(l:MeteoLine,axis:MeteoAxis) {
        val path=Path();var started=false
        l.values.forEachIndexed { i,v -> if(v.isFinite()) { if(!started) { path.moveTo(x(i),y(axis,v));started=true } else path.lineTo(x(i),y(axis,v)) } else started=false }
        p.style=Paint.Style.STROKE;p.strokeWidth=l.width;p.strokeJoin=Paint.Join.ROUND;p.strokeCap=Paint.Cap.ROUND;p.color=l.color
        p.pathEffect=if(l.dashed) DashPathEffect(floatArrayOf(4*dn,3*dn),0f) else null
        cv.drawPath(path,p);p.pathEffect=null;p.style=Paint.Style.FILL
    }
    // Outer axes first, so that the temperature ends up on top.
    (right.reversed()+left.reversed()).forEach { axis -> axis.lines.reversed().forEach { draw(it,axis) } }

    // Value axes: numbers in the colour of the quantity, its icon on top.
    p.style=Paint.Style.FILL;p.textSize=9*dn
    fun axisColumn(a:MeteoAxis,inner:Float,dir:Int) {
        p.color=a.color;p.textAlign=if(dir<0) Paint.Align.RIGHT else Paint.Align.LEFT
        val text=labels(a)
        for(k in 0..4 step stride) cv.drawText(text[k],inner+dir*3*dn,bottom-plotH*k/4+3.2f*dn,p)
        cv.drawBitmap(widgetIconBitmap(a.icon,a.color,glyph),inner+dir*a.w/2-glyph/2f,if(small) 0f else (headH-glyph)/2f,null)
    }
    var edge=plotL
    left.forEach { axisColumn(it,edge,-1);edge-=it.w }
    edge=plotR
    right.forEach { axisColumn(it,edge,1);edge+=it.w }

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
            if(!small) cv.drawBitmap(widgetSymbolBitmap(h.code,isDay(h.time),iconPx),(x(i)-iconPx/2f).coerceIn(plotL,(plotR-iconPx).coerceAtLeast(plotL)),0f,null)
        }
    }
    return bmp
}
