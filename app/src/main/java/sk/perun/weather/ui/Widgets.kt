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
import android.view.View
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import sk.perun.weather.ui.theme.PerunTheme
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

/** Per-widget settings; loc is "active" or a BackgroundRefresh.locationKey. */
internal data class WidgetConfig(val loc:String,val params:List<String>,val extra:String,val hours:Int,val bg:String,val text:String,val meteo:List<String>)

internal object PerunWidgets {
    private const val Prefs="perun_widgets"
    private const val Work="perun-widgets"
    const val ActionRefresh="sk.perun.weather.WIDGET_REFRESH"
    /** Width from which the Next 6 hours and 6 days widgets show the fourth value; about five cells. */
    const val WideDp=320
    val Kinds:List<Class<*>> = listOf(CompactWidget::class.java,NowWidget::class.java,MeteogramWidget::class.java)
    /** Every widget of the app, for the settings page. The last three are drawn by their own workers. */
    val Listed:List<Class<*>> = listOf(CompactWidget::class.java,NowWidget::class.java,MeteogramWidget::class.java,SixDayWidget::class.java,DayHoursWidget::class.java,AirWidget::class.java)
    val MeteoOptions=listOf("temp","feels","dew","precip","prob","humidity","cloud","wind","gust","pressure","uv","visibility")
    val Backgrounds=listOf("dark","glass","light")
    private fun prefs(c:Context) = c.applicationContext.getSharedPreferences(Prefs,Context.MODE_PRIVATE)
    fun read(c:Context,id:Int):WidgetConfig {
        val p=prefs(c)
        val params=(p.getString("params_$id","") ?: "").split(",").filter { it in Notifier.CurrentOptions }.distinct().take(4)
        val padded=(params+listOf("wind","rain","uv","humidity").filter { it !in params }).take(4)
        val meteo=(p.getString("meteo_$id","temp,precip,cloud") ?: "").split(",").filter { it in MeteoOptions }
        val bg=(p.getString("bg_$id",null) ?: if(p.getBoolean("glass_$id",false)) "glass" else "dark").takeIf { it in Backgrounds } ?: "dark"
        val text=if(p.getString("text_$id","light")=="dark") "dark" else "light"
        return WidgetConfig(p.getString("loc_$id","active") ?: "active",padded,p.getString("extra_$id","none") ?: "none",p.getInt("hours_$id",24),bg,text,meteo)
    }
    fun write(c:Context,id:Int,w:WidgetConfig) {
        prefs(c).edit().putString("loc_$id",w.loc).putString("params_$id",w.params.joinToString(",")).putString("extra_$id",w.extra).putInt("hours_$id",w.hours)
            .putString("bg_$id",w.bg).putString("text_$id",w.text).putString("meteo_$id",w.meteo.joinToString(",")).remove("glass_$id").apply()
    }
    fun remove(c:Context,ids:IntArray) {
        val e=prefs(c).edit()
        ids.forEach { id -> listOf("loc_","params_","extra_","hours_","glass_","bg_","text_","meteo_").forEach { e.remove(it+id) } }
        e.apply()
    }
    fun ids(c:Context,kind:Class<*>):IntArray = AppWidgetManager.getInstance(c).getAppWidgetIds(ComponentName(c,kind))
    fun any(c:Context):Boolean = Kinds.any { ids(c,it).isNotEmpty() }
    fun count(c:Context):Int = Listed.sumOf { ids(c,it).size }
    /** Re-renders every widget; force bypasses the fresh cache (refresh button). */
    fun refresh(c:Context,force:Boolean=false) {
        if(!any(c)) return
        val request=OneTimeWorkRequestBuilder<WidgetWorker>().setInputData(workDataOf("force" to force))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(c.applicationContext).enqueueUniqueWork(Work,ExistingWorkPolicy.REPLACE,request)
    }
    /** The manual refresh of the settings page: every widget of the app, from the network. */
    fun refreshAll(c:Context) { refresh(c,true);SixDayWidgets.refresh(c,true);DayWidgets.refresh(c,true);AirWidgets.refresh(c,true) }
    fun pin(c:Context,kind:Class<*>):Boolean {
        val m=AppWidgetManager.getInstance(c)
        return m.isRequestPinAppWidgetSupported && m.requestPinAppWidget(ComponentName(c,kind),null,null)
    }
    fun kindName(kind:Class<*>):String = uiText(when(kind) { CompactWidget::class.java->R.string.wg_title_compact;NowWidget::class.java->R.string.wg_title_now;SixDayWidget::class.java->R.string.wg_name_days;DayHoursWidget::class.java->R.string.wg_name_day;AirWidget::class.java->R.string.wg_name_air;else->R.string.wg_title_meteo })
}

