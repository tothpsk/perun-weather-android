package sk.perun.weather.ui

import sk.perun.weather.R

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import sk.perun.weather.ui.theme.PerunTheme
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Row is one row of two or three cells; Strip is one row of four cells and more; CompactDetails is the three-cell, two-row layout. */
internal enum class AirWidgetShape { Tiny,Square,Row,Strip,CompactDetails,Full }
internal fun airWidgetShape(wDp:Int,hDp:Int):AirWidgetShape = when {
    wDp<120 -> AirWidgetShape.Tiny
    hDp<100 -> if(wDp<240) AirWidgetShape.Row else AirWidgetShape.Strip
    wDp<180 -> AirWidgetShape.Square
    wDp<250 -> AirWidgetShape.CompactDetails
    else -> AirWidgetShape.Full
}

private class PollenNow(val type:AirPollenType,val value:Double,val level:Int)
private val AqiColors=intArrayOf(0xFF66D17A.toInt(),0xFFB5D64A.toInt(),0xFFFFD166.toInt(),0xFFFB8C00.toInt(),0xFFE53935.toInt(),0xFFAB47BC.toInt())
private val PollenColors=intArrayOf(0xFF66D17A.toInt(),0xFFFFB74D.toInt(),0xFFE53935.toInt())

internal class AirWidgetConfig(val loc:String,val main:String,val bg:String,val text:String)

