package sk.perun.weather.ui

import sk.perun.weather.R

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import sk.perun.weather.ui.theme.PerunTheme
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.math.abs

internal class DaySummary(val date:LocalDate,val code:Int,val max:Double,val min:Double)
/** Hourly values by Open-Meteo key, with their times (unix seconds) and the time zone of the place. */
internal class HourlySeries(val zone:ZoneId,val times:List<Long>,val values:Map<String,List<Double>>) {
    fun of(key:String):List<Double> = values[key] ?: List(times.size) { Double.NaN }
    fun days(count:Int=6):List<DaySummary> = daySummaries(times,of("temperature_2m"),of("weather_code"),zone,Instant.now().atZone(zone).toLocalDate(),count)
}
/** Days from today: highest and lowest temperature and the weather code of the hour nearest to noon, as in the day rows of the Forecast screen. */
internal fun daySummaries(times:List<Long>,temps:List<Double>,codes:List<Double>,zone:ZoneId,today:LocalDate,count:Int=6):List<DaySummary> {
    val rows=times.indices.groupBy { Instant.ofEpochSecond(times[it]).atZone(zone).toLocalDate() }
    return rows.keys.filter { it>=today }.sorted().mapNotNull { date ->
        val ix=rows.getValue(date);val t=ix.map { temps[it] }.filter { it.isFinite() }
        if(t.isEmpty()) null else {
            val noon=ix.minByOrNull { abs(Instant.ofEpochSecond(times[it]).atZone(zone).hour-12) }
            val code=noon?.let { codes[it] } ?: Double.NaN
            DaySummary(date,if(code.isFinite()) code.toInt() else -1,t.maxOrNull() ?: Double.NaN,t.minOrNull() ?: Double.NaN)
        }
    }.take(count)
}

/** The 6 days widget: the top of the Current conditions widget with six days in place of six hours. Settings are stored like those of the other widgets. */
internal object SixDayWidgets {
    private const val Once="perun-widget-days"
    private const val Periodic="perun-widget-days-tick"
    const val ActionRefresh="sk.perun.weather.DAYS_WIDGET_REFRESH"
    // The same list and order as the Forecast screen, so that both share one cached response.
    private val Keys=listOf("temperature_2m","apparent_temperature","precipitation","precipitation_probability","wind_speed_10m","wind_gusts_10m","wind_direction_10m","cloud_cover","uv_index","relative_humidity_2m","dew_point_2m","visibility","pressure_msl","snowfall","rain","showers","weather_code","is_day")
    private val Hm:DateTimeFormatter=DateTimeFormatter.ofPattern("HH:mm")
    private val CellIcon=intArrayOf(R.id.wg_c0i,R.id.wg_c1i,R.id.wg_c2i,R.id.wg_c3i)
    private val CellValue=intArrayOf(R.id.wg_c0v,R.id.wg_c1v,R.id.wg_c2v,R.id.wg_c3v)
    private val CellArrow=intArrayOf(R.id.wg_c0a,R.id.wg_c1a,R.id.wg_c2a,R.id.wg_c3a)
    private val DayName=intArrayOf(R.id.wg_h0t,R.id.wg_h1t,R.id.wg_h2t,R.id.wg_h3t,R.id.wg_h4t,R.id.wg_h5t)
    private val DayIcon=intArrayOf(R.id.wg_h0i,R.id.wg_h1i,R.id.wg_h2i,R.id.wg_h3i,R.id.wg_h4i,R.id.wg_h5i)
    private val DayMax=intArrayOf(R.id.wg_h0v,R.id.wg_h1v,R.id.wg_h2v,R.id.wg_h3v,R.id.wg_h4v,R.id.wg_h5v)
    private val DayMin=intArrayOf(R.id.wg_h0m,R.id.wg_h1m,R.id.wg_h2m,R.id.wg_h3m,R.id.wg_h4m,R.id.wg_h5m)