abstract class PerunWidgetProvider:AppWidgetProvider() {
    override fun onReceive(context:Context,intent:Intent) { if(intent.action==PerunWidgets.ActionRefresh) PerunWidgets.refresh(context,true) else super.onReceive(context,intent) }
    override fun onUpdate(context:Context,manager:AppWidgetManager,ids:IntArray) { PerunWidgets.refresh(context);BackgroundRefresh.schedule(context) }
    override fun onAppWidgetOptionsChanged(context:Context,manager:AppWidgetManager,id:Int,options:Bundle) { PerunWidgets.refresh(context) }
    override fun onDeleted(context:Context,ids:IntArray) { PerunWidgets.remove(context,ids) }
    override fun onDisabled(context:Context) { BackgroundRefresh.schedule(context) }
}
class CompactWidget:PerunWidgetProvider()
class NowWidget:PerunWidgetProvider()
class MeteogramWidget:PerunWidgetProvider()

internal class WidgetWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        val c=applicationContext;val manager=AppWidgetManager.getInstance(c)
        val store=LocationStore(c);val all=store.load()
        val active=all.getOrNull(store.loadSelected()) ?: all.firstOrNull()
        val sources=LocationSourceStore(c)
        val units=readForecastPreferences(c.getSharedPreferences("perun_forecast",Context.MODE_PRIVATE)).units
        val cache=HashMap<String,Pair<OverviewData,OverviewExtra?>?>()
        val force=inputData.getBoolean("force",false)
        var failed=false
        if(force) NetCache.forceNetwork=true
        try {
            for(kind in PerunWidgets.Kinds) for(id in PerunWidgets.ids(c,kind)) {
                val cfg=PerunWidgets.read(c,id)
                val l=(if(cfg.loc=="active") null else all.firstOrNull { BackgroundRefresh.locationKey(it)==cfg.loc }) ?: active ?: continue
                val key=BackgroundRefresh.locationKey(l)
                val data=if(cache.containsKey(key)) cache[key] else {
                    val loaded=try {
                        val src=sources.read(l)
                        loadOverview(l,src.alertSource,src.weatherModel) to loadOverviewExtra(l,src.weatherModel)
                    } catch(e:CancellationException) { throw e } catch(e:Exception) { null }
                    cache[key]=loaded
                    loaded
                }
                if(data==null) { failed=true;continue }
                val views=try {
                    // Hourly values the overview does not load, only for a meteogram that shows one of them.
                    val more=if(kind==MeteogramWidget::class.java && cfg.meteo.any { it in MeteoExtraKeys }) loadMeteogramExtra(l,sources.read(l).weatherModel,cfg.meteo) else emptyMap()
                    WidgetRender.render(c,kind,id,cfg,l,data.first,data.second,units,manager.getAppWidgetOptions(id),more)
                } catch(e:CancellationException) { throw e } catch(e:Exception) { null }
                if(views==null) { failed=true;continue }
                manager.updateAppWidget(id,views)
            }
        } finally { if(force) NetCache.forceNetwork=false }
        return if(failed && runAttemptCount<2) Result.retry() else Result.success()
    }
}

private class Palette(val fg:Int,val muted:Int,val grid:Int,val cloud:Int,val dark:Boolean)
private val LightText=Palette(0xFFFFFFFF.toInt(),0xFFBBC4D2.toInt(),0x55FFFFFF,0xFFC8D2E1.toInt(),false)
private val DarkText=Palette(0xFF14161A.toInt(),0xFF4B586C.toInt(),0x44000000,0xFF5A6478.toInt(),true)

