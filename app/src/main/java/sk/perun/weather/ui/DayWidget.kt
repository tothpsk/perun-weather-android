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
import android.graphics.Path
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path as ComposePath
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import sk.perun.weather.ui.theme.PerunTheme
import java.io.File
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Rows of the hourly chart, in the order of the expanded day on the Forecast screen. */
internal val DayWidgetRows=listOf("temp","rain","prob","wind","gust","direction","cloud","uv","humidity","dew","visibility","pressure")
private const val DayWidgetFixedDp=45
private const val DayWidgetMinRowDp=24
/** As many rows as fit under the now badge and the icon strip and above the time axis, at least one. */
internal fun dayWidgetRowCount(chartDp:Int,chosen:Int):Int = ((chartDp-DayWidgetFixedDp)/DayWidgetMinRowDp).coerceIn(1,maxOf(1,chosen))
/** The chosen day while the data still has it; a day that has passed falls back to the first one. */
internal fun dayWidgetDate(wanted:LocalDate?,days:List<LocalDate>):LocalDate? = when {
    days.isEmpty()->null
    wanted==null || wanted<days.first()->days.first()
    wanted>days.last()->days.last()
    else->days.firstOrNull { it>=wanted }
}
internal fun dayWidgetStep(current:LocalDate,days:List<LocalDate>,delta:Int):LocalDate = days[(days.indexOf(current)+delta).coerceIn(0,days.lastIndex)]

private class DayData(val zone:ZoneId,val hours:List<ForecastHour>) {
    private fun date(h:ForecastHour):LocalDate = Instant.ofEpochSecond(h.time).atZone(zone).toLocalDate()
    fun days(from:LocalDate):List<LocalDate> = hours.map { date(it) }.distinct().filter { it>=from }
    fun day(d:LocalDate):ForecastDay = ForecastDay(d,d.atStartOfDay(zone).toEpochSecond(),d.plusDays(1).atStartOfDay(zone).toEpochSecond(),hours.filter { date(it)==d })
}