    fun ids(c:Context):IntArray = AppWidgetManager.getInstance(c).getAppWidgetIds(ComponentName(c,SixDayWidget::class.java))
    private fun online() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    fun refresh(c:Context,force:Boolean=false) {
        if(ids(c).isEmpty()) return
        val request=OneTimeWorkRequestBuilder<SixDayWidgetWorker>().setInputData(workDataOf("force" to force)).setConstraints(online()).build()
        WorkManager.getInstance(c.applicationContext).enqueueUniqueWork(Once,ExistingWorkPolicy.REPLACE,request)
    }
    fun schedule(c:Context) {
        val request=PeriodicWorkRequestBuilder<SixDayWidgetWorker>(30,TimeUnit.MINUTES).setConstraints(online()).build()
        WorkManager.getInstance(c.applicationContext).enqueueUniquePeriodicWork(Periodic,ExistingPeriodicWorkPolicy.KEEP,request)
    }
    fun cancel(c:Context) { WorkManager.getInstance(c.applicationContext).cancelUniqueWork(Periodic) }
    private fun num(x:Double,dec:Int):String = if(x.isFinite()) weatherUnitNumber(x,dec) else "-"
    private fun t(u:WeatherUnits,x:Double):String = num(u.temperature.fromCelsius(x),0)+"\u00B0"
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
    /** The hourly forecast of 16 days, the same request as the Forecast screen, so that both share one cached response. */
    internal fun loadSeries(l:Location,model:String):HourlySeries {
        val q="latitude=${l.latitude}&longitude=${l.longitude}&timezone="+URLEncoder.encode(l.timezone.ifBlank { "auto" },"UTF-8")+"&timeformat=unixtime&forecast_days=16&past_days=1&temperature_unit=celsius&wind_speed_unit=kmh&precipitation_unit=mm"+(l.elevation?.let { "&elevation=$it" } ?: "")
        val extra=(if(model=="best_match") "" else "&models="+URLEncoder.encode(model,"UTF-8"))+"&hourly="+Keys.joinToString(",")
        val root=JSONObject(String(fetchBytes("https://api.open-meteo.com/v1/forecast?$q$extra"),Charsets.UTF_8))
        val zone=ZoneId.of(root.getString("timezone"));val h=root.getJSONObject("hourly");val t=h.getJSONArray("time")
        fun series(key:String):List<Double> { val a=h.optJSONArray(key);return List(t.length()) { i -> if(a==null || a.isNull(i)) Double.NaN else a.optDouble(i,Double.NaN) } }
        return HourlySeries(zone,List(t.length()) { t.getLong(it) },Keys.associateWith { series(it) })
    }
    internal fun load(l:Location,model:String):List<DaySummary> = loadSeries(l,model).days()
    private fun launch(c:Context,id:Int,loc:String):PendingIntent {
        val i=(c.packageManager.getLaunchIntentForPackage(c.packageName) ?: Intent()).apply {
            data=android.net.Uri.parse("perun://widget/"+id)
            putExtra("perun_route","forecast");putExtra("perun_location",loc)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntent.getActivity(c,10000+id,i,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    /** Loads and draws one widget; false when the place or the data is missing. */
    suspend fun render(c:Context,id:Int,all:List<Location>,active:Location?,u:WeatherUnits):Boolean {
        val cfg=PerunWidgets.read(c,id)
        val l=(if(cfg.loc=="active") null else all.firstOrNull { BackgroundRefresh.locationKey(it)==cfg.loc }) ?: active ?: return false
        val src=LocationSourceStore(c).read(l)
        val d=loadOverview(l,src.alertSource,src.weatherModel)
        val ex:OverviewExtra?=loadOverviewExtra(l,src.weatherModel)
        val days=withContext(Dispatchers.IO) { load(l,src.weatherModel) }
        val dark=cfg.text=="dark"
        val fg=if(dark) 0xFF14161A.toInt() else 0xFFFFFFFF.toInt()
        val muted=if(dark) 0xFF4B586C.toInt() else 0xFFBBC4D2.toInt()
        val manager=AppWidgetManager.getInstance(c)
        val v=RemoteViews(c.packageName,R.layout.wg_days)
        v.setImageViewBitmap(R.id.wg_icon,widgetSymbolBitmap(d.code,d.isDayFlag,120))
        v.setTextViewText(R.id.wg_place,l.name);v.gpsMark(l,fg)
        v.setTextViewText(R.id.wg_cond,overviewCondition(d.code))
        v.setTextViewText(R.id.wg_temp,t(u,d.temperature))
        val snowing=(d.snowfall.isFinite() && d.snowfall>0.0) || d.code in setOf(71,73,75,77,85,86)
        // The fourth value needs the room of about five cells.
        val wide=(manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it>0 } ?: 250)>=PerunWidgets.WideDp
        v.setViewVisibility(R.id.wg_c3,if(wide) View.VISIBLE else View.GONE)
        v.setViewVisibility(R.id.wg_c3s,if(wide) View.VISIBLE else View.GONE)
        cfg.params.take(if(wide) 4 else 3).forEachIndexed { i,key ->
            v.setImageViewBitmap(CellIcon[i],widgetParamIcon(if(key=="rain" && snowing) "snow" else key,fg,48,ex?.pressureChange ?: Double.NaN))
            v.setTextViewText(CellValue[i],param(key,d,ex,u))
            widgetWindArrowView(v,CellArrow[i],key,d.direction,fg)
        }
        for(i in 0 until 6) {
            val day=days.getOrNull(i)
            v.setViewVisibility(DayIcon[i],if(day==null) View.INVISIBLE else View.VISIBLE)
            v.setTextViewText(DayName[i],if(day==null) "" else if(i==0) uiText(R.string.wg_today) else day.date.format(DateTimeFormatter.ofPattern("EEE",appLocale())))
            v.setTextViewText(DayMax[i],if(day==null) "" else t(u,day.max))
            v.setTextViewText(DayMin[i],if(day==null) "" else t(u,day.min))
            if(day!=null) v.setImageViewBitmap(DayIcon[i],widgetSymbolBitmap(day.code,true,64))
        }
        v.setTextViewText(R.id.wg_foot,uiText(R.string.wg_updated,d.time.format(Hm)))
        (intArrayOf(R.id.wg_place,R.id.wg_temp)+CellValue+DayMax).forEach { v.setTextColor(it,fg) }
        (intArrayOf(R.id.wg_cond,R.id.wg_foot)+DayName+DayMin).forEach { v.setTextColor(it,muted) }
        // The warning of the six days that the widget shows (widgetAlert).
        val now=Instant.now()
        val alert=widgetDays(now,d.zone,6).let { (from,to)->widgetAlert(d.alerts,from,to) }
        if(alert==null) v.setViewVisibility(R.id.wg_alert,View.GONE) else {
            v.setViewVisibility(R.id.wg_alert,View.VISIBLE)
            v.setImageViewBitmap(R.id.wg_alert,widgetAlertBitmap(alert,54))
            v.setContentDescription(R.id.wg_alert,alertName(alert))
        }
        v.setInt(R.id.wg_root,"setBackgroundResource",widgetBackgroundRes(cfg.bg))
        v.setOnClickPendingIntent(R.id.wg_root,launch(c,id,BackgroundRefresh.locationKey(l)))
        v.setInt(R.id.wg_refresh,"setColorFilter",muted)
        v.setOnClickPendingIntent(R.id.wg_refresh,PendingIntent.getBroadcast(c,20000+id,Intent(c,SixDayWidget::class.java).setAction(ActionRefresh),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        manager.updateAppWidget(id,v)
        return true
    }
}

class SixDayWidget:AppWidgetProvider() {
    override fun onReceive(context:Context,intent:Intent) { if(intent.action==SixDayWidgets.ActionRefresh) SixDayWidgets.refresh(context,true) else super.onReceive(context,intent) }
    override fun onUpdate(context:Context,manager:AppWidgetManager,ids:IntArray) { SixDayWidgets.refresh(context);SixDayWidgets.schedule(context) }
    // A new width can show or hide the fourth value.
    override fun onAppWidgetOptionsChanged(context:Context,manager:AppWidgetManager,id:Int,options:Bundle) { SixDayWidgets.refresh(context) }
    override fun onDeleted(context:Context,ids:IntArray) { PerunWidgets.remove(context,ids);WidgetBackup.release(context,ids) }
    override fun onDisabled(context:Context) { SixDayWidgets.cancel(context) }
}

internal class SixDayWidgetWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        val c=applicationContext
        // A widget refresh moves the Current location first: precise after the refresh button, otherwise power-saving.
        CurrentLocation.update(c,precise=inputData.getBoolean("force",false),fromBackground=true)
        val store=LocationStore(c);val all=store.load()
        val active=all.getOrNull(store.loadSelected()) ?: all.firstOrNull()
        val units=readForecastPreferences(c.getSharedPreferences("perun_forecast",Context.MODE_PRIVATE)).units
        val force=inputData.getBoolean("force",false)
        var failed=false
        if(force) NetCache.forceNetwork=true
        try {
            for(id in SixDayWidgets.ids(c)) {
                val ok=try { SixDayWidgets.render(c,id,all,active,units) } catch(e:CancellationException) { throw e } catch(e:Exception) { false }
                if(!ok) failed=true
            }
        } finally { if(force) NetCache.forceNetwork=false }
        return if(failed && runAttemptCount<2) Result.retry() else Result.success()
    }
}

class SixDayConfigActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val id=intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED,Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id))
        if(id==AppWidgetManager.INVALID_APPWIDGET_ID) { finish();return }
        // A widget placed after a restore takes the waiting settings of its kind from the backup.
        WidgetBackup.prefill(this,id,::backupReadValue)
        val locations=LocationStore(this).load()
        val initial=PerunWidgets.read(this,id)
        setContent {
            PerunTheme {
                Surface(Modifier.fillMaxSize(),color=Color(0xFF151A22),contentColor=Color(0xFFE8EAF0)) {
                    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Text(uiText(R.string.wg_config_title),style=MaterialTheme.typography.titleLarge,color=Color(0xFFE8EAF0))
                        // The form of the Current conditions widget: place, four values, background and text colour.
                        WidgetConfigForm("SixDayWidget",locations,initial) { cfg ->
                            PerunWidgets.write(this@SixDayConfigActivity,id,cfg)
                            SixDayWidgets.refresh(this@SixDayConfigActivity)
                            SixDayWidgets.schedule(this@SixDayConfigActivity)
                            WidgetBackup.commit(this@SixDayConfigActivity,id)
                            setResult(RESULT_OK,Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id))
                            finish()
                        }
                    }
                }
            }
        }
    }
}
