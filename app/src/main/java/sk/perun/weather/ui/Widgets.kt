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
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.max
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
    /** Every widget of the app, for the settings page. The last four are drawn by their own workers. */
    val Listed:List<Class<*>> = listOf(CompactWidget::class.java,NowWidget::class.java,MeteogramWidget::class.java,SixDayWidget::class.java,DayHoursWidget::class.java,AirWidget::class.java,SkyWidget::class.java)
    val MeteoOptions=listOf("temp","feels","dew","precip","prob","humidity","cloud","wind","gust","direction","pressure","uv","visibility")
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
    /** The manual refresh of the settings page: every widget of the app, from the network. The Sun and Moon widget is computed in the device. */
    fun refreshAll(c:Context) { refresh(c,true);SixDayWidgets.refresh(c,true);DayWidgets.refresh(c,true);AirWidgets.refresh(c,true);SkyWidgets.refresh(c) }
    fun pin(c:Context,kind:Class<*>):Boolean {
        val m=AppWidgetManager.getInstance(c)
        return m.isRequestPinAppWidgetSupported && m.requestPinAppWidget(ComponentName(c,kind),null,null)
    }
    fun kindName(kind:Class<*>):String = uiText(when(kind) { CompactWidget::class.java->R.string.wg_title_compact;NowWidget::class.java->R.string.wg_title_now;SixDayWidget::class.java->R.string.wg_name_days;DayHoursWidget::class.java->R.string.wg_name_day;AirWidget::class.java->R.string.wg_name_air;SkyWidget::class.java->R.string.wg_name_sky;else->R.string.wg_title_meteo })
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
        // A widget refresh moves the Current location first: precise after the refresh button, otherwise power-saving.
        CurrentLocation.update(c,precise=inputData.getBoolean("force",false),fromBackground=true)
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
                    val more=if(kind==MeteogramWidget::class.java && cfg.meteo.any { it in MeteoExtraKeys || it=="precip" }) loadMeteogramExtra(l,sources.read(l).weatherModel,cfg.meteo) else emptyMap()
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
    private val CellArrow=intArrayOf(R.id.wg_c0a,R.id.wg_c1a,R.id.wg_c2a,R.id.wg_c3a)
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
        v.setOnClickPendingIntent(R.id.wg_root,launch(c,id,"overview",BackgroundRefresh.locationKey(l)))
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
    private fun t(u:WeatherUnits,x:Double):String = num(u.temperature.fromCelsius(x),0)+"\u00b0"
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
    /** The warning of the shown period (widgetAlert) as a symbol in the colour of its level; local model estimates are left to the app. */
    private fun alert(v:RemoteViews,d:OverviewData,hours:Long) {
        val now=Instant.now()
        val a=widgetAlert(d.alerts,now,now.plusSeconds(hours*3600))
        if(a==null) { v.setViewVisibility(R.id.wg_alert,View.GONE);return }
        v.setViewVisibility(R.id.wg_alert,View.VISIBLE)
        v.setImageViewBitmap(R.id.wg_alert,widgetAlertBitmap(a,54))
        v.setContentDescription(R.id.wg_alert,alertName(a))
    }
    private fun snowing(d:OverviewData):Boolean = (d.snowfall.isFinite() && d.snowfall>0.0) || d.code in setOf(71,73,75,77,85,86)
    /**
     * Below 120 dp of width only the icon and the temperature. Otherwise the widget is one picture, laid out by its size in cells:
     * one row or two, and two, three or four and more cells of width. The place is as large as in the Next 6 hours widget and the
     * warning follows it; the refresh button is shown where it fits.
     */
    private fun compact(c:Context,w:WidgetConfig,l:Location,d:OverviewData,ex:OverviewExtra?,u:WeatherUnits,pal:Palette,o:Bundle):RemoteViews {
        val wDp=o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it>0 } ?: 180
        val hDp=o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it>0 } ?: 110
        if(wDp<120) {
            val v=RemoteViews(c.packageName,R.layout.wg_compact_tiny)
            v.setImageViewBitmap(R.id.wg_icon,widgetSymbolBitmap(d.code,d.isDayFlag,144))
            v.setTextViewText(R.id.wg_temp,t(u,d.temperature))
            paint(v,pal.fg,R.id.wg_temp)
            return v
        }
        val row=hDp<100
        val cells=if(wDp<180) 2 else if(wDp<250) 3 else 4
        // The two layouts differ in the place of the refresh button: the middle of the row, or the second row.
        val v=RemoteViews(c.packageName,if(row) R.layout.wg_compact_row else R.layout.wg_compact)
        val dn=c.resources.displayMetrics.density
        // The next 24 hours.
        val a=widgetNextDay().let { (from,to)->widgetAlert(d.alerts,from,to) }
        val extraKey=w.extra.takeIf { it!="none" && it in Notifier.CurrentOptions }
        val temp=t(u,d.temperature)
        val (bmp,refresh)=compactBitmap(dn,((wDp-20)*dn).roundToInt().coerceIn(40,1400),((hDp-(if(row) 16 else 20))*dn).roundToInt().coerceIn(40,900),row,cells,d,l.name,temp,extraKey,extraKey?.let { param(it,d,ex,u)+(if((it=="wind" || it=="gust") && d.direction.isFinite()) "\u2003\u2005" else "") },a,uiText(R.string.wg_updated,d.time.format(Hm)),pal,ex?.pressureChange ?: Double.NaN,gps=l.current)
        v.setImageViewBitmap(R.id.wg_compact_img,bmp)
        v.setContentDescription(R.id.wg_compact_img,l.name+" "+temp+(a?.let { ", "+alertName(it) } ?: ""))
        v.setViewVisibility(R.id.wg_refresh,if(refresh) View.VISIBLE else View.GONE)
        return v
    }
    /** The picture and whether the refresh button of the layout has its room in it. */
    private fun compactBitmap(dn:Float,wPx:Int,hPx:Int,row:Boolean,cells:Int,d:OverviewData,place:String,temp:String,extraKey:String?,extra:String?,alert:UnifiedAlert?,updated:String,pal:Palette,pressureChange:Double=Double.NaN,gps:Boolean=false):Pair<Bitmap,Boolean> {
        val bmp=Bitmap.createBitmap(wPx,hPx,Bitmap.Config.ARGB_8888);val cv=Canvas(bmp)
        val w=wPx.toFloat();val h=hPx.toFloat()
        val p=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        // The arrow of the wind direction; it is drawn into the em space that ends the value of the wind or the gusts.
        val windArrow=if(extraKey=="wind" || extraKey=="gust") widgetWindArrowBitmap(d.direction,pal.fg,64) else null
        fun font(size:Float,bold:Boolean) { p.textSize=size*dn;p.typeface=if(bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT }
        fun width(s:String,size:Float,bold:Boolean):Float { font(size,bold);return p.measureText(s) }
        fun text(s:String,x:Float,y:Float,size:Float,color:Int,bold:Boolean=false,align:Paint.Align=Paint.Align.LEFT) {
            font(size,bold);p.color=color;p.textAlign=align;cv.drawText(s,x,y,p)
            if(windArrow!=null && s.endsWith("\u2003\u2005")) {
                val tw=p.measureText(s);val a=size*dn
                val end=when(align) { Paint.Align.RIGHT->x;Paint.Align.CENTER->x+tw/2;else->x+tw }
                val mid=y-size*dn*0.36f
                val cx=end-p.measureText("\u2003\u2005")+size*dn*0.714f
                cv.drawBitmap(windArrow,null,RectF(cx-a/2,mid-a/2,cx+a/2,mid+a/2),p)
            }
        }
        fun fit(s:String,size:Float,bold:Boolean,room:Float):String {
            font(size,bold)
            if(p.measureText(s)<=room) return s
            var t=s
            while(t.length>1 && p.measureText(t+"\u2026")>room) t=t.dropLast(1)
            return if(p.measureText(t+"\u2026")<=room) t+"\u2026" else ""
        }
        fun pic(b:Bitmap,x:Float,y:Float,size:Float) { p.color=0xFFFFFFFF.toInt();cv.drawBitmap(b,null,RectF(x,y,x+size,y+size),p) }
        fun weather(x:Float,y:Float,size:Float) = pic(widgetSymbolBitmap(d.code,d.isDayFlag,size.roundToInt().coerceAtLeast(1)),x,y,size)
        fun glyph(key:String,x:Float,y:Float,size:Float) = pic(widgetParamIcon(key,pal.fg,size.roundToInt().coerceAtLeast(1),pressureChange,size/dn),x,y,size)
        val hasAlert=alert!=null
        fun warn(x:Float,y:Float,size:Float) { if(alert!=null) pic(widgetAlertBitmap(alert,size.roundToInt().coerceAtLeast(1)),x,y,size) }
        // The place at 15 sp, bold and in the colour of the text, with the 18 dp warning 6 dp behind it, as in the Next 6 hours widget; the warning stands alone when the place is left out.
        // The Current location has the 15 dp location mark of the app 4 dp after the name, before the warning.
        val gpsW=if(gps) 19*dn else 0f
        val alertW=(if(hasAlert) 24*dn else 0f)+gpsW
        fun placedWidth(shown:String):Float = if(shown.isNotEmpty()) width(shown,15f,true)+alertW else if(hasAlert) 18*dn else 0f
        fun placed(shown:String,x:Float,cy:Float) {
            if(shown.isNotEmpty()) text(shown,x,cy+15*dn*0.36f,15f,pal.fg,true)
            if(gps && shown.isNotEmpty()) drawGpsMark(cv,x+width(shown,15f,true)+4*dn,cy,15*dn,pal.fg)
            warn(if(shown.isNotEmpty()) x+width(shown,15f,true)+6*dn+gpsW else x,cy-9*dn,18*dn)
        }
        var refresh=false
        if(row) {
            // One row. Two cells: icon, temperature, warning. Three: the chosen value instead of the place. Four and more: all of them.
            val cy=h/2;val gap=8*dn
            val s=min(h,34*dn)
            val ts=(h/dn*0.5f).coerceIn(13f,22f)
            val wA=s+6*dn+width(temp,ts,true)
            val value=if(cells>=3 && extra!=null && extraKey!=null) extra else null
            val wC=if(value!=null) 17*dn+width(value,13f,true) else 0f
            val showPlace=cells>=4 || (cells==3 && value==null)
            val gaps=gap*(if(wC>0f) 2 else 1)
            refresh=cells>=3 && wA+placedWidth(if(showPlace) place else "")+wC+gaps+30*dn<=w
            val avail=if(refresh) w-30*dn else w
            val shown=if(showPlace) fit(place,15f,true,avail-wA-wC-alertW-gaps) else ""
            val wB=placedWidth(shown)
            // With the chosen value the place stands in the middle between it and the temperature; without it the two groups are spread evenly.
            val xA=(if(wC>0f) 0f else if(wB>0f) (avail-wA-wB)/3 else (avail-wA)/2).coerceAtLeast(0f)
            val xB=if(wC>0f) wA+(avail-wC-wA-wB)/2 else xA+wA+(avail-wA-wB)/3
            weather(xA,(h-s)/2,s)
            text(temp,xA+s+6*dn,cy+ts*dn*0.36f,ts,pal.fg,true)
            placed(shown,xB,cy)
            if(value!=null && extraKey!=null) { glyph(extraKey,avail-wC,cy-7*dn,14*dn);text(value,avail,cy+13*dn*0.36f,13f,pal.fg,true,Paint.Align.RIGHT) }
        } else if(cells<4) {
            // Two rows, two or three cells: the icon, the temperature with the chosen value below it, and the place with the warning in the second row.
            // Two cells are centred; three cells are laid out to the edges, the icon and the place on the left, the values and the refresh button on the right.
            val edges=cells==3
            val s=min(min(h-48*dn,w*0.42f),60*dn).coerceAtLeast(20*dn)
            val es=if(edges) 15f else 13f
            val room=w-s-6*dn
            var ts=(s/dn*0.6f).coerceIn(16f,40f)
            while(ts>14f && width(temp,ts,true)>room) ts-=1f
            val value=if(extra!=null && extraKey!=null) fit(extra,es,false,room-(es+3f)*dn) else ""
            val blockW=max(width(temp,ts,true),if(value.isNotEmpty()) (es+3f)*dn+width(value,es,false) else 0f)
            val blockH=ts*dn+(if(value.isNotEmpty()) (es+4f)*dn else 0f)
            val r1=max(s,blockH)
            refresh=edges && placedWidth(place)+34*dn<=w
            val shown=fit(place,15f,true,w-alertW)
            val xB=if(edges) 0f else (w-placedWidth(shown))/2
            // Three cells keep the second row level with the refresh button; two cells share the height evenly.
            val free=(h-r1-18*dn).coerceAtLeast(0f)
            val top=if(edges) ((h-30*dn-r1)/2).coerceAtLeast(0f) else free/3
            val cy2=if(edges) h-13*dn else top+r1+free/3+9*dn
            val x0=if(edges) 0f else ((w-s-6*dn-blockW)/2).coerceAtLeast(0f)
            weather(x0,top+(r1-s)/2,s)
            val bt=top+(r1-blockH)/2;val tx=x0+s+6*dn
            val valueTop=bt+ts*dn+4*dn
            if(edges) {
                text(temp,w,bt+ts*dn*0.85f,ts,pal.fg,true,Paint.Align.RIGHT)
                if(value.isNotEmpty() && extraKey!=null) { text(value,w,valueTop+es*dn*0.85f,es,pal.fg,false,Paint.Align.RIGHT);glyph(extraKey,w-width(value,es,false)-(es+3f)*dn,valueTop,es*dn) }
            } else {
                text(temp,tx,bt+ts*dn*0.85f,ts,pal.fg,true)
                if(value.isNotEmpty() && extraKey!=null) { glyph(extraKey,tx,valueTop,es*dn);text(value,tx+(es+3f)*dn,valueTop+es*dn*0.85f,es,pal.fg) }
            }
            placed(shown,xB,cy2)
        } else {
            // Two rows, four cells and more: the chosen value (or the weather in words) on the right; the second row has the place with the warning,
            // the time of the update where it fits beside the whole name, and the refresh button.
            refresh=true
            val cy2=h-13*dn
            placed(fit(place,15f,true,w-30*dn-alertW),0f,cy2)
            if(placedWidth(place)+10*dn+width(updated,10f,false)+30*dn<=w) text(updated,w-30*dn,cy2+10*dn*0.36f,10f,pal.muted,false,Paint.Align.RIGHT)
            val area=h-30*dn
            val s=min(area*0.92f,w*0.3f);weather(0f,(area-s)/2,s)
            val x0=s+8*dn
            val ts=(area/dn*0.5f).coerceIn(16f,40f)
            text(temp,x0,area/2+ts*dn*0.36f,ts,pal.fg,true)
            val room=w-(x0+width(temp,ts,true)+10*dn)
            if(room>=40*dn) {
                if(extra!=null && extraKey!=null) {
                    text(fit(Notifier.paramLabel(extraKey),10f,false,room),w,area/2-6*dn,10f,pal.muted,false,Paint.Align.RIGHT)
                    val value=fit(extra,17f,true,room-22*dn)
                    text(value,w,area/2+14*dn,17f,pal.fg,true,Paint.Align.RIGHT)
                    glyph(extraKey,w-width(value,17f,true)-21*dn,area/2-1*dn,17*dn)
                } else text(fit(overviewCondition(d.code),13f,false,room),w,area/2+5*dn,13f,pal.fg,false,Paint.Align.RIGHT)
            }
        }
        return bmp to refresh
    }
    private fun now(c:Context,w:WidgetConfig,l:Location,d:OverviewData,ex:OverviewExtra?,u:WeatherUnits,pal:Palette,o:Bundle):RemoteViews {
        val v=RemoteViews(c.packageName,R.layout.wg_now)
        v.setImageViewBitmap(R.id.wg_icon,widgetSymbolBitmap(d.code,d.isDayFlag,120))
        v.setTextViewText(R.id.wg_place,l.name)
        v.setTextViewText(R.id.wg_cond,overviewCondition(d.code))
        v.setTextViewText(R.id.wg_temp,t(u,d.temperature))
        // The fourth value needs the room of about five cells.
        val wide=(o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it>0 } ?: 250)>=PerunWidgets.WideDp
        v.setViewVisibility(R.id.wg_c3,if(wide) View.VISIBLE else View.GONE)
        v.setViewVisibility(R.id.wg_c3s,if(wide) View.VISIBLE else View.GONE)
        w.params.take(if(wide) 4 else 3).forEachIndexed { i,key -> v.setImageViewBitmap(CellIcon[i],widgetParamIcon(if(key=="rain" && snowing(d)) "snow" else key,pal.fg,48,ex?.pressureChange ?: Double.NaN));v.setTextViewText(CellValue[i],param(key,d,ex,u));widgetWindArrowView(v,CellArrow[i],key,d.direction,pal.fg) }
        d.hours.take(6).forEachIndexed { i,h ->
            v.setTextViewText(HourTime[i],h.time.format(Hm))
            v.setImageViewBitmap(HourIcon[i],widgetSymbolBitmap(h.code,dayAt(d,h.time),64))
            v.setTextViewText(HourTemp[i],t(u,h.temperature))
        }
        v.setTextViewText(R.id.wg_foot,uiText(R.string.wg_updated,d.time.format(Hm)))
        paint(v,pal.fg,R.id.wg_place,R.id.wg_temp,*CellValue,*HourTemp);v.gpsMark(l,pal.fg)
        paint(v,pal.muted,R.id.wg_cond,R.id.wg_foot,*HourTime)
        // The next six hours.
        alert(v,d,6)
        return v
    }
    /** The chart itself is drawn in MeteogramChart.kt, at the real size of the widget down to two cells. */
    private fun meteo(c:Context,w:WidgetConfig,l:Location,d:OverviewData,u:WeatherUnits,o:Bundle,pal:Palette,more:Map<String,Map<LocalDateTime,Double>>):RemoteViews {
        val v=RemoteViews(c.packageName,R.layout.wg_meteo)
        v.setImageViewBitmap(R.id.wg_icon,widgetSymbolBitmap(d.code,d.isDayFlag,84))
        v.setTextViewText(R.id.wg_place,l.name)
        v.setTextViewText(R.id.wg_cond,overviewCondition(d.code))
        v.setTextViewText(R.id.wg_temp,t(u,d.temperature))
        paint(v,pal.fg,R.id.wg_place,R.id.wg_temp);paint(v,pal.muted,R.id.wg_cond);v.gpsMark(l,pal.fg)
        // The hours of the chart.
        alert(v,d,w.hours.toLong())
        val dn=c.resources.displayMetrics.density
        val wDp=o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it>0 } ?: 300
        val hDp=o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it>0 } ?: 160
        val cw=((wDp-20)*dn).roundToInt().coerceIn(200,1400)
        val ch=((hDp-51)*dn).roundToInt().coerceIn((40*dn).roundToInt(),(420*dn).roundToInt())
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
    "direction"->uiText(R.string.wg_param_direction)
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
    val keys=listOf("active")+pickerLocations(locations).map { BackgroundRefresh.locationKey(it) }
    val names=listOf(uiText(R.string.wg_loc_active))+pickerLocations(locations).map { pickerName(it) }
    val options=Notifier.CurrentOptions
    val labels=options.map { Notifier.paramLabel(it) }
    val muted=Color(0xFFBBC4D2)
    // Every section but the first starts with a divider, like the rows of the main list of the settings.
    @Composable fun label(res:Int,first:Boolean=false) { if(!first) HorizontalDivider(color=Color(0x804B586C));Text(uiText(res),style=MaterialTheme.typography.labelLarge,color=muted) }
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        label(R.string.wg_location,true)
        PerunChips(names,keys.indexOf(loc).coerceAtLeast(0),{ loc=keys[it] },marked=keys.indexOf(CurrentLocation.Key))
        if(kind.endsWith("NowWidget") || kind.endsWith("SixDayWidget")) {
            for(i in 0..3) {
                HorizontalDivider(color=Color(0x804B586C))
                // The fourth value is shown only on a wide widget; the note is behind the (i) button.
                if(i==3) Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text(uiText(R.string.wg_param_n,i+1),Modifier.weight(1f),style=MaterialTheme.typography.labelLarge,color=muted)
                    SettingsInfo(uiText(R.string.wg_param_wide))
                } else Text(uiText(R.string.wg_param_n,i+1),style=MaterialTheme.typography.labelLarge,color=muted)
                PerunChips(labels,options.indexOf(params[i]).coerceAtLeast(0),{ pick ->
                    val next=params.toMutableList();val chosen=options[pick]
                    val other=next.indexOf(chosen);if(other>=0 && other!=i) next[other]=next[i]
                    next[i]=chosen;params=next
                })
            }
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
        // The Day by hours, the Air and pollen and the Sun and Moon widgets keep their own settings and have their own forms.
        val ownSettings=kind==DayHoursWidget::class.java || kind==AirWidget::class.java || kind==SkyWidget::class.java
        val locKey=when(kind) { DayHoursWidget::class.java->DayWidgets.loc(c,id);AirWidget::class.java->AirWidgets.loc(c,id);SkyWidget::class.java->SkyWidgets.loc(c,id);else->cfg.loc }
        val place=locations.firstOrNull { BackgroundRefresh.locationKey(it)==locKey }?.let { pickerName(it) } ?: uiText(R.string.wg_loc_active)
        Row(Modifier.fillMaxWidth().heightIn(min=52.dp).clickable { editing=if(editing==id) null else id },verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(PerunWidgets.kindName(kind),style=MaterialTheme.typography.titleSmall)
                Text(place,style=MaterialTheme.typography.bodySmall,color=muted)
            }
            Text(if(editing==id) "‹" else "›",style=MaterialTheme.typography.headlineSmall,color=MaterialTheme.colorScheme.primary)
        }
        if(editing==id) key(id) {
            if(kind==DayHoursWidget::class.java) DayWidgetForm(locations,DayWidgets.loc(c,id),DayWidgets.rows(c,id)) { loc,rows -> DayWidgets.write(c,id,loc,rows);runCatching { DayWidgets.draw(c,id) };DayWidgets.refresh(c,true);editing=null;tick++ }
            else if(kind==AirWidget::class.java) AirWidgetForm(locations,AirWidgets.read(c,id)) { next -> AirWidgets.write(c,id,next);AirWidgets.refresh(c,true);editing=null;tick++ }
            else if(kind==SkyWidget::class.java) SkyWidgetForm(locations,SkyWidgets.read(c,id)) { next -> SkyWidgets.write(c,id,next);SkyWidgets.refresh(c);editing=null;tick++ }
            else WidgetConfigForm(kind.name,locations,cfg) { next -> PerunWidgets.write(c,id,next);if(kind==SixDayWidget::class.java) SixDayWidgets.refresh(c,true) else PerunWidgets.refresh(c,true);editing=null;tick++ }
        }
        HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.5f))
    }
    if(placed.isNotEmpty()) OutlinedButton(onClick={ PerunWidgets.refreshAll(c) },modifier=Modifier.fillMaxWidth()) { Text(uiText(R.string.wg_refresh_all)) }
    // The note about adding from the home screen sits behind the (i) next to the heading, as in the background refresh settings.
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text(uiText(R.string.wg_add_title),Modifier.weight(1f),style=MaterialTheme.typography.labelLarge,color=muted)
        SettingsInfo(uiText(R.string.wg_add_note))
    }
    PerunWidgets.Listed.forEach { k -> OutlinedButton(onClick={ PerunWidgets.pin(c,k) },modifier=Modifier.fillMaxWidth()) { Text(PerunWidgets.kindName(k)) } }
}