// The row icons of the expanded day on the Forecast screen. Their composables (OverviewTileKit.kt, HumidityGraphic.kt,
// ForecastScreen.kt) cannot run in a widget, so the same shapes are drawn here with the same Compose drawing calls.
private fun svg(d:String):ComposePath = PathParser().parsePathString(d).toNodes().toPath()
private fun composeBitmap(px:Int,box:Float,block:DrawScope.()->Unit):Bitmap {
    val image=ImageBitmap(px,px)
    CanvasDrawScope().draw(Density(1f),LayoutDirection.Ltr,androidx.compose.ui.graphics.Canvas(image),Size(px.toFloat(),px.toFloat())) { scale(px/box,px/box,Offset.Zero) { block() } }
    return image.asAndroidBitmap()
}
private fun rowGlyph(row:String,color:Color,px:Int):Bitmap = composeBitmap(px,24f) {
    val s=Stroke(2f,cap=StrokeCap.Round,join=StrokeJoin.Round)
    when(row) {
        "temp"->drawPath(svg("M10 4a2 2 0 0 1 4 0v9a4 4 0 1 1-4 0Z"),color,style=s)
        "rain"->drawPath(svg("M12 2C12 2 5 10 5 15a7 7 0 0 0 14 0C19 10 12 2 12 2Z"),color,style=s)
        "snow"->drawPath(svg("M12 3v18M4.2 7.5l15.6 9M4.2 16.5l15.6-9"),color,style=s)
        "wind"->drawPath(svg("M3 9h11a3 3 0 1 0-3-3M3 15h15a3 3 0 1 1-3 3M3 12h7"),color,style=s)
        "uv"->{ val sun=Stroke(1.6f,cap=StrokeCap.Round,join=StrokeJoin.Round);drawPath(svg("M12 1.1v1.3M6.1 7h1.3M16.6 7h1.3M7.83 2.83l0.92 0.92M16.17 2.83l-0.92 0.92M7.83 11.17l0.92-0.92M16.17 11.17l-0.92-0.92"),color,style=sun);drawCircle(color,2.4f,Offset(12f,7f),style=sun);drawPath(svg("M5.5 14.4v5.1a2.5 2.5 0 0 0 5 0v-5.1M13 14.4l2.75 7.6l2.75-7.6"),color,style=Stroke(1.8f,cap=StrokeCap.Round,join=StrokeJoin.Round)) }
        "gust"->drawPath(ComposePath().apply {
            moveTo(1.5f,9f);lineTo(12f,9f);cubicTo(16.7f,9f,16.7f,2.5f,12.9f,2.5f);cubicTo(10.9f,2.5f,10f,4f,10f,5.4f)
            moveTo(4.5f,12f);lineTo(18.3f,12f);cubicTo(23.3f,12f,23.3f,5.2f,19.2f,5.2f);cubicTo(17.5f,5.2f,16.7f,6.6f,16.7f,8f)
            moveTo(1.5f,15f);lineTo(17.8f,15f);cubicTo(23f,15f,23f,21.5f,19f,21.5f);cubicTo(17.1f,21.5f,16.1f,20f,16.1f,18.6f)
        },color,style=Stroke(1.8f,cap=StrokeCap.Round,join=StrokeJoin.Round))
        "cloud"->drawPath(ComposePath().apply { moveTo(5f,19f);cubicTo(0f,19f,0f,11f,5f,11f);cubicTo(5f,3f,16f,3f,17f,10f);cubicTo(24f,9f,25f,19f,19f,19f);close() },color,style=Stroke(1.8f,join=StrokeJoin.Round))
        "humidity"->{
            drawPath(ComposePath().apply { moveTo(12f,2f);cubicTo(12f,2f,5f,10f,5f,15f);cubicTo(5f,24.333334f,19f,24.333334f,19f,15f);cubicTo(19f,10f,12f,2f,12f,2f);close() },color,style=Stroke(1.6f,cap=StrokeCap.Round,join=StrokeJoin.Round))
            drawCircle(color,1.25f,Offset(9.4f,12.8f),style=Stroke(1.1f))
            drawCircle(color,1.25f,Offset(14.6f,17.6f),style=Stroke(1.1f))
            drawLine(color,Offset(9.5f,18.1f),Offset(14.5f,12.3f),1.4f,StrokeCap.Round)
        }
        "dew"->{
            val thermometer=ComposePath().apply { moveTo(7f,14.37f);lineTo(7f,5f);arcTo(Rect(7f,3f,11f,7f),180f,180f,false);lineTo(11f,14.37f);arcTo(Rect(5.2f,13.8f,12.8f,21.4f),-58.2f,296.4f,false);close() }
            drawPath(thermometer,color,style=Stroke(1.6f,cap=StrokeCap.Round,join=StrokeJoin.Round))
            drawLine(color,Offset(9f,17.6f),Offset(9f,10.5f),1.5f,StrokeCap.Round)
            drawCircle(color,1.7f,Offset(9f,17.6f))
            drawPath(ComposePath().apply { moveTo(17.5f,4.5f);cubicTo(17.5f,4.5f,14.2f,8.2f,14.2f,11f);arcTo(Rect(14.2f,7.7f,20.8f,14.3f),180f,-180f,false);cubicTo(20.8f,8.2f,17.5f,4.5f,17.5f,4.5f);close() },color)
        }
        "visibility"->{
            drawPath(ComposePath().apply { moveTo(2f,12f);cubicTo(7f,2f,17f,2f,22f,12f);cubicTo(17f,22f,7f,22f,2f,12f);close() },color,style=Stroke(1.7f,cap=StrokeCap.Round,join=StrokeJoin.Round))
            drawCircle(color,3.5f,Offset(12f,12f))
        }
        "pressure"->{
            drawCircle(color,10f,Offset(12f,12f),style=Stroke(1.7f,cap=StrokeCap.Round,join=StrokeJoin.Round))
            drawLine(color,Offset(6f,12f),Offset(18f,12f),1.8f,StrokeCap.Round)
            drawLine(color,Offset(14f,8f),Offset(18f,12f),1.8f,StrokeCap.Round)
            drawLine(color,Offset(14f,16f),Offset(18f,12f),1.8f,StrokeCap.Round)
        }
        else->Unit
    }
}
/** The compass of the wind direction row: ring, arrow and the letter for north. */
private fun compassGlyph(color:Color,px:Int):Bitmap {
    val bmp=composeBitmap(px,100f) {
        drawCircle(Color(0xFF8C9BB2),36f,Offset(50f,58f),style=Stroke(2f))
        rotate(180f,Offset(50f,58f)) { drawPath(svg("M50 28L38 80L50 68L62 80Z"),color) }
        drawCircle(Color(0xFFE8EAF0),4f,Offset(50f,58f))
    }
    val k=px/100f
    val p=Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color=0xFFE8EAF0.toInt();textSize=18f*k;textAlign=Paint.Align.CENTER;typeface=Typeface.DEFAULT_BOLD }
    Canvas(bmp).drawText(uiText(R.string.ui_s_8de0b3c4),50f*k,15f*k,p)
    return bmp
}

