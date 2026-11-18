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
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import android.util.TypedValue
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
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/** Per-widget settings; loc is "active" or a BackgroundRefresh.locationKey. */
internal data class WidgetConfig(val loc:String,val params:List<String>,val extra:String,val hours:Int,val bg:String,val text:String,val meteo:List<String>)

internal object PerunWidgets {
    private const val Prefs="perun_widgets"
    private const val Work="perun-widgets"
    const val ActionRefresh="sk.perun.weather.WIDGET_REFRESH"
    /** Width from which the Current conditions and 6 days widgets show the fourth value; about five cells. */
    const val WideDp=320
    val Kinds:List<Class<*>> = listOf(CompactWidget::class.java,NowWidget::class.java,MeteogramWidget::class.java)
    /** Every widget of the app, for the settings page. The last two are drawn by their own workers. */
    val Listed:List<Class<*>> = listOf(CompactWidget::class.java,NowWidget::class.java,MeteogramWidget::class.java,SixDayWidget::class.java,DayHoursWidget::class.java)
    val MeteoOptions=listOf("temp","precip","prob","cloud","wind","gust")
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
    fun refreshAll(c:Context) { refresh(c,true);SixDayWidgets.refresh(c,true);DayWidgets.refresh(c,true) }
    fun pin(c:Context,kind:Class<*>):Boolean {
        val m=AppWidgetManager.getInstance(c)
        return m.isRequestPinAppWidgetSupported && m.requestPinAppWidget(ComponentName(c,kind),null,null)
    }
    fun kindName(kind:Class<*>):String = uiText(when(kind) { CompactWidget::class.java->R.string.wg_name_compact;NowWidget::class.java->R.string.wg_name_now;SixDayWidget::class.java->R.string.wg_name_days;DayHoursWidget::class.java->R.string.wg_name_day;else->R.string.wg_name_meteo })
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
                val views=try { WidgetRender.render(c,kind,id,cfg,l,data.first,data.second,units,manager.getAppWidgetOptions(id)) }
                    catch(e:CancellationException) { throw e } catch(e:Exception) { null }
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
    private val Hh:DateTimeFormatter=DateTimeFormatter.ofPattern("HH")
    private val DayFormat:DateTimeFormatter=DateTimeFormatter.ofPattern(appPattern("EEE d."),appLocale())
    private val CellIcon=intArrayOf(R.id.wg_c0i,R.id.wg_c1i,R.id.wg_c2i,R.id.wg_c3i)
    private val CellValue=intArrayOf(R.id.wg_c0v,R.id.wg_c1v,R.id.wg_c2v,R.id.wg_c3v)
    private val HourTime=intArrayOf(R.id.wg_h0t,R.id.wg_h1t,R.id.wg_h2t,R.id.wg_h3t,R.id.wg_h4t,R.id.wg_h5t)
    private val HourIcon=intArrayOf(R.id.wg_h0i,R.id.wg_h1i,R.id.wg_h2i,R.id.wg_h3i,R.id.wg_h4i,R.id.wg_h5i)
    private val HourTemp=intArrayOf(R.id.wg_h0v,R.id.wg_h1v,R.id.wg_h2v,R.id.wg_h3v,R.id.wg_h4v,R.id.wg_h5v)