internal object WidgetRender {
    private val Hm:DateTimeFormatter=DateTimeFormatter.ofPattern("HH:mm")
    private val CellIcon=intArrayOf(R.id.wg_c0i,R.id.wg_c1i,R.id.wg_c2i,R.id.wg_c3i)
    private val CellValue=intArrayOf(R.id.wg_c0v,R.id.wg_c1v,R.id.wg_c2v,R.id.wg_c3v)
    private val HourTime=intArrayOf(R.id.wg_h0t,R.id.wg_h1t,R.id.wg_h2t,R.id.wg_h3t,R.id.wg_h4t,R.id.wg_h5t)
    private val HourIcon=intArrayOf(R.id.wg_h0i,R.id.wg_h1i,R.id.wg_h2i,R.id.wg_h3i,R.id.wg_h4i,R.id.wg_h5i)
    private val HourTemp=intArrayOf(R.id.wg_h0v,R.id.wg_h1v,R.id.wg_h2v,R.id.wg_h3v,R.id.wg_h4v,R.id.wg_h5v)

    fun render(c:Context,kind:Class<*>,id:Int,w:WidgetConfig,l:Location,d:OverviewData,ex:OverviewExtra?,u:WeatherUnits,o:Bundle,more:Map<String,Map<LocalDateTime,Double>> = emptyMap()):RemoteViews {
        val pal=if(w.text=="dark") DarkText else LightText
        val v=when(kind) {
            CompactWidget::class.java->compact(c,w,l,d,ex,u,pal,o)
            NowWidget::class.java->now(c,w,l,d,ex,u,pal,o)
            else->meteo(c,w,l,d,u,o,pal,more)
        }
        v.setInt(R.id.wg_root,"setBackgroundResource",when(w.bg) { "glass"->R.drawable.wg_bg_glass;"light"->R.drawable.wg_bg_light;else->R.drawable.wg_bg })
        v.setOnClickPendingIntent(R.id.wg_root,launch(c,id,if(kind==MeteogramWidget::class.java) "forecast" else "overview",BackgroundRefresh.locationKey(l)))
        v.setImageViewResource(R.id.wg_refresh,R.drawable.ic_wg_refresh)
        v.setInt(R.id.wg_refresh,"setColorFilter",pal.muted)
        v.setOnClickPendingIntent(R.id.wg_refresh,PendingIntent.getBroadcast(c,20000+id,Intent(c,kind).setAction(PerunWidgets.ActionRefresh),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        return v
    }
    private fun launch(c:Context,id:Int,route:String,loc:String):PendingIntent {
        val i=(c.packageManager.getLaunchIntentForPackage(c.packageName) ?: Intent()).apply {
            data=android.net.Uri.parse("perun://widget/"+id)
            putExtra("perun_route",route);putExtra("perun_location",loc)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntent.getActivity(c,10000+id,i,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    private fun paint(v:RemoteViews,color:Int,vararg ids:Int) { ids.forEach { v.setTextColor(it,color) } }
    private fun num(x:Double,dec:Int):String = if(x.isFinite()) weatherUnitNumber(x,dec) else "-"
    private fun t(u:WeatherUnits,x:Double):String = num(u.temperature.fromCelsius(x),0)+"°"
    private fun dayAt(d:OverviewData,time:LocalDateTime):Boolean {
        if(d.sunTimes.isEmpty()) return time.hour in 7..19
        val at=time.atZone(d.zone).toInstant()
        return d.sunTimes.any { at>=it.first && at<it.second }
    }
    private fun param(key:String,d:OverviewData,ex:OverviewExtra?,u:WeatherUnits):String = when(key) {
        "wind"->num(u.wind.fromKmh(d.wind),0)+" "+u.wind.symbol
        "gust"->num(u.wind.fromKmh(d.gust),0)+" "+u.wind.symbol
        "rain"->num(u.precipitation.fromMillimetres(d.precipitation),1)+" "+u.precipitation.liquidSymbol
        "prob"->num(d.probability,0)+" %"
        "uv"->num(d.uv,1)
        "humidity"->num(ex?.humidity ?: Double.NaN,0)+" %"
        "pressure"->num(u.pressure.fromHpa(ex?.pressure ?: Double.NaN),0)+" "+u.pressure.symbol
        "cloud"->num(d.cloud,0)+" %"
        "visibility"->num((ex?.visibility ?: Double.NaN)/1000.0,1)+" km"
        "dew"->t(u,ex?.dewPoint ?: Double.NaN)
        "feels"->t(u,d.apparent)
        else->"-"
    }
    /** Strongest official warning as a symbol in the colour of its level; local model estimates are left to the app. */
    private fun alert(v:RemoteViews,d:OverviewData,pal:Palette) {
        val order=listOf("red","orange","yellow")
        val a=d.alerts.filter { !it.local && it.level in order }.minByOrNull { order.indexOf(it.level) }
        if(a==null) { v.setViewVisibility(R.id.wg_alert,View.GONE);return }
        v.setViewVisibility(R.id.wg_alert,View.VISIBLE)
        v.setImageViewBitmap(R.id.wg_alert,widgetAlertBitmap(a.event,a.level,54))
        v.setContentDescription(R.id.wg_alert,a.event)
    }
    private fun snowing(d:OverviewData):Boolean = (d.snowfall.isFinite() && d.snowfall>0.0) || d.code in setOf(71,73,75,77,85,86)
    /**
     * Below 120 dp of width only the icon and the temperature. Otherwise the widget is one picture: a single row at one cell of height,
     * the icon with the values beside it and the place below at two cells of width, and a wider form with the refresh button.
     */
    private fun compact(c:Context,w:WidgetConfig,l:Location,d:OverviewData,ex:OverviewExtra?,u:WeatherUnits,pal:Palette,o:Bundle):RemoteViews {
        val wDp=o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it>0 } ?: 180
        val hDp=o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it>0 } ?: 110
        if(wDp<120) {
            val v=RemoteViews(c.packageName,R.layout.wg_compact_tiny)
            v.setImageViewBitmap(R.id.wg_icon,widgetWeatherBitmap(d.code,d.isDayFlag,144))
            v.setTextViewText(R.id.wg_temp,t(u,d.temperature))
            paint(v,pal.fg,R.id.wg_temp)
            return v
        }
        val v=RemoteViews(c.packageName,R.layout.wg_compact)
        val dn=c.resources.displayMetrics.density
        val row=hDp<100;val wide=!row && wDp>=200
        val order=listOf("red","orange","yellow")
        val a=d.alerts.filter { !it.local && it.level in order }.minByOrNull { order.indexOf(it.level) }
        val extraKey=w.extra.takeIf { it!="none" && it in Notifier.CurrentOptions }
        val temp=t(u,d.temperature)
        v.setImageViewBitmap(R.id.wg_compact_img,compactBitmap(dn,((wDp-16)*dn).roundToInt().coerceIn(40,1400),((hDp-16)*dn).roundToInt().coerceIn(40,900),row,wide,d,l.name,temp,extraKey,extraKey?.let { param(it,d,ex,u) },a?.event,a?.level,pal))
        v.setContentDescription(R.id.wg_compact_img,l.name+" "+temp+(a?.let { ", "+it.event } ?: ""))
        v.setViewVisibility(R.id.wg_refresh,if(wide) View.VISIBLE else View.GONE)
        return v
    }
    private fun compactBitmap(dn:Float,wPx:Int,hPx:Int,row:Boolean,wide:Boolean,d:OverviewData,place:String,temp:String,extraKey:String?,extra:String?,alertEvent:String?,alertLevel:String?,pal:Palette):Bitmap {
        val bmp=Bitmap.createBitmap(wPx,hPx,Bitmap.Config.ARGB_8888);val cv=Canvas(bmp)
        val w=wPx.toFloat();val h=hPx.toFloat()
        val p=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        fun font(size:Float,bold:Boolean) { p.textSize=size*dn;p.typeface=if(bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT }
        fun width(s:String,size:Float,bold:Boolean):Float { font(size,bold);return p.measureText(s) }
        fun text(s:String,x:Float,y:Float,size:Float,color:Int,bold:Boolean=false,align:Paint.Align=Paint.Align.LEFT) { font(size,bold);p.color=color;p.textAlign=align;cv.drawText(s,x,y,p) }
        fun fit(s:String,size:Float,bold:Boolean,room:Float):String {
            font(size,bold)
            if(p.measureText(s)<=room) return s
            var t=s
            while(t.length>1 && p.measureText(t+"\u2026")>room) t=t.dropLast(1)
            return if(p.measureText(t+"\u2026")<=room) t+"\u2026" else ""
        }
        fun pic(b:Bitmap,x:Float,y:Float,size:Float) { p.color=0xFFFFFFFF.toInt();cv.drawBitmap(b,null,RectF(x,y,x+size,y+size),p) }
        fun weather(x:Float,y:Float,size:Float) = pic(widgetWeatherBitmap(d.code,d.isDayFlag,size.roundToInt().coerceAtLeast(1)),x,y,size)
        fun glyph(key:String,x:Float,y:Float,size:Float) = pic(widgetGlyphBitmap(key,pal.fg,size.roundToInt().coerceAtLeast(1)),x,y,size)
        val hasAlert=alertEvent!=null && alertLevel!=null
        fun warn(x:Float,y:Float,size:Float) { if(alertEvent!=null && alertLevel!=null) pic(widgetAlertBitmap(alertEvent,alertLevel,size.roundToInt().coerceAtLeast(1)),x,y,size) }
        if(row) {
            // One row: icon, temperature, place, warning, the chosen value.
            val s=min(h,34*dn);weather(0f,(h-s)/2,s)
            val ts=(h/dn*0.5f).coerceIn(13f,22f)
            val x=s+6*dn+width(temp,ts,true)+8*dn
            text(temp,s+6*dn,h/2+ts*dn*0.36f,ts,pal.fg,true)
            var right=w
            if(extra!=null && extraKey!=null) {
                val ew=width(extra,13f,true)
                text(extra,right,h/2+13*dn*0.36f,13f,pal.fg,true,Paint.Align.RIGHT)
                glyph(extraKey,right-ew-17*dn,h/2-7*dn,14*dn)
                right-=ew+25*dn
            }
            if(hasAlert) { warn(right-18*dn,h/2-9*dn,18*dn);right-=24*dn }
            text(fit(place,13f,false,right-x),x,h/2+13*dn*0.36f,13f,pal.muted)
        } else if(!wide) {
            // Two cells: icon, the temperature and the chosen value beside it, the warning in the corner, the place below.
            warn(w-20*dn,0f,20*dn)
            text(fit(place,12f,false,w),w/2,h-4*dn,12f,pal.muted,false,Paint.Align.CENTER)
            val area=h-20*dn
            val s=min(area,w*0.45f);weather(0f,(area-s)/2,s)
            val x0=s+6*dn;val room=w-x0
            var ts=(area/dn*0.42f).coerceIn(14f,34f)
            while(ts>14f && width(temp,ts,true)>room) ts-=1f
            val top=(area-ts*dn-(if(extra!=null) 19*dn else 0f))/2
            text(temp,x0,top+ts*dn*0.85f,ts,pal.fg,true)
            if(extra!=null && extraKey!=null) {
                val base=top+ts*dn+15*dn
                glyph(extraKey,x0,base-12*dn,13*dn)
                text(fit(extra,13f,false,room-16*dn),x0+16*dn,base,13f,pal.fg)
            }
        } else {
            // Three cells and more: the chosen value (or the weather in words) fills the right side, the warning follows the place.
            val shown=fit(place,13f,false,w-(if(hasAlert) 24*dn else 0f))
            text(shown,0f,h-4*dn,13f,pal.muted)
            warn(width(shown,13f,false)+6*dn,h-19*dn,18*dn)
            val area=h-22*dn
            val s=min(area,w*0.3f);weather(0f,(area-s)/2,s)
            val x0=s+8*dn
            val ts=(area/dn*0.5f).coerceIn(16f,40f)
            text(temp,x0,area/2+ts*dn*0.36f,ts,pal.fg,true)
            // The refresh button of the layout takes the top right corner.
            val right=w-30*dn;val room=right-(x0+width(temp,ts,true)+10*dn)
            if(room>=40*dn) {
                if(extra!=null && extraKey!=null) {
                    text(fit(Notifier.paramLabel(extraKey),10f,false,room),right,area/2-6*dn,10f,pal.muted,false,Paint.Align.RIGHT)
                    val value=fit(extra,17f,true,room-22*dn)
                    text(value,right,area/2+14*dn,17f,pal.fg,true,Paint.Align.RIGHT)
                    glyph(extraKey,right-width(value,17f,true)-21*dn,area/2-1*dn,17*dn)
                } else text(fit(overviewCondition(d.code),13f,false,room),right,area/2+5*dn,13f,pal.fg,false,Paint.Align.RIGHT)
            }
        }
        return bmp
    }
    private fun now(c:Context,w:WidgetConfig,l:Location,d:OverviewData,ex:OverviewExtra?,u:WeatherUnits,pal:Palette,o:Bundle):RemoteViews {
        val v=RemoteViews(c.packageName,R.layout.wg_now)
        v.setImageViewBitmap(R.id.wg_icon,widgetWeatherBitmap(d.code,d.isDayFlag,120))
        v.setTextViewText(R.id.wg_place,l.name)
        v.setTextViewText(R.id.wg_cond,overviewCondition(d.code))
        v.setTextViewText(R.id.wg_temp,t(u,d.temperature))
        // The fourth value needs the room of about five cells.
        val wide=(o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it>0 } ?: 250)>=PerunWidgets.WideDp
        v.setViewVisibility(R.id.wg_c3,if(wide) View.VISIBLE else View.GONE)
        v.setViewVisibility(R.id.wg_c3s,if(wide) View.VISIBLE else View.GONE)
        w.params.take(if(wide) 4 else 3).forEachIndexed { i,key -> v.setImageViewBitmap(CellIcon[i],widgetGlyphBitmap(if(key=="rain" && snowing(d)) "snow" else key,pal.fg,48));v.setTextViewText(CellValue[i],param(key,d,ex,u)) }
        d.hours.take(6).forEachIndexed { i,h ->
            v.setTextViewText(HourTime[i],h.time.format(Hm))
            v.setImageViewBitmap(HourIcon[i],widgetWeatherBitmap(h.code,dayAt(d,h.time),64))
            v.setTextViewText(HourTemp[i],t(u,h.temperature))
        }
        v.setTextViewText(R.id.wg_foot,uiText(R.string.wg_updated,d.time.format(Hm)))
        paint(v,pal.fg,R.id.wg_place,R.id.wg_temp,*CellValue,*HourTemp)
        paint(v,pal.muted,R.id.wg_cond,R.id.wg_foot,*HourTime)
        alert(v,d,pal)
        return v
    }
    /** The chart itself is drawn in MeteogramChart.kt, at the real size of the widget down to two cells. */
    private fun meteo(c:Context,w:WidgetConfig,l:Location,d:OverviewData,u:WeatherUnits,o:Bundle,pal:Palette,more:Map<String,Map<LocalDateTime,Double>>):RemoteViews {
        val v=RemoteViews(c.packageName,R.layout.wg_meteo)
        v.setImageViewBitmap(R.id.wg_icon,widgetWeatherBitmap(d.code,d.isDayFlag,84))
        v.setTextViewText(R.id.wg_place,l.name)
        v.setTextViewText(R.id.wg_cond,overviewCondition(d.code))
        v.setTextViewText(R.id.wg_temp,t(u,d.temperature))
        paint(v,pal.fg,R.id.wg_place,R.id.wg_temp);paint(v,pal.muted,R.id.wg_cond)
        alert(v,d,pal)
        val dn=c.resources.displayMetrics.density
        val wDp=o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it>0 } ?: 300
        val hDp=o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it>0 } ?: 160
        val cw=((wDp-20)*dn).roundToInt().coerceIn(200,1400)
        val ch=((hDp-54)*dn).roundToInt().coerceIn((40*dn).roundToInt(),(420*dn).roundToInt())
        v.setImageViewBitmap(R.id.wg_chart,meteogramBitmap(c,d,u,w.hours,w.meteo,pal.muted,pal.dark,cw,ch,more))
        return v
    }
}

class WidgetConfigActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val id=intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED,Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id))
        if(id==AppWidgetManager.INVALID_APPWIDGET_ID) { finish();return }
        val kind=AppWidgetManager.getInstance(this).getAppWidgetInfo(id)?.provider?.className ?: ""
        val locations=LocationStore(this).load()
        val initial=PerunWidgets.read(this,id)
        setContent {
            PerunTheme {
                Surface(Modifier.fillMaxSize(),color=Color(0xFF151A22),contentColor=Color(0xFFE8EAF0)) {
                    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Text(uiText(R.string.wg_config_title),style=MaterialTheme.typography.titleLarge,color=Color(0xFFE8EAF0))
                        WidgetConfigForm(kind,locations,initial) { cfg ->
                            PerunWidgets.write(this@WidgetConfigActivity,id,cfg)
                            PerunWidgets.refresh(this@WidgetConfigActivity)
                            BackgroundRefresh.schedule(this@WidgetConfigActivity)
                            setResult(RESULT_OK,Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id))
                            finish()
                        }
                    }
                }
            }
        }
    }
}