/** The Day by hours widget keeps the last forecast in a file, so that the day arrows and the now line work without the network. */
internal object DayWidgets {
    private const val Prefs="perun_widget_day"
    private const val Once="perun-widget-day"
    private const val Periodic="perun-widget-day-tick"
    const val ActionRefresh="sk.perun.weather.DAY_WIDGET_REFRESH"
    const val ActionPrev="sk.perun.weather.DAY_WIDGET_PREV"
    const val ActionNext="sk.perun.weather.DAY_WIDGET_NEXT"
    // The same list and order as the Forecast screen, so that both share one cached response.
    private val Keys=listOf("temperature_2m","apparent_temperature","precipitation","precipitation_probability","wind_speed_10m","wind_gusts_10m","wind_direction_10m","cloud_cover","uv_index","relative_humidity_2m","dew_point_2m","visibility","pressure_msl","snowfall","rain","showers","weather_code","is_day")
    private val RowKey=mapOf("temp" to "temperature_2m","rain" to "precipitation","prob" to "precipitation_probability","wind" to "wind_speed_10m","gust" to "wind_gusts_10m","direction" to "wind_direction_10m","cloud" to "cloud_cover","uv" to "uv_index","humidity" to "relative_humidity_2m","dew" to "dew_point_2m","visibility" to "visibility","pressure" to "pressure_msl")
    private val ZeroBased=setOf("rain","prob","wind","gust","cloud","uv","humidity")
    private val Levels=listOf("red","orange","yellow")
    private val Hm:DateTimeFormatter=DateTimeFormatter.ofPattern("HH:mm")
    private const val TextColor=0xFFE8EAF0.toInt()
    private const val Panel=0xFF202833.toInt()
    private const val PanelAlt=0xFF2A3543.toInt()
    private const val Divider=0xFF18212C.toInt()
    private const val Grid=0x3B4B586C
    private const val Disabled=0xFF4B586C.toInt()
    private val Muted:Int get()=ForecastMuted.toArgb()