    fun render(c:Context,kind:Class<*>,id:Int,w:WidgetConfig,l:Location,d:OverviewData,ex:OverviewExtra?,u:WeatherUnits,o:Bundle):RemoteViews {
        val pal=if(w.text=="dark") DarkText else LightText
        val v=when(kind) {
            CompactWidget::class.java->compact(c,w,l,d,ex,u,pal,o)
            NowWidget::class.java->now(c,w,l,d,ex,u,pal,o)
            else->meteo(c,w,l,d,u,o,pal)
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
    /** Very small: icon and temperature only; the place and the chosen value appear as the widget grows. */
    private fun compact(c:Context,w:WidgetConfig,l:Location,d:OverviewData,ex:OverviewExtra?,u:WeatherUnits,pal:Palette,o:Bundle):RemoteViews {
        val wDp=o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it>0 } ?: 180
        val hDp=o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it>0 } ?: 110
        val tiny=wDp<120
        val v=RemoteViews(c.packageName,if(tiny) R.layout.wg_compact_tiny else R.layout.wg_compact)
        v.setImageViewBitmap(R.id.wg_icon,widgetWeatherBitmap(d.code,d.isDayFlag,144))
        v.setTextViewText(R.id.wg_temp,t(u,d.temperature))
        paint(v,pal.fg,R.id.wg_temp)
        if(tiny) return v
        val roomy=hDp>=100;val big=hDp>=130 && wDp>=170
        val showPlace=hDp>=72
        val showExtra=roomy && w.extra!="none" && w.extra in Notifier.CurrentOptions
        v.setTextViewTextSize(R.id.wg_temp,TypedValue.COMPLEX_UNIT_SP,if(big) 40f else if(roomy) 32f else 26f)
        val small=if(big) 16f else if(roomy) 14f else 13f
        v.setTextViewTextSize(R.id.wg_place,TypedValue.COMPLEX_UNIT_SP,small)
        v.setTextViewTextSize(R.id.wg_extra,TypedValue.COMPLEX_UNIT_SP,small)
        v.setViewVisibility(R.id.wg_place,if(showPlace) View.VISIBLE else View.GONE)
        v.setTextViewText(R.id.wg_place,l.name)
        v.setViewVisibility(R.id.wg_extra,if(showExtra) View.VISIBLE else View.GONE)
        if(showExtra) v.setTextViewText(R.id.wg_extra,param(w.extra,d,ex,u))
        paint(v,pal.fg,R.id.wg_extra);paint(v,pal.muted,R.id.wg_place)
        return v
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
    private fun meteo(c:Context,w:WidgetConfig,l:Location,d:OverviewData,u:WeatherUnits,o:Bundle,pal:Palette):RemoteViews {
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
        val ch=((hDp-54)*dn).roundToInt().coerceIn((80*dn).roundToInt(),(420*dn).roundToInt())
        v.setImageViewBitmap(R.id.wg_chart,chart(c,d,u,w,pal,cw,ch))
        return v
    }
    /** Meteogram rows from top: day names, icons, cloud band, plot (temperature, wind, probability, precipitation), day-night strip, hours. */
    private fun chart(c:Context,d:OverviewData,u:WeatherUnits,w:WidgetConfig,pal:Palette,wPx:Int,hPx:Int):Bitmap {
        val bmp=Bitmap.createBitmap(wPx,hPx,Bitmap.Config.ARGB_8888);val cv=Canvas(bmp)
        val s=d.series.take(if(w.hours==48) 48 else 24)
        if(s.size<2) return bmp
        val show=w.meteo
        val dn=c.resources.displayMetrics.density
        val iconPx=(18*dn).roundToInt()
        val dayBase=10*dn;val iconTop=dayBase+3*dn;val cloudTop=iconTop+iconPx+3*dn
        val top=cloudTop+(if("cloud" in show) 7*dn else 2*dn)
        val sunH=4*dn;val bottom=hPx-20*dn;val bandTop=bottom+3*dn
        val left=4*dn;val right=wPx-4*dn
        val plotH=max(bottom-top,10f)
        val step=(right-left)/(s.size-1)
        fun x(i:Int):Float = left+step*i
        val p=Paint(Paint.ANTI_ALIAS_FLAG)
        p.style=Paint.Style.FILL
        class Tag(val key:String,val color:Int,val text:String,val cx:Float,val cy:Float)
        val tags=ArrayList<Tag>();val used=ArrayList<android.graphics.RectF>();val busy=ArrayList<android.graphics.RectF>()
        val t0=s[0].time.atZone(d.zone).toInstant()
        fun xt(t:Instant):Float = left+step*(Duration.between(t0,t).seconds/3600f)
        p.color=if(pal.dark) 0xFF5C6B8A.toInt() else 0xFF2E3A57.toInt()
        cv.drawRoundRect(left,bandTop,right,bandTop+sunH,sunH/2,sunH/2,p)
        p.color=0xFFFFC94D.toInt()
        if(d.sunTimes.isEmpty()) s.forEachIndexed { i,h -> if(h.time.hour in 7..19) cv.drawRect(x(i)-step/2,bandTop,x(i)+step/2,bandTop+sunH,p) }
        for((rise,set) in d.sunTimes) { val a=xt(rise).coerceIn(left,right);val b=xt(set).coerceIn(left,right);if(b-a>1f) cv.drawRoundRect(a,bandTop,b,bandTop+sunH,sunH/2,sunH/2,p) }
        val today=d.time.toLocalDate()
        fun dayName(date:LocalDate):String = when(ChronoUnit.DAYS.between(today,date)) { 0L->uiText(R.string.wg_today);1L->uiText(R.string.wg_tomorrow);else->date.format(DayFormat) }
        val starts=s.indices.filter { it==0 || s[it].time.hour==0 }
        p.textSize=10*dn;p.textAlign=Paint.Align.LEFT
        starts.forEachIndexed { k,i ->
            if(i>0) { p.color=pal.grid;cv.drawRect(x(i)-0.5f*dn,iconTop,x(i)+0.5f*dn,bandTop+sunH,p) }
            val label=dayName(s[i].time.toLocalDate());val lw=p.measureText(label)
            val next=starts.getOrNull(k+1)
            if(next==null || x(next)-x(i)>lw+8*dn) { p.color=pal.muted;cv.drawText(label,(x(i)+3*dn).coerceAtMost(right-lw),dayBase,p) }
        }
        if("cloud" in show) s.forEachIndexed { i,h ->
            if(h.cloud.isFinite()) {
                p.color=android.graphics.Color.argb((25+h.cloud*1.9).toInt().coerceIn(0,255),android.graphics.Color.red(pal.cloud),android.graphics.Color.green(pal.cloud),android.graphics.Color.blue(pal.cloud))
                cv.drawRect(x(i)-step/2,cloudTop,x(i)+step/2,cloudTop+4*dn,p)
            }
        }
        if("precip" in show) {
            val maxP=max(2.0,s.maxOf { if(it.precipitation.isFinite()) it.precipitation else 0.0 });val wet=s.indices.filter { s[it].precipitation.isFinite() }.maxByOrNull { s[it].precipitation };if(wet!=null && s[wet].precipitation>0.05) tags+=Tag("rain",0xFF4FC3F7.toInt(),weatherUnitNumber(u.precipitation.fromMillimetres(s[wet].precipitation),1)+" "+u.precipitation.liquidSymbol,x(wet),bottom-(s[wet].precipitation/maxP*plotH*0.4).toFloat()-13*dn)
            s.forEachIndexed { i,h ->
                val pr=if(h.precipitation.isFinite()) h.precipitation else 0.0
                if(pr>0.05) {
                    val bar=(pr/maxP*plotH*0.4).toFloat()
                    val prob=if(h.probability.isFinite()) h.probability else 100.0
                    p.color=android.graphics.Color.argb((90+prob*1.6).toInt().coerceIn(0,255),79,195,247)
                    cv.drawRect(x(i)-step*0.35f,bottom-bar,x(i)+step*0.35f,bottom,p);busy+=android.graphics.RectF(x(i)-step*0.35f,bottom-bar,x(i)+step*0.35f,bottom)
                }
            }
        }
        fun line(values:List<Double>,y:(Double)->Float,color:Int,dashed:Boolean,width:Float) {
            values.indices.forEach { i -> val v=values[i];if(v.isFinite()) { val n=values.getOrNull(i+1)?.takeIf { it.isFinite() } ?: v;busy+=android.graphics.RectF(x(i)-1*dn,kotlin.math.min(y(v),y(n))-0.5f*dn,x(i)+step+1*dn,max(y(v),y(n))+0.5f*dn) } }
            val path=Path();var started=false
            values.forEachIndexed { i,v -> if(v.isFinite()) { if(!started) { path.moveTo(x(i),y(v));started=true } else path.lineTo(x(i),y(v)) } }
            p.style=Paint.Style.STROKE;p.strokeWidth=width;p.strokeJoin=Paint.Join.ROUND;p.strokeCap=Paint.Cap.ROUND;p.color=color
            p.pathEffect=if(dashed) DashPathEffect(floatArrayOf(4*dn,3*dn),0f) else null
            cv.drawPath(path,p);p.pathEffect=null;p.style=Paint.Style.FILL
        }
        if("prob" in show) { line(s.map { it.probability },{ v -> bottom-(v/100.0*plotH*0.9).toFloat() },0xCC4FC3F7.toInt(),true,1.3f*dn);val likely=s.indices.filter { s[it].probability.isFinite() }.maxByOrNull { s[it].probability };if(likely!=null && s[likely].probability>0.0) tags+=Tag("umbrella",0xFF4FC3F7.toInt(),weatherUnitNumber(s[likely].probability,0)+" %",x(likely),bottom-(s[likely].probability/100.0*plotH*0.9).toFloat()-13*dn) }
        val windKeys=listOf("wind","gust").filter { it in show }
        if(windKeys.isNotEmpty()) {
            fun windSeries(k:String):List<Double> = s.map { u.wind.fromKmh(if(k=="wind") it.wind else it.gust) }
            val all=windKeys.flatMap { windSeries(it) }.filter { it.isFinite() }
            if(all.isNotEmpty()) {
                val top3=all.maxOf { it };val hi=max(top3,u.wind.fromKmh(20.0))
                val yw={ v:Double -> bottom-(v/hi*plotH*0.55).toFloat() }
                windKeys.forEach { k -> line(windSeries(k),yw,0xFF80CBC4.toInt(),k=="gust",1.6f*dn) }
                windKeys.forEach { k -> val ws=windSeries(k);val wi=ws.indices.filter { ws[it].isFinite() }.maxByOrNull { ws[it] };if(wi!=null) tags+=Tag(k,0xFF80CBC4.toInt(),weatherUnitNumber(ws[wi],0)+" "+u.wind.symbol,x(wi),yw(ws[wi])-13*dn) }
            }
        }
        if("temp" in show) {
            val temps=s.map { u.temperature.fromCelsius(it.temperature) }
            val valid=temps.indices.filter { temps[it].isFinite() }
            if(valid.isNotEmpty()) {
                val lo=valid.minOf { temps[it] };val hi=max(valid.maxOf { temps[it] },lo+1.0)
                val y={ v:Double -> top+18*dn+((plotH-18*dn)*0.58*(1.0-(v-lo)/(hi-lo))).toFloat() }
                line(temps,y,0xFFEF5350.toInt(),false,2.2f*dn)
                p.textSize=11*dn;p.textAlign=Paint.Align.CENTER;p.color=0xFFEF5350.toInt()
                var iconDone=false
                fun mark(i:Int,above:Boolean) {
                    val text=weatherUnitNumber(temps[i],0)+"°";val tw=p.measureText(text);val lead=above && !iconDone
                    val tx=x(i).coerceIn(left+(if(lead) 22*dn else 10*dn),right-10*dn);val ty=if(above) y(temps[i])-5*dn else y(temps[i])+14*dn
                    val box=android.graphics.RectF(tx-tw/2-(if(lead) 12*dn else 0f),ty-11*dn,tx+tw/2,ty+2*dn)
                    if(used.any { android.graphics.RectF.intersects(it,box) }) return
                    cv.drawText(text,tx,ty,p)
                    if(lead) { cv.drawBitmap(widgetGlyphBitmap("temp",0xFFEF5350.toInt(),(11*dn).roundToInt()),tx-tw/2-12*dn,ty-10*dn,null);iconDone=true }
                    used+=box
                }
                valid.groupBy { s[it].time.toLocalDate() }.values.filter { it.size>=3 }.forEach { day ->
                    val hiI=day.maxBy { temps[it] };val loI=day.minBy { temps[it] }
                    mark(hiI,true);if(loI!=hiI) mark(loI,false)
                }
            }
        }
        val gs=(10*dn).roundToInt()
        p.style=Paint.Style.FILL;p.textSize=9*dn;p.textAlign=Paint.Align.LEFT
        for(tg in tags) {
            val bw=gs+2*dn+p.measureText(tg.text);val bh=12*dn
            val cands=ArrayList<android.graphics.RectF>()
            for(kx in listOf(0f,-0.8f,0.8f,-1.6f,1.6f)) for(ky in -8..8) { val x0=(tg.cx-bw/2+kx*bw).coerceIn(left,(right-bw).coerceAtLeast(left));val y0=(tg.cy+ky*bh/2).coerceIn(top,(bottom-bh).coerceAtLeast(top));cands+=android.graphics.RectF(x0,y0,x0+bw,y0+bh) }
            cands.sortBy { kotlin.math.abs(it.left-(tg.cx-bw/2))+kotlin.math.abs(it.top-tg.cy) }
            fun clear(r:android.graphics.RectF,among:List<android.graphics.RectF>):Boolean = among.none { android.graphics.RectF.intersects(it,r) }
            val spot=cands.firstOrNull { clear(it,used) && clear(it,busy) } ?: cands.firstOrNull { clear(it,used) } ?: continue
            used+=spot
            cv.drawBitmap(widgetGlyphBitmap(tg.key,tg.color,gs),spot.left,spot.top+1*dn,null)
            p.color=tg.color;cv.drawText(tg.text,spot.left+gs+2*dn,spot.top+10*dn,p)
        }
        val every=if(s.size>30) 6 else 3
        p.style=Paint.Style.FILL;p.textSize=10*dn;p.textAlign=Paint.Align.CENTER
        s.forEachIndexed { i,h ->
            if(h.time.hour%every==0) {
                p.color=pal.muted
                cv.drawText(h.time.format(Hh),x(i).coerceIn(left+7*dn,right-7*dn),hPx-3*dn,p)
                cv.drawBitmap(widgetWeatherBitmap(h.code,dayAt(d,h.time),iconPx),(x(i)-iconPx/2f).coerceIn(0f,(wPx-iconPx).toFloat()),iconTop,null)
            }
        }
        return bmp
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
    "cloud"->LongMetric.Cloud.label;"wind"->uiText(R.string.nt_metric_wind);else->uiText(R.string.nt_metric_gust)
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
        // The Day by hours widget keeps its own settings; they open from the home screen.
        val ownSettings=kind==DayHoursWidget::class.java
        val locKey=if(ownSettings) DayWidgets.loc(c,id) else cfg.loc
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