/** The Air and pollen widget. It keeps its own settings; the main value is "air" or "pollen". */
internal object AirWidgets {
    private const val Prefs="perun_widget_air"
    private const val Once="perun-widget-air"
    private const val Periodic="perun-widget-air-tick"
    private fun prefs(c:Context) = c.applicationContext.getSharedPreferences(Prefs,Context.MODE_PRIVATE)
    fun read(c:Context,id:Int):AirWidgetConfig {
        val p=prefs(c)
        return AirWidgetConfig(p.getString("loc_$id","active") ?: "active",if(p.getString("main_$id","air")=="pollen") "pollen" else "air",
            (p.getString("bg_$id","dark") ?: "dark").takeIf { it in PerunWidgets.Backgrounds } ?: "dark",if(p.getString("text_$id","light")=="dark") "dark" else "light")
    }
    fun write(c:Context,id:Int,w:AirWidgetConfig) { prefs(c).edit().putString("loc_$id",w.loc).putString("main_$id",w.main).putString("bg_$id",w.bg).putString("text_$id",w.text).apply() }
    fun remove(c:Context,ids:IntArray) { val e=prefs(c).edit();ids.forEach { id -> listOf("loc_","main_","bg_","text_").forEach { e.remove(it+id) } };e.apply() }
    fun loc(c:Context,id:Int):String = read(c,id).loc
    fun ids(c:Context):IntArray = AppWidgetManager.getInstance(c).getAppWidgetIds(ComponentName(c,AirWidget::class.java))
    private fun online() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    fun refresh(c:Context,force:Boolean=false) {
        if(ids(c).isEmpty()) return
        val request=OneTimeWorkRequestBuilder<AirWidgetWorker>().setInputData(workDataOf("force" to force)).setConstraints(online()).build()
        WorkManager.getInstance(c.applicationContext).enqueueUniqueWork(Once,ExistingWorkPolicy.REPLACE,request)
    }
    fun schedule(c:Context) {
        val request=PeriodicWorkRequestBuilder<AirWidgetWorker>(60,TimeUnit.MINUTES).setConstraints(online()).build()
        WorkManager.getInstance(c.applicationContext).enqueueUniquePeriodicWork(Periodic,ExistingPeriodicWorkPolicy.KEEP,request)
    }
    fun cancel(c:Context) { WorkManager.getInstance(c.applicationContext).cancelUniqueWork(Periodic) }
    /** Loads and draws one widget; false when the place is missing. A tap opens the Air quality and pollen screen, on the pollen mode for a pollen widget. */
    suspend fun render(c:Context,id:Int,all:List<Location>,active:Location?):Boolean {
        val cfg=read(c,id)
        val l=(if(cfg.loc=="active") null else all.firstOrNull { BackgroundRefresh.locationKey(it)==cfg.loc }) ?: active ?: return false
        val air=loadAirQuality(l)
        val saved=c.getSharedPreferences("perun_air_detail",Context.MODE_PRIVATE).getString("scale","")
        val scale=AirQualityScale.entries.firstOrNull { it.name==saved } ?: AirQualityScale.European
        val manager=AppWidgetManager.getInstance(c);val o=manager.getAppWidgetOptions(id)
        val wDp=o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it>0 } ?: 160
        val hDp=o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it>0 } ?: 160
        val dn=c.resources.displayMetrics.density
        // 10 dp at the sides as in the Meteogram widget; one cell of width keeps 6 dp. Above and below 10 dp at two rows and 6 dp at one row, which is too low for more.
        val padH=if(wDp<120) 6 else 10;val padV=if(hDp<100) 6 else 10
        val wPx=((wDp-2*padH)*dn).roundToInt().coerceIn(40,1400);val hPx=((hDp-2*padV)*dn).roundToInt().coerceIn(40,900)
        val v=RemoteViews(c.packageName,R.layout.wg_air)
        // The warning of the next 24 hours, drawn behind the place where the widget shows it.
        val warning=widgetNextDay().let { (from,to)->widgetAlert(widgetPlaceAlerts(c,l),from,to) }
        val warningPicture=warning?.let { widgetAlertBitmap(it,(18*dn).roundToInt()) }
        v.setImageViewBitmap(R.id.wg_air_img,airWidgetBitmap(dn,l.name,air,scale,cfg.main=="pollen",cfg.text=="dark",wPx,hPx,airWidgetShape(wDp,hDp),warningPicture))
        v.setContentDescription(R.id.wg_air_img,l.name+" \u00B7 "+uiText(R.string.wg_name_air)+(warning?.let { ", "+alertName(it) } ?: ""))
        v.setInt(R.id.wg_root,"setBackgroundResource",when(cfg.bg) { "glass"->R.drawable.wg_bg_glass;"light"->R.drawable.wg_bg_light;else->R.drawable.wg_bg })
        v.setViewPadding(R.id.wg_root,(padH*dn).roundToInt(),(padV*dn).roundToInt(),(padH*dn).roundToInt(),(padV*dn).roundToInt())
        val open=(c.packageManager.getLaunchIntentForPackage(c.packageName) ?: Intent()).apply {
            setData(android.net.Uri.parse("perun://widget/"+id))
            putExtra("perun_route",if(cfg.main=="pollen") "more/air?mode=pollen" else "more/air");putExtra("perun_location",BackgroundRefresh.locationKey(l))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        v.setOnClickPendingIntent(R.id.wg_root,PendingIntent.getActivity(c,10000+id,open,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        manager.updateAppWidget(id,v)
        return true
    }
}

/** The whole widget as one picture, so that every size is laid out to the pixel. */
private fun airWidgetBitmap(dn:Float,place:String,data:AirQualityData,scale:AirQualityScale,pollenMain:Boolean,darkText:Boolean,wPx:Int,hPx:Int,shape:AirWidgetShape,warning:Bitmap?=null):Bitmap {
    val bmp=Bitmap.createBitmap(wPx,hPx,Bitmap.Config.ARGB_8888);val cv=Canvas(bmp)
    val w=wPx.toFloat();val h=hPx.toFloat()
    val fg=if(darkText) 0xFF14161A.toInt() else 0xFFFFFFFF.toInt()
    val muted=if(darkText) 0xFF4B586C.toInt() else 0xFFBBC4D2.toInt()
    val track=(0x33 shl 24) or (fg and 0xFFFFFF)
    val p=Paint(Paint.ANTI_ALIAS_FLAG)
    val nowSeconds=Instant.now().epochSecond
    val i=data.times.indexOfLast { it<=nowSeconds }.coerceAtLeast(0)
    val eu=scale==AirQualityScale.European
    val aqiKey=if(eu) "european_aqi" else "us_aqi"
    val aqi=data.at(aqiKey,i);val level=airQualityLevel(aqi,scale)
    val aqiColor=level?.let { AqiColors[it.band] } ?: muted
    val aqiText=if(aqi.isFinite()) aqi.roundToInt().toString() else "\u2013"
    val aqiFraction=if(aqi.isFinite()) (aqi/(if(eu) 100.0 else 300.0)).coerceIn(0.04,1.0).toFloat() else 0f
    val aqiLine="AQI "+aqiText+(level?.let { " \u00B7 "+it.label } ?: "")
    // Pollen that is in the air now, the strongest first.
    val pollen=AirPollenTypes.map { PollenNow(it,data.at(it.key,i),airPollenLevel(it,data.at(it.key,i))) }.filter { it.value.isFinite() && it.value>0.0 }
        .sortedWith(compareByDescending<PollenNow> { it.level }.thenByDescending { it.value/it.type.high })
    // Every kind of pollen, those in the air first; the kinds without a measurement come last.
    val pollenAll=AirPollenTypes.map { PollenNow(it,data.at(it.key,i),airPollenLevel(it,data.at(it.key,i))) }
        .sortedWith(compareByDescending<PollenNow> { if(it.value.isFinite()) it.level else -2 }.thenByDescending { if(it.value.isFinite()) it.value/it.type.high else -1.0 })
    val top=pollen.firstOrNull()
    val pollenKnown=AirPollenTypes.any { data.at(it.key,i).isFinite() }
    val pollenColor=if(top!=null) PollenColors[top.level] else if(pollenKnown) PollenColors[0] else muted
    val pollenFraction=if(top!=null) (top.level+1)/3f else 0f
    val pollenLabel=if(top!=null) AirPollenLevelNames[top.level] else if(pollenKnown) uiText(R.string.ui_ziadny_pel_d712b212) else "\u2013"
    val pollenLine=if(top!=null) top.type.name+": "+AirPollenLevelNames[top.level] else pollenLabel

    fun font(size:Float,bold:Boolean) { p.style=Paint.Style.FILL;p.textSize=size*dn;p.typeface=if(bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT }
    fun text(s:String,x:Float,y:Float,size:Float,color:Int,bold:Boolean=false,align:Paint.Align=Paint.Align.LEFT) { font(size,bold);p.color=color;p.textAlign=align;cv.drawText(s,x,y,p) }
    fun width(s:String,size:Float,bold:Boolean=false):Float { font(size,bold);return p.measureText(s) }
    fun fit(s:String,size:Float,bold:Boolean,room:Float):String {
        font(size,bold)
        if(p.measureText(s)<=room) return s
        var t=s
        while(t.length>1 && p.measureText(t+"\u2026")>room) t=t.dropLast(1)
        return t+"\u2026"
    }
    // The place with the warning of the next 24 hours behind it, as in the Sun and Moon widget.
    fun placeText(s:String,x:Float,y:Float,size:Float,color:Int,bold:Boolean,room:Float,align:Paint.Align=Paint.Align.LEFT) {
        val a=warning;val sz=(size+3)*dn;val aw=if(a!=null) sz+4*dn else 0f
        val shown=if(room-aw>0f) fit(s,size,bold,room-aw) else "";val tw=if(shown.isEmpty()) 0f else width(shown,size,bold)
        val left=when(align) { Paint.Align.CENTER->x-(tw+aw)/2;Paint.Align.RIGHT->x-tw-aw;else->x }
        if(shown.isNotEmpty()) text(shown,left,y,size,color,bold)
        if(a!=null) { val cy=y-0.36f*size*dn;val ax=if(shown.isNotEmpty()) left+tw+4*dn else left;p.style=Paint.Style.FILL;p.color=0xFFFFFFFF.toInt();cv.drawBitmap(a,null,RectF(ax,cy-sz/2,ax+sz,cy+sz/2),p) }
    }
    fun dot(x:Float,y:Float,color:Int) { p.style=Paint.Style.FILL;p.color=color;cv.drawCircle(x,y,3*dn,p) }
    fun arc(cx:Float,cy:Float,r:Float,stroke:Float,fraction:Float,color:Int) {
        val box=RectF(cx-r,cy-r,cx+r,cy+r)
        p.style=Paint.Style.STROKE;p.strokeWidth=stroke;p.strokeCap=Paint.Cap.ROUND
        p.color=track;cv.drawArc(box,135f,270f,false,p)
        if(fraction>0f) { p.color=color;cv.drawArc(box,135f,270f*fraction,false,p) }
        p.style=Paint.Style.FILL
    }
    // Pollen has no number, so its strength is three rising bars.
    fun bars(cx:Float,cy:Float,size:Float,filled:Int,color:Int) {
        val bw=size/5;val gap=size/8;val x0=cx-(3*bw+2*gap)/2;val base=cy+size/2
        p.style=Paint.Style.FILL
        for(k in 0..2) { p.color=if(k<=filled) color else track;cv.drawRect(x0+k*(bw+gap),base-size*(0.4f+0.3f*k),x0+k*(bw+gap)+bw,base,p) }
    }
    fun gauge(cx:Float,cy:Float,r:Float,caption:String) {
        val stroke=max(3*dn,r*0.16f)
        if(pollenMain) { arc(cx,cy,r-stroke/2,stroke,pollenFraction,pollenColor);bars(cx,cy-r*0.08f,r*0.7f,top?.level ?: -1,pollenColor) }
        else { arc(cx,cy,r-stroke/2,stroke,aqiFraction,aqiColor);val size=r/dn*0.78f;text(aqiText,cx,cy+size*dn*0.33f,size,fg,true,Paint.Align.CENTER) }
        if(caption.isNotEmpty() && r>=16*dn) { val cs=(r/dn*0.3f).coerceIn(7.5f,11f);text(fit(caption,cs,false,r*1.35f),cx,cy+r*0.92f,cs,muted,false,Paint.Align.CENTER) }
    }
    fun pollutant(key:String):Triple<String,String,Int> {
        val v=data.at(key,i);val sub=data.at((if(eu) "european_aqi_" else "us_aqi_")+key,i)
        return Triple(AirPollutants.firstOrNull { it.key==key }?.symbol ?: key,if(v.isFinite()) v.roundToInt().toString() else "\u2013",airQualityLevel(sub,scale)?.let { AqiColors[it.band] } ?: muted)
    }
    // A kind of pollen as a value: its name, the amount and the colour of its level; a dash without a measurement.
    fun pollenItem(x:PollenNow):Triple<String,String,Int> =
        Triple(x.type.name,if(x.value.isFinite()) x.value.roundToInt().toString() else "\u2013",if(x.value.isFinite() && x.level>=0) PollenColors[x.level] else muted)
    // A column: a coloured bar, the name above the value.
    fun column(x:Float,y:Float,room:Float,item:Triple<String,String,Int>) {
        p.style=Paint.Style.FILL;p.color=item.third;cv.drawRect(x,y-8*dn,x+2.5f*dn,y+15*dn,p)
        text(fit(item.first,9f,false,room-8*dn),x+6*dn,y,9f,muted)
        text(fit(item.second,13f,true,room-8*dn),x+6*dn,y+14*dn,13f,fg,true)
    }
    // The width a value takes: its bar and the wider of its name and its value.
    fun columnWidth(item:Triple<String,String,Int>):Float = 6*dn+max(width(item.first,9f),width(item.second,13f,true))
    // Values in one row: the first starts at x0, the last ends at the right edge and the gaps between them are equal.
    // When they do not fit, the room is shared evenly.
    fun spread(x0:Float,y:Float,items:List<Triple<String,String,Int>>) {
        val widths=items.map { columnWidth(it) }
        val gap=if(items.size>1) (w-x0-widths.sum())/(items.size-1) else 0f
        if(items.size<2 || gap<4*dn) { val cw=(w-x0)/items.size;items.forEachIndexed { k,item -> column(x0+k*cw,y,cw,item) };return }
        var x=x0
        items.forEachIndexed { k,item -> column(x,y,widths[k]+3*dn,item);x+=widths[k]+gap }
    }
    val mainWord=if(pollenMain) uiText(R.string.wg_air_pollen) else "AQI"
    when(shape) {
        AirWidgetShape.Tiny -> gauge(w/2,h/2,min(w,h)/2-2*dn,mainWord)
        AirWidgetShape.Square -> {
            placeText(place,w/2,11*dn,11f,muted,false,w,Paint.Align.CENTER)
            // The other value in one line at the bottom.
            val line=fit(if(pollenMain) aqiLine else pollenLine,11f,false,w-10*dn)
            val x0=(w-width(line,11f)-9*dn)/2
            dot(x0+3*dn,h-7*dn,if(pollenMain) aqiColor else pollenColor)
            text(line,x0+9*dn,h-3*dn,11f,fg)
            val areaTop=16*dn;val areaBottom=h-18*dn
            gauge(w/2,(areaTop+areaBottom)/2,max(min(w/2-4*dn,(areaBottom-areaTop)/2),8*dn),if(pollenMain) pollenLabel else level?.label ?: "")
        }
        AirWidgetShape.CompactDetails -> {
            placeText(place,0f,13*dn,13f,fg,true,w)
            val areaTop=22*dn
            val area=(h-areaTop).coerceAtLeast(1f)
            val diameter=min(w*0.46f,(area-4*dn).coerceAtLeast(1f))
            gauge(diameter/2,areaTop+area/2,diameter/2,if(pollenMain) pollenLabel else level?.label ?: mainWord)
            val x0=diameter+12*dn
            val room=(w-x0).coerceAtLeast(1f)
            // Six values in two columns of three. The air quality: PM2.5, PM10 and ozone, then the other pollutants of the Air quality screen.
            // The pollen: all kinds, the strongest first.
            val airKeys=listOf("pm2_5","pm10","ozone").let { first -> first+AirPollutants.map { it.key }.filter { it !in first }.take(3) }
            val items=if(pollenMain) pollenAll.take(6).map { pollenItem(it) } else airKeys.map { pollutant(it) }
            val cw=room/2
            // The right column ends at the right edge.
            val x2=max(x0+cw,w-(items.drop(3).maxOfOrNull { columnWidth(it) } ?: 0f))
            items.forEachIndexed { k,item -> column(if(k<3) x0 else x2,areaTop+area*(((k%3)+0.5f)/3f)-3.5f*dn,if(k<3) cw else w-x2+3*dn,item) }
        }
        AirWidgetShape.Row -> {
            // One row of two or three cells: the gauge on the left; to its right the rating above the place, and at three cells two values.
            val r=h/2-1*dn
            gauge(r+1*dn,h/2,r,"")
            val x0=2*r+8*dn
            val three=w>=160*dn
            val block=if(three) min(72*dn,(w-x0)*0.45f) else (w-x0).coerceAtLeast(1f)
            text(fit(if(pollenMain) pollenLabel else level?.label ?: mainWord,13f,true,block),x0,h/2-2*dn,13f,fg,true)
            placeText(place,x0,h/2+12*dn,10f,muted,false,block)
            if(three) {
                // The air quality: PM2.5 and PM10. The pollen: the two strongest kinds.
                val items=if(pollenMain) pollenAll.take(2).map { pollenItem(it) } else listOf(pollutant("pm2_5"),pollutant("pm10"))
                val start=x0+block+6*dn;val cw=(w-start)/items.size
                if(cw>=26*dn) spread(start,h/2-3*dn,items)
            }
        }
        AirWidgetShape.Strip -> {
            val r=h/2-1*dn
            gauge(r+1*dn,h/2,r,"")
            // A narrow heading leaves room for the values. The pollen shows its strongest kinds: three at four cells and five from five cells.
            // The air quality shows three pollutants at four cells and four from five cells.
            val x0=2*r+9*dn;val block=if(pollenMain) min(72*dn,(w-x0)*0.3f) else min(84*dn,(w-x0)*0.34f)
            text(fit(if(pollenMain) pollenLabel else level?.label ?: mainWord,13f,true,block),x0,h/2-2*dn,13f,fg,true)
            placeText(place,x0,h/2+12*dn,10f,muted,false,block)
            val wide=w>=300*dn
            val items=if(pollenMain) pollenAll.take(if(wide) 5 else 3).map { pollenItem(it) }
                else (if(wide) listOf("pm2_5","pm10","nitrogen_dioxide","ozone") else listOf("pm2_5","pm10","ozone")).map { pollutant(it) }
            val start=x0+block+6*dn
            val shown=items.take(((w-start)/((if(pollenMain) 30 else 36)*dn)).toInt().coerceAtLeast(0))
            if(shown.isNotEmpty()) spread(start,h/2-3*dn,shown)
        }
        AirWidgetShape.Full -> {
            // The header: the place on the left. On the right the air quality index; in the pollen widget the strongest kind of pollen,
            // its grains per cubic metre and its level, chosen as on the Now screen.
            val unit=uiText(R.string.ui_zrn_m3_2736b71a).trim()
            val headLine=if(!pollenMain) aqiLine else if(top!=null) top.value.roundToInt().toString()+" "+unit+" \u00B7 "+AirPollenLevelNames[top.level]
                else if(pollenKnown) "0 "+unit+" \u00B7 "+pollenLabel else pollenLabel
            val summary=fit(headLine,12f,false,w*(if(pollenMain) 0.62f else 0.45f))
            val summaryWidth=width(summary,12f)
            placeText(place,0f,13*dn,13f,fg,true,if(pollenMain) (w-summaryWidth-18*dn).coerceAtLeast(1f) else w*0.5f)
            text(summary,w,13*dn,12f,fg,false,Paint.Align.RIGHT)
            dot(w-summaryWidth-7*dn,9*dn,if(pollenMain) pollenColor else aqiColor)
            // The main value over the next 24 hours; the band grows with the widget.
            val values=h>=108*dn
            val spare=(h-(if(values) 108 else 78)*dn).coerceAtLeast(0f)
            val bandH=min(12*dn+spare*0.6f,40*dn)
            text((if(pollenMain) uiText(R.string.wg_air_pollen) else uiText(R.string.wg_air_quality))+" \u00B7 24 h",0f,30*dn,9f,muted)
            val bandTop=34*dn
            val n=min(24,data.times.size-i)
            if(n>0) {
                val seg=w/n
                for(k in 0 until n) {
                    val color=if(pollenMain) { val worst=AirPollenTypes.maxOf { airPollenLevel(it,data.at(it.key,i+k)) };if(worst<0) track else PollenColors[worst] }
                        else airQualityLevel(data.at(aqiKey,i+k),scale)?.let { AqiColors[it.band] } ?: track
                    p.style=Paint.Style.FILL;p.color=color
                    cv.drawRect(k*seg,bandTop,(k+1)*seg-max(1f,0.5f*dn),bandTop+bandH,p)
                    val hour=Instant.ofEpochSecond(data.times[i+k]).atZone(data.zone).hour
                    if(hour%6==0) text(hour.toString().padStart(2,'0'),((k+0.5f)*seg).coerceIn(6*dn,w-6*dn),bandTop+bandH+11*dn,9f,muted,false,Paint.Align.CENTER)
                }
            }
            val rowY=bandTop+bandH+28*dn
            // The line under the band tells the other value: the pollen widget the air quality index, the air quality widget the pollen.
            if(pollenMain) { dot(3*dn,rowY-4*dn,aqiColor);text(fit(aqiLine,11f,false,w-9*dn),9*dn,rowY,11f,fg) }
            // Every kind of pollen in the air, as many as fit. A place without pollen data has no such line.
            else if(pollenKnown) {
                if(pollen.isEmpty()) text(pollenLabel,0f,rowY,11f,muted) else {
                    var x=0f;var drawn=0
                    for(item in pollen) {
                        val tw=width(item.type.name,11f)
                        if(x+9*dn+tw>w-(if(drawn<pollen.size-1) 22*dn else 0f)) break
                        dot(x+3*dn,rowY-4*dn,PollenColors[item.level]);text(item.type.name,x+9*dn,rowY,11f,fg)
                        x+=9*dn+tw+10*dn;drawn++
                    }
                    if(drawn<pollen.size) text("+"+(pollen.size-drawn),x,rowY,11f,muted)
                }
            }
            if(values) {
                // The bottom row: four values at four cells and six from five cells. The air quality: the pollutants of the Air quality screen.
                // The pollen: its strongest kinds.
                val six=w>=300*dn
                val items=if(pollenMain) pollenAll.take(if(six) 6 else 4).map { pollenItem(it) }
                    else (if(six) AirPollutants.map { it.key } else listOf("pm2_5","pm10","nitrogen_dioxide","ozone")).map { pollutant(it) }
                spread(0f,rowY+16*dn,items)
            }
        }
    }
    return bmp
}

class AirWidget:AppWidgetProvider() {
    override fun onUpdate(context:Context,manager:AppWidgetManager,ids:IntArray) { AirWidgets.refresh(context);AirWidgets.schedule(context) }
    // A new size changes what the widget shows.
    override fun onAppWidgetOptionsChanged(context:Context,manager:AppWidgetManager,id:Int,options:Bundle) { AirWidgets.refresh(context) }
    override fun onDeleted(context:Context,ids:IntArray) { AirWidgets.remove(context,ids) }
    override fun onDisabled(context:Context) { AirWidgets.cancel(context) }
}

internal class AirWidgetWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        val c=applicationContext
        val store=LocationStore(c);val all=store.load()
        val active=all.getOrNull(store.loadSelected()) ?: all.firstOrNull()
        val force=inputData.getBoolean("force",false)
        var failed=false
        if(force) NetCache.forceNetwork=true
        try {
            for(id in AirWidgets.ids(c)) {
                val ok=try { AirWidgets.render(c,id,all,active) } catch(e:CancellationException) { throw e } catch(e:Exception) { false }
                if(!ok) failed=true
            }
        } finally { if(force) NetCache.forceNetwork=false }
        return if(failed && runAttemptCount<2) Result.retry() else Result.success()
    }
}

class AirWidgetConfigActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val id=intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED,Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id))
        if(id==AppWidgetManager.INVALID_APPWIDGET_ID) { finish();return }
        val locations=LocationStore(this).load()
        val initial=AirWidgets.read(this,id)
        val keys=listOf("active")+locations.map { BackgroundRefresh.locationKey(it) }
        val names=listOf(uiText(R.string.wg_loc_active))+locations.map { it.name }
        setContent {
            PerunTheme {
                Surface(Modifier.fillMaxSize(),color=Color(0xFF151A22),contentColor=Color(0xFFE8EAF0)) {
                    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Text(uiText(R.string.wg_config_title),style=MaterialTheme.typography.titleLarge,color=Color(0xFFE8EAF0))
                        AirWidgetForm(locations,initial) { next ->
                            AirWidgets.write(this@AirWidgetConfigActivity,id,next)
                            AirWidgets.refresh(this@AirWidgetConfigActivity)
                            AirWidgets.schedule(this@AirWidgetConfigActivity)
                            setResult(RESULT_OK,Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id))
                            finish()
                        }
                    }
                }
            }
        }
    }
}

/** The settings of the Air and pollen widget; shared by the screen that opens from the home screen and by the Settings of the app. */
@Composable internal fun AirWidgetForm(locations:List<Location>,initial:AirWidgetConfig,onSave:(AirWidgetConfig)->Unit) {
    val keys=listOf("active")+locations.map { BackgroundRefresh.locationKey(it) }
    val names=listOf(uiText(R.string.wg_loc_active))+locations.map { it.name }
    var loc by remember { mutableStateOf(initial.loc) }
    var main by remember { mutableStateOf(initial.main) }
    var bg by remember { mutableStateOf(initial.bg) }
    var text by remember { mutableStateOf(initial.text) }
    val muted=Color(0xFFBBC4D2)
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text(uiText(R.string.wg_location),style=MaterialTheme.typography.labelLarge,color=muted)
        PerunChips(names,keys.indexOf(loc).coerceAtLeast(0),{ loc=keys[it] })
        HorizontalDivider(color=Color(0x804B586C))
        Text(uiText(R.string.wg_air_main),style=MaterialTheme.typography.labelLarge,color=muted)
        PerunChips(listOf(uiText(R.string.wg_air_quality),uiText(R.string.wg_air_pollen)),if(main=="pollen") 1 else 0,{ main=if(it==1) "pollen" else "air" })
        HorizontalDivider(color=Color(0x804B586C))
        Text(uiText(R.string.wg_background),style=MaterialTheme.typography.labelLarge,color=muted)
        PerunChips(listOf(uiText(R.string.wg_bg_dark),uiText(R.string.wg_bg_glass),uiText(R.string.wg_bg_light)),PerunWidgets.Backgrounds.indexOf(bg).coerceAtLeast(0),{ bg=PerunWidgets.Backgrounds[it] })
        HorizontalDivider(color=Color(0x804B586C))
        Text(uiText(R.string.wg_text),style=MaterialTheme.typography.labelLarge,color=muted)
        PerunChips(listOf(uiText(R.string.wg_text_light),uiText(R.string.wg_text_dark)),if(text=="dark") 1 else 0,{ text=if(it==1) "dark" else "light" })
        Button(onClick={ onSave(AirWidgetConfig(loc,main,bg,text)) },modifier=Modifier.fillMaxWidth()) { Text(uiText(R.string.wg_save)) }
    }
}