    private fun prefs(c:Context) = c.applicationContext.getSharedPreferences(Prefs,Context.MODE_PRIVATE)
    private fun file(c:Context,id:Int) = File(c.applicationContext.filesDir,"widget_day_$id.json")
    fun ids(c:Context):IntArray = AppWidgetManager.getInstance(c).getAppWidgetIds(ComponentName(c,DayHoursWidget::class.java))
    fun loc(c:Context,id:Int):String = prefs(c).getString("loc_$id","active") ?: "active"
    fun rows(c:Context,id:Int):List<String> {
        val saved=(prefs(c).getString("rows_$id",null) ?: return DayWidgetRows).split(",")
        return DayWidgetRows.filter { it in saved }.ifEmpty { DayWidgetRows }
    }
    fun write(c:Context,id:Int,loc:String,rows:List<String>) {
        prefs(c).edit().putString("loc_$id",loc).putString("rows_$id",DayWidgetRows.filter { it in rows }.joinToString(",")).apply()
    }
    fun remove(c:Context,ids:IntArray) {
        val edit=prefs(c).edit()
        ids.forEach { id -> listOf("loc_","rows_","date_","key_","at_","alert_").forEach { edit.remove(it+id) };file(c,id).delete() }
        edit.apply()
    }
    /** Downloads when the data is old or force is set, then redraws every widget. */
    fun refresh(c:Context,force:Boolean=false) {
        if(ids(c).isEmpty()) return
        val request=OneTimeWorkRequestBuilder<DayWidgetWorker>().setInputData(workDataOf("force" to force)).build()
        WorkManager.getInstance(c.applicationContext).enqueueUniqueWork(Once,ExistingWorkPolicy.REPLACE,request)
    }
    /** Every 15 minutes: moves the now line from the stored data and downloads only when the data is old. */
    fun schedule(c:Context) {
        val request=PeriodicWorkRequestBuilder<DayWidgetWorker>(15,TimeUnit.MINUTES).build()
        WorkManager.getInstance(c.applicationContext).enqueueUniquePeriodicWork(Periodic,ExistingPeriodicWorkPolicy.KEEP,request)
    }
    fun cancel(c:Context) { WorkManager.getInstance(c.applicationContext).cancelUniqueWork(Periodic) }
    fun place(c:Context,id:Int):Location? {
        val store=LocationStore(c);val all=store.load();val want=loc(c,id)
        return (if(want=="active") null else all.firstOrNull { BackgroundRefresh.locationKey(it)==want }) ?: all.getOrNull(store.loadSelected()) ?: all.firstOrNull()
    }
    private fun stamp(c:Context,l:Location):String = BackgroundRefresh.locationKey(l)+"|"+LocationSourceStore(c).read(l).weatherModel
    fun stale(c:Context,id:Int,l:Location):Boolean {
        val p=prefs(c)
        return p.getString("key_$id","")!=stamp(c,l) || !file(c,id).exists() || System.currentTimeMillis()/1000-p.getLong("at_$id",0L)>3000
    }
    private fun parse(raw:String):DayData {
        val root=JSONObject(raw);val zone=ZoneId.of(root.getString("timezone"));val h=root.getJSONObject("hourly");val t=h.getJSONArray("time")
        val all=List(t.length()) { i -> ForecastHour(t.getLong(i),Keys.associateWith { k -> val a=h.optJSONArray(k);if(a==null || a.isNull(i)) Double.NaN else a.optDouble(i,Double.NaN) }) }
        val last=all.lastOrNull { it.v("temperature_2m").isFinite() }?.time ?: throw IllegalStateException()
        return DayData(zone,all.filter { it.time<=last })
    }
    suspend fun download(c:Context,id:Int,l:Location) {
        val src=LocationSourceStore(c).read(l)
        withContext(Dispatchers.IO) {
            val q="latitude=${l.latitude}&longitude=${l.longitude}&timezone="+URLEncoder.encode(l.timezone.ifBlank { "auto" },"UTF-8")+"&timeformat=unixtime&forecast_days=16&past_days=1&temperature_unit=celsius&wind_speed_unit=kmh&precipitation_unit=mm"+(l.elevation?.let { "&elevation=$it" } ?: "")
            fun request(extra:String):JSONObject = JSONObject(String(fetchBytes("https://api.open-meteo.com/v1/forecast?$q$extra"),Charsets.UTF_8))
            val root=request((if(src.weatherModel=="best_match") "" else "&models="+URLEncoder.encode(src.weatherModel,"UTF-8"))+"&hourly="+Keys.joinToString(","))
            val hourly=root.getJSONObject("hourly");val times=hourly.getJSONArray("time")
            // As on the Forecast screen: values the chosen model lacks are filled in, UV from GFS and the probability from the default model.
            fun fill(key:String,extra:String) {
                val target=hourly.optJSONArray(key) ?: return
                if((0 until times.length()).none { target.isNull(it) }) return
                try {
                    val other=request(extra).getJSONObject("hourly");val ot=other.getJSONArray("time");val ov=other.getJSONArray(key)
                    val ix=(0 until ot.length()).associate { ot.getLong(it) to if(ov.isNull(it)) Double.NaN else ov.optDouble(it,Double.NaN) }
                    for(i in 0 until times.length()) if(target.isNull(i)) { val a=ix[times.getLong(i)] ?: Double.NaN;if(a.isFinite()) target.put(i,a) }
                } catch(e:CancellationException) { throw e } catch(e:Exception) { }
            }
            fill("uv_index","&models=gfs_seamless&hourly=uv_index")
            if(src.weatherModel!="best_match") fill("precipitation_probability","&hourly=precipitation_probability")
            val text=root.toString()
            parse(text)
            file(c,id).writeText(text)
        }
        // Official warnings with their validity, so that the header can show the one valid now. A failed request stores none.
        val alerts=try { loadOverview(l,src.alertSource,src.weatherModel).alerts.filter { !it.local && it.level in Levels } }
            catch(e:CancellationException) { throw e } catch(e:Exception) { emptyList() }
        val stored=JSONArray()
        alerts.forEach { a -> stored.put(JSONObject().put("level",a.level).put("event",a.event).put("start",a.start?.epochSecond ?: 0L).put("end",a.end?.epochSecond ?: 0L)) }
        prefs(c).edit().putString("key_$id",stamp(c,l)).putLong("at_$id",System.currentTimeMillis()/1000).putString("alert_$id",stored.toString()).apply()
    }
    /** Level and event of the strongest stored warning that is valid at the given time. */
    private fun currentAlert(raw:String?,now:Long):Pair<String,String>? {
        if(raw==null) return null
        val list=runCatching { JSONArray(raw) }.getOrNull() ?: return null
        var best:Pair<String,String>?=null
        for(i in 0 until list.length()) {
            val o=list.optJSONObject(i) ?: continue
            val level=o.optString("level");val start=o.optLong("start",0L);val end=o.optLong("end",0L)
            if(level !in Levels || (start>0L && now<start) || (end>0L && now>=end)) continue
            val current=best
            if(current==null || Levels.indexOf(level)<Levels.indexOf(current.first)) best=level to o.optString("event")
        }
        return best
    }
    private fun read(c:Context,id:Int,l:Location):DayData? {
        if(prefs(c).getString("key_$id","")!=stamp(c,l)) return null
        return runCatching { parse(file(c,id).readText()) }.getOrNull()
    }
    private fun send(c:Context,id:Int,action:String):PendingIntent {
        val i=Intent(c,DayHoursWidget::class.java).setAction(action).setData(android.net.Uri.parse("perun://day-widget/$id")).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id)
        return PendingIntent.getBroadcast(c,id,i,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    /** A tap opens the Forecast screen; with a day it opens that day expanded. */
    private fun launch(c:Context,id:Int,loc:String,day:LocalDate?):PendingIntent {
        val i=(c.packageManager.getLaunchIntentForPackage(c.packageName) ?: Intent()).apply {
            data=android.net.Uri.parse("perun://widget/"+id)
            putExtra("perun_route",if(day==null) "forecast" else "forecast?day="+day);putExtra("perun_location",loc)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntent.getActivity(c,10000+id,i,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    private fun chevron(dn:Float,right:Boolean,color:Int):Bitmap {
        val s=(24*dn).roundToInt();val bmp=Bitmap.createBitmap(s,s,Bitmap.Config.ARGB_8888);val cv=Canvas(bmp)
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.STROKE;strokeWidth=2.4f*dn;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND;this.color=color }
        val a=if(right) .38f else .62f;val m=if(right) .62f else .38f
        cv.drawPath(Path().apply { moveTo(s*a,s*.25f);lineTo(s*m,s*.5f);lineTo(s*a,s*.75f) },p)
        return bmp
    }
    private fun dayLabel(date:LocalDate,today:LocalDate):String {
        val name=when(date) { today->uiText(R.string.wg_today);today.plusDays(1)->uiText(R.string.wg_tomorrow);else->date.format(DateTimeFormatter.ofPattern("EEE",appLocale())) }
        return name+" \u00B7 "+date.format(DateTimeFormatter.ofPattern(appPattern("d. M."),appLocale()))
    }
    /** Draws one widget from the stored data; delta moves the day and needs no network. */
    fun draw(c:Context,id:Int,delta:Int=0) {
        val manager=AppWidgetManager.getInstance(c)
        val v=RemoteViews(c.packageName,R.layout.wg_day)
        val dn=c.resources.displayMetrics.density
        val blue=RainBlue.toArgb()
        v.setInt(R.id.wg_refresh,"setColorFilter",Muted)
        v.setOnClickPendingIntent(R.id.wg_refresh,send(c,id,ActionRefresh))
        v.setOnClickPendingIntent(R.id.wg_day_prev,send(c,id,ActionPrev))
        v.setOnClickPendingIntent(R.id.wg_day_next,send(c,id,ActionNext))
        val l=place(c,id)
        if(l!=null) {
            v.setTextViewText(R.id.wg_place,l.name)
            v.setOnClickPendingIntent(R.id.wg_root,launch(c,id,BackgroundRefresh.locationKey(l),null))
        }
        val data=if(l==null) null else read(c,id,l)
        val today=if(data==null) null else Instant.now().atZone(data.zone).toLocalDate()
        val days=if(data==null || today==null) emptyList() else data.days(today)
        if(data==null || today==null || days.isEmpty()) {
            v.setTextViewText(R.id.wg_day_name,uiText(R.string.wg_loading))
            v.setViewVisibility(R.id.wg_alert,View.GONE)
            v.setImageViewBitmap(R.id.wg_day_prev,chevron(dn,false,Disabled))
            v.setImageViewBitmap(R.id.wg_day_next,chevron(dn,true,Disabled))
            manager.updateAppWidget(id,v)
            return
        }
        val p=prefs(c)
        val wanted=p.getString("date_$id",null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        var date=dayWidgetDate(wanted,days) ?: days.first()
        if(delta!=0) { date=dayWidgetStep(date,days,delta);p.edit().putString("date_$id",date.toString()).apply() }
        v.setTextViewText(R.id.wg_day_name,dayLabel(date,today))
        // The tap carries the shown day, so that the Forecast screen opens it expanded.
        if(l!=null) v.setOnClickPendingIntent(R.id.wg_root,launch(c,id,BackgroundRefresh.locationKey(l),date))
        v.setImageViewBitmap(R.id.wg_day_prev,chevron(dn,false,if(date>days.first()) blue else Disabled))
        v.setImageViewBitmap(R.id.wg_day_next,chevron(dn,true,if(date<days.last()) blue else Disabled))
        val alert=currentAlert(p.getString("alert_$id",null),Instant.now().epochSecond)
        if(alert==null) v.setViewVisibility(R.id.wg_alert,View.GONE) else {
            v.setViewVisibility(R.id.wg_alert,View.VISIBLE)
            v.setImageViewBitmap(R.id.wg_alert,widgetAlertBitmap(alert.second,alert.first,54))
            v.setContentDescription(R.id.wg_alert,alert.second)
        }
        val o=manager.getAppWidgetOptions(id)
        val wDp=o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it>0 } ?: 300
        val hDp=o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it>0 } ?: 200
        val chartDp=(hDp-54).coerceAtLeast(40)
        val cw=((wDp-20)*dn).roundToInt().coerceIn(200,1400)
        // A tall widget would need more pixels than the picture of a widget may have. The chart is then drawn at a lower density
        // in its true proportions and enlarged by the layout, instead of being cut short and stretched.
        val fullH=(chartDp*dn).roundToInt().coerceAtLeast((40*dn).roundToInt())
        val fit=Math.sqrt(1_500_000.0/(cw.toDouble()*fullH)).coerceAtMost(1.0).toFloat()
        val bw=(cw*fit).roundToInt().coerceAtLeast(1);val ch=(fullH*fit).roundToInt().coerceAtLeast(1)
        val units=readForecastPreferences(c.getSharedPreferences("perun_forecast",Context.MODE_PRIVATE)).units
        val chosen=rows(c,id)
        v.setImageViewBitmap(R.id.wg_chart,chart(data.day(date),data.zone,units,chosen.take(dayWidgetRowCount(chartDp,chosen.size)),bw,ch,dn*fit))
        manager.updateAppWidget(id,v)
    }
    private fun convert(row:String,x:Double,u:WeatherUnits):Double = when(row) {
        "temp","dew"->u.temperature.fromCelsius(x);"rain"->u.precipitation.fromMillimetres(x);"wind","gust"->u.wind.fromKmh(x)
        "visibility"->x/1000;"pressure"->u.pressure.fromHpa(x);else->x
    }
    private fun decimals(row:String,u:WeatherUnits):Int = when(row) { "rain"->if(u.precipitation==PrecipitationUnit.Imperial) 2 else 1;"visibility"->1;else->0 }
    private fun color(row:String,h:ForecastHour):Int {
        val x=h.v(RowKey.getValue(row))
        if(!x.isFinite()) return Muted
        return when(row) {
            "temp","dew"->tempDotColor(x).toArgb()
            "rain"->(precipitationType(h.v("rain"),h.v("showers"),h.v("snowfall")) ?: RainBlue).toArgb()
            "prob"->RainBlue.toArgb()
            "wind","gust"->windBeaufortColor(x).toArgb()
            "uv"->uvColor(x).toArgb()
            "humidity"->humidityBand(x,h.v("dew_point_2m"))?.color?.toArgb() ?: Muted
            "visibility"->visibilityBand(x)?.color?.toArgb() ?: Muted
            "cloud"->0xFF8294AD.toInt()
            "pressure"->0xFFBA9EDB.toInt()
            else->Muted
        }
    }
    /** From top: the now badge, weather icons, the chosen rows, the time axis. Colours, scales and labels follow the expanded day of the Forecast screen. */
    private fun chart(day:ForecastDay,zone:ZoneId,u:WeatherUnits,rows:List<String>,wPx:Int,hPx:Int,dn:Float):Bitmap {
        val bmp=Bitmap.createBitmap(wPx,hPx,Bitmap.Config.ARGB_8888);val cv=Canvas(bmp)
        if(day.hours.isEmpty() || rows.isEmpty()) return bmp
        val p=Paint(Paint.ANTI_ALIAS_FLAG)
        val left=26*dn;val right=wPx-6*dn;val width=(right-left).coerceAtLeast(1f)
        val badge=13*dn;val top=badge+20*dn;val bottom=hPx-12*dn;val rh=(bottom-top)/rows.size
        val duration=(day.end-day.start).toDouble()
        val green=ForecastGreen.toArgb()
        fun x(t:Long):Float = left+((t-day.start)/duration*width).toFloat()
        fun slot(h:Int):Long = when { h<=0->day.start;h>=24->day.end;else->day.date.atTime(h,0).atZone(zone).toEpochSecond() }
        fun hourOf(h:ForecastHour):Int = Instant.ofEpochSecond(h.time).atZone(zone).hour
        fun rect(color:Int,l:Float,t:Float,r:Float,b:Float) { p.style=Paint.Style.FILL;p.color=color;cv.drawRect(l,t,r,b,p) }
        fun line(color:Int,x0:Float,y0:Float,x1:Float,y1:Float,w:Float) { p.style=Paint.Style.STROKE;p.strokeWidth=w;p.strokeCap=Paint.Cap.ROUND;p.color=color;cv.drawLine(x0,y0,x1,y1,p) }
        fun arrow(cx:Float,cy:Float,deg:Double,r:Float) {
            val ang=(deg+180)*PI/180;val vx=sin(ang).toFloat();val vy=-cos(ang).toFloat()
            val tx=cx+vx*r;val ty=cy+vy*r;val bx=tx-vx*r*.57f;val by=ty-vy*r*.57f;val sx=-vy*r*.43f;val sy=vx*r*.43f
            line(green,cx-vx*r,cy-vy*r,tx,ty,1.5f*dn);line(green,tx,ty,bx+sx,by+sy,1.5f*dn);line(green,tx,ty,bx-sx,by-sy,1.5f*dn)
        }
        day.hours.forEach { h -> rect(if(h.v("is_day")==1.0) 0xFF25303C.toInt() else Panel,x(h.time),badge,x(min(h.time+3600,day.end)),top) }
        p.textSize=9*dn;p.textAlign=Paint.Align.LEFT
        rows.forEachIndexed { ri,row ->
            val y0=top+ri*rh;val y1=y0+rh;val key=RowKey.getValue(row)
            rect(if(ri%2==0) Panel else PanelAlt,0f,y0,wPx.toFloat(),y1)
            day.hours.filter { it.v("is_day")==1.0 }.forEach { h -> rect(0x06FFFFFF,x(h.time),y0,x(min(h.time+3600,day.end)),y1) }
            rect(Divider,0f,y0,wPx.toFloat(),y0+dn)
            val gs=min(18*dn,rh-6*dn).roundToInt().coerceAtLeast(8)
            val icon=when(row) {
                "direction"->compassGlyph(Color.White,(gs*1.15f).roundToInt())
                "prob"->widgetGlyphBitmap("prob",Color.White.toArgb(),gs)
                "rain"->rowGlyph(row,Color.White,gs)
                "gust"->rowGlyph(row,Color.White,gs)
                "dew"->rowGlyph(row,Color.White,gs)
                else->rowGlyph(row,Color.White,gs)
            }
            cv.drawBitmap(icon,(left-icon.width)/2f,y0+(rh-icon.height)/2f,null)
            if(row=="direction") {
                val arrowSize=min(7*dn,rh/2-2*dn).coerceAtLeast(3*dn)
                day.hours.forEach { h -> val deg=h.v(key);if(deg.isFinite() && hourOf(h)%3==0) arrow(x(h.time).coerceIn(left+6*dn,right-6*dn),y0+rh/2,deg,arrowSize) }
                return@forEachIndexed
            }
            val values=day.hours.map { convert(row,it.v(key),u) }
            val finite=values.filter { it.isFinite() }
            if(finite.isEmpty()) return@forEachIndexed
            val low=if(row in ZeroBased) 0.0 else finite.minOrNull() ?: 0.0
            val high=(finite.maxOrNull() ?: low).coerceAtLeast(low+1)
            val labelSpace=if(row=="pressure") 3*dn else 11*dn
            val span=(rh-labelSpace-4*dn).coerceAtLeast(1f)
            fun y(a:Double):Float = y1-3*dn-((a-low)/(high-low)*span).toFloat()
            var previous=-1
            day.hours.forEachIndexed { i,h ->
                val a=values[i]
                if(a.isFinite()) {
                    val tone=color(row,h)
                    if(previous>=0 && h.time-day.hours[previous].time<=3600L) line(tone,x(day.hours[previous].time),y(values[previous]),x(h.time),y(a),1.4f*dn)
                    p.style=Paint.Style.FILL;p.color=tone;cv.drawCircle(x(h.time),y(a),1.3f*dn,p)
                    previous=i
                } else previous=-1
            }
            // The app shows no numbers in the pressure row either.
            if(row!="pressure") {
                var lastEnd=left-100f
                day.hours.forEachIndexed { i,h ->
                    val a=values[i]
                    if(a.isFinite() && hourOf(h)%3==0) {
                        val s=weatherUnitNumber(a,decimals(row,u));val tw=p.measureText(s)
                        val at=x(h.time).coerceAtMost(right-tw).coerceAtLeast(left)
                        if(at>=lastEnd+3*dn) { p.style=Paint.Style.FILL;p.color=color(row,h);cv.drawText(s,at,y0+10*dn,p);lastEnd=at+tw }
                    }
                }
            }
            // Snowfall is marked under the precipitation line every three hours, as in the app.
            if(row=="rain") {
                val ss=min(11*dn,rh*.4f).roundToInt().coerceAtLeast(6)
                day.hours.forEach { h -> if(h.v("snowfall")>0 && hourOf(h)%3==0) cv.drawBitmap(rowGlyph("snow",Color(color(row,h)),ss),(x(h.time)-ss/2f).coerceIn(left,(right-ss).coerceAtLeast(left)),y1-ss-dn,null) }
            }
        }
        for(k in 0..8) line(Grid,x(slot(k*3)),badge,x(slot(k*3)),bottom,.5f*dn)
        rect(Divider,0f,bottom,wPx.toFloat(),bottom+dn)
        p.style=Paint.Style.FILL;p.color=Muted
        for(k in 0..4) {
            val s=if(k==4) "24:00" else Instant.ofEpochSecond(slot(k*6)).atZone(zone).format(Hm)
            val tw=p.measureText(s)
            cv.drawText(s,(x(slot(k*6))-tw/2).coerceIn(0f,(wPx-tw).coerceAtLeast(0f)),hPx-2*dn,p)
        }
        val symbol=(18*dn).roundToInt()
        day.hours.forEach { h ->
            val code=h.v("weather_code")
            if(code.isFinite() && hourOf(h)%3==0) cv.drawBitmap(widgetSymbolBitmap(code.toInt(),h.v("is_day")==1.0,symbol),(x(h.time)-symbol/2f).coerceIn(0f,(wPx-symbol).toFloat().coerceAtLeast(0f)),badge+(top-badge-symbol)/2,null)
        }
        // The now cursor of the app: a line through the chart and a badge with the word and the time.
        val now=Instant.now().epochSecond
        if(now>=day.start && now<day.end) {
            val xx=x(now);val s=uiText(R.string.ui_teraz_e6989b57).trim()+" "+Instant.ofEpochSecond(now).atZone(zone).format(Hm)
            val tw=p.measureText(s);val pad=3*dn
            val bx=(xx-tw/2).coerceIn(pad,(wPx-tw-pad).coerceAtLeast(pad))
            line(TextColor,xx,badge,xx,bottom,1f*dn)
            p.style=Paint.Style.FILL;p.color=0xFF111A24.toInt();cv.drawRoundRect(bx-pad,0f,bx+tw+pad,badge,3*dn,3*dn,p)
            p.color=TextColor;cv.drawText(s,bx,badge-3.5f*dn,p)
        }
        return bmp
    }
}

class DayHoursWidget:AppWidgetProvider() {
    override fun onReceive(context:Context,intent:Intent) {
        val id=intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID)
        when(intent.action) {
            DayWidgets.ActionRefresh->DayWidgets.refresh(context,true)
            DayWidgets.ActionPrev->runCatching { DayWidgets.draw(context,id,-1) }
            DayWidgets.ActionNext->runCatching { DayWidgets.draw(context,id,1) }
            else->super.onReceive(context,intent)
        }
    }
    override fun onUpdate(context:Context,manager:AppWidgetManager,ids:IntArray) {
        ids.forEach { id -> runCatching { DayWidgets.draw(context,id) } }
        DayWidgets.refresh(context);DayWidgets.schedule(context)
    }
    override fun onAppWidgetOptionsChanged(context:Context,manager:AppWidgetManager,id:Int,options:Bundle) { runCatching { DayWidgets.draw(context,id) } }
    override fun onDeleted(context:Context,ids:IntArray) { DayWidgets.remove(context,ids) }
    override fun onDisabled(context:Context) { DayWidgets.cancel(context) }
}

internal class DayWidgetWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        val c=applicationContext;val force=inputData.getBoolean("force",false)
        if(force) NetCache.forceNetwork=true
        try {
            for(id in DayWidgets.ids(c)) {
                val l=DayWidgets.place(c,id)
                if(l!=null && (force || DayWidgets.stale(c,id,l))) try { DayWidgets.download(c,id,l) } catch(e:CancellationException) { throw e } catch(e:Exception) { }
                try { DayWidgets.draw(c,id) } catch(e:CancellationException) { throw e } catch(e:Exception) { }
            }
        } finally { if(force) NetCache.forceNetwork=false }
        return Result.success()
    }
}