private fun meteoLabel(key:String):String = when(key) {
    "temp"->uiText(R.string.nt_metric_temp);"precip"->uiText(R.string.nt_metric_precip);"prob"->uiText(R.string.nt_metric_prob)
    "cloud"->LongMetric.Cloud.label;"wind"->uiText(R.string.nt_metric_wind);"gust"->uiText(R.string.nt_metric_gust)
    else->Notifier.paramLabel(key)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun WidgetConfigForm(kind:String,locations:List<Location>,initial:WidgetConfig,onSave:(WidgetConfig)->Unit) {
    var loc by remember { mutableStateOf(initial.loc) }
    var params by remember { mutableStateOf(initial.params) }
    var extra by remember { mutableStateOf(initial.extra) }
    var hours by remember { mutableStateOf(initial.hours) }
    var bg by remember { mutableStateOf(initial.bg) }
    var text by remember { mutableStateOf(initial.text) }
    var meteo by remember { mutableStateOf(initial.meteo) }
    val keys=listOf("active")+locations.map { BackgroundRefresh.locationKey(it) }
    val names=listOf(uiText(R.string.wg_loc_active))+locations.map { it.name }
    val options=Notifier.CurrentOptions
    val labels=options.map { Notifier.paramLabel(it) }
    val muted=Color(0xFFBBC4D2)
    @Composable fun label(res:Int) = Text(uiText(res),style=MaterialTheme.typography.labelLarge,color=muted)
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        label(R.string.wg_location)
        PerunChips(names,keys.indexOf(loc).coerceAtLeast(0),{ loc=keys[it] })
        if(kind.endsWith("NowWidget") || kind.endsWith("SixDayWidget")) {
            for(i in 0..3) {
                Text(uiText(R.string.wg_param_n,i+1),style=MaterialTheme.typography.labelLarge,color=muted)
                PerunChips(labels,options.indexOf(params[i]).coerceAtLeast(0),{ pick ->
                    val next=params.toMutableList();val chosen=options[pick]
                    val other=next.indexOf(chosen);if(other>=0 && other!=i) next[other]=next[i]
                    next[i]=chosen;params=next
                })
            }
            Text(uiText(R.string.wg_param_wide),style=MaterialTheme.typography.bodySmall,color=muted)
        }
        if(kind.endsWith("CompactWidget")) {
            val extras=listOf("none")+options
            label(R.string.wg_extra)
            PerunChips(listOf(uiText(R.string.wg_none))+labels,extras.indexOf(extra).coerceAtLeast(0),{ extra=extras[it] })
        }
        if(kind.endsWith("MeteogramWidget")) {
            val spans=listOf(24,48)
            label(R.string.wg_hours)
            PerunChips(spans.map { uiText(R.string.wg_hours_n,it) },spans.indexOf(hours).coerceAtLeast(0),{ hours=spans[it] })
            label(R.string.wg_meteo_params)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                PerunWidgets.MeteoOptions.forEach { key ->
                    PerunChip(meteoLabel(key),key in meteo) { meteo=if(key in meteo) meteo-key else PerunWidgets.MeteoOptions.filter { it in meteo || it==key } }
                }
            }
        }
        label(R.string.wg_background)
        PerunChips(listOf(uiText(R.string.wg_bg_dark),uiText(R.string.wg_bg_glass),uiText(R.string.wg_bg_light)),PerunWidgets.Backgrounds.indexOf(bg).coerceAtLeast(0),{ bg=PerunWidgets.Backgrounds[it] })
        label(R.string.wg_text)
        PerunChips(listOf(uiText(R.string.wg_text_light),uiText(R.string.wg_text_dark)),if(text=="dark") 1 else 0,{ text=if(it==1) "dark" else "light" })
        Text(uiText(R.string.wg_note),style=MaterialTheme.typography.bodySmall,color=muted)
        Button(onClick={ onSave(WidgetConfig(loc,params,extra,hours,bg,text,meteo)) },modifier=Modifier.fillMaxWidth()) { Text(uiText(R.string.wg_save)) }
    }
}