private fun dayWidgetRowLabel(key:String):String = uiText(when(key) {
    "temp"->R.string.ui_teplota_4b720579;"rain"->R.string.ui_zrazky_66d17319;"prob"->R.string.ui_pravdepodobnost_zrazok_c9b0a84a
    "wind"->R.string.ui_vietor_8a3ebf26;"gust"->R.string.ui_narazovy_vietor_ca701315;"direction"->R.string.ui_smer_vetra_f7a54eb7
    "cloud"->R.string.ui_oblacnost_b5a6e70c;"uv"->R.string.ui_uv_027fcbbc;"humidity"->R.string.ui_vlhkost_e06c751e
    "dew"->R.string.ui_rosny_bod_6caea4c6;"visibility"->R.string.ui_viditelnost_7f90db41;else->R.string.ui_tlak_vzduchu_cc4c1846
})

class DayWidgetConfigActivity:ComponentActivity() {
    @OptIn(ExperimentalLayoutApi::class)
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val id=intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED,Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id))
        if(id==AppWidgetManager.INVALID_APPWIDGET_ID) { finish();return }
        val locations=LocationStore(this).load()
        val keys=listOf("active")+locations.map { BackgroundRefresh.locationKey(it) }
        val names=listOf(uiText(R.string.wg_loc_active))+locations.map { it.name }
        val startLoc=DayWidgets.loc(this,id);val startRows=DayWidgets.rows(this,id)
        setContent {
            PerunTheme {
                Surface(Modifier.fillMaxSize(),color=Color(0xFF151A22),contentColor=Color(0xFFE8EAF0)) {
                    var loc by remember { mutableStateOf(startLoc) }
                    var rows by remember { mutableStateOf(startRows) }
                    val muted=Color(0xFFBBC4D2)
                    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Text(uiText(R.string.wg_config_title),style=MaterialTheme.typography.titleLarge,color=Color(0xFFE8EAF0))
                        Text(uiText(R.string.wg_location),style=MaterialTheme.typography.labelLarge,color=muted)
                        PerunChips(names,keys.indexOf(loc).coerceAtLeast(0),{ loc=keys[it] })
                        Text(uiText(R.string.wg_day_rows),style=MaterialTheme.typography.labelLarge,color=muted)
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            DayWidgetRows.forEach { key ->
                                PerunChip(dayWidgetRowLabel(key),key in rows) { rows=if(key in rows) rows-key else DayWidgetRows.filter { it in rows || it==key } }
                            }
                        }
                        Text(uiText(R.string.wg_day_rows_note),style=MaterialTheme.typography.bodySmall,color=muted)
                        Button(onClick={
                            DayWidgets.write(this@DayWidgetConfigActivity,id,loc,rows.ifEmpty { DayWidgetRows })
                            runCatching { DayWidgets.draw(this@DayWidgetConfigActivity,id) }
                            DayWidgets.refresh(this@DayWidgetConfigActivity)
                            DayWidgets.schedule(this@DayWidgetConfigActivity)
                            setResult(RESULT_OK,Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id))
                            finish()
                        },modifier=Modifier.fillMaxWidth()) { Text(uiText(R.string.wg_save)) }
                    }
                }
            }
        }
    }
}