@Composable internal fun widgetSummary():String {
    val c=LocalContext.current
    val n=rememberOnResume { PerunWidgets.count(c) }
    return if(n==0) uiText(R.string.wg_summary_none) else uiText(R.string.wg_summary_n,n)
}

/** Settings page: placed widgets with their own settings, adding to the home screen and a manual refresh. */
@Composable internal fun WidgetSettingsPane() {
    val c=LocalContext.current
    var tick by remember { mutableStateOf(0) }
    var editing by remember { mutableStateOf<Int?>(null) }
    val resumed=rememberOnResume { System.currentTimeMillis() }
    val placed=remember(tick,resumed) { PerunWidgets.Listed.flatMap { k -> PerunWidgets.ids(c,k).map { k to it } } }
    val locations=remember { LocationStore(c).load() }
    val muted=MaterialTheme.colorScheme.onSurfaceVariant
    if(placed.isEmpty()) Text(uiText(R.string.wg_settings_empty),style=MaterialTheme.typography.bodyMedium,color=muted)
    placed.forEach { (kind,id) ->
        val cfg=PerunWidgets.read(c,id)
        // The Day by hours and the Air and pollen widgets keep their own settings; they open from the home screen.
        val ownSettings=kind==DayHoursWidget::class.java || kind==AirWidget::class.java
        val locKey=when(kind) { DayHoursWidget::class.java->DayWidgets.loc(c,id);AirWidget::class.java->AirWidgets.loc(c,id);else->cfg.loc }
        val place=locations.firstOrNull { BackgroundRefresh.locationKey(it)==locKey }?.name ?: uiText(R.string.wg_loc_active)
        Row(Modifier.fillMaxWidth().heightIn(min=52.dp).clickable { editing=if(editing==id) null else id },verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(PerunWidgets.kindName(kind),style=MaterialTheme.typography.titleSmall)
                Text(place,style=MaterialTheme.typography.bodySmall,color=muted)
            }
            Text(if(editing==id) "‹" else "›",style=MaterialTheme.typography.headlineSmall,color=MaterialTheme.colorScheme.primary)
        }
        if(editing==id) key(id) {
            if(ownSettings) Text(uiText(R.string.wg_day_settings_hint),style=MaterialTheme.typography.bodySmall,color=muted)
            else WidgetConfigForm(kind.name,locations,cfg) { next -> PerunWidgets.write(c,id,next);if(kind==SixDayWidget::class.java) SixDayWidgets.refresh(c,true) else PerunWidgets.refresh(c,true);editing=null;tick++ }
        }
        HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.5f))
    }
    if(placed.isNotEmpty()) OutlinedButton(onClick={ PerunWidgets.refreshAll(c) },modifier=Modifier.fillMaxWidth()) { Text(uiText(R.string.wg_refresh_all)) }
    Text(uiText(R.string.wg_add_title),style=MaterialTheme.typography.labelLarge,color=muted)
    PerunWidgets.Listed.forEach { k -> OutlinedButton(onClick={ PerunWidgets.pin(c,k) },modifier=Modifier.fillMaxWidth()) { Text(PerunWidgets.kindName(k)) } }
    Text(uiText(R.string.wg_add_note),style=MaterialTheme.typography.bodySmall,color=muted)
}
