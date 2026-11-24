package sk.perun.weather.ui

import sk.perun.weather.R

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import android.widget.RemoteViews
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** loc is "active" (follows the selected place) or a BackgroundRefresh.locationKey. Event rules keep the forecast window in hours as threshold. */
internal data class NotifyRule(val id:String,val metric:String,val above:Boolean,val threshold:Double,val loc:String="active")
internal data class NotificationSettings(
    val enabled:Boolean,val current:Boolean,val alerts:Boolean,val alertLevel:String,val alertScope:String,
    val rules:List<NotifyRule>,val daily:Boolean,val dailyHour:Int,val tomorrow:Boolean,val tomorrowHour:Int,
    val alertLocations:Set<String> = emptySet(),
    val currentParams:List<String> = listOf("wind","rain","uv"),
    val status:Boolean=false,val statusParam:String="feels",val statusLoc:String="active",
    val dailyLoc:String="active",val tomorrowLoc:String="active",
)
private const val K_TEMP=0
private const val K_WIND=1
private const val K_RAIN=2
private const val K_SNOW=3
private const val K_PRESSURE=4
private const val K_PERCENT=5
private const val K_PLAIN=6
private const val K_KM=7
private const val K_GAS=8
private const val K_DUST=9
private const val K_POLLEN=10
private const val K_EVENT=11
private const val G_WEATHER=0
private const val G_AIR=1
private const val G_POLLEN=2
private const val G_EVENTS=3
/** A rule metric: stored thresholds are canonical (°C, km/h, mm, cm, hPa, m, µg/m³, grains/m³); the UI converts to the chosen units. */
internal class RuleMetric(val key:String,val kind:Int,val group:Int,val decimals:Int,val band:Double,val label:()->String)

private val SunYellow=0xFFFFD166.toInt()
private val MoonBlue=0xFF8FA8FF.toInt()
private val CloudLight=0xFFDCE3EE.toInt()
private val CloudGrey=0xFF9FB0C7.toInt()
private val CloudDark=0xFF6F7F96.toInt()
private val DropBlue=0xFF4FC3F7.toInt()
private val AirKeys=listOf("european_aqi","us_aqi","pm2_5","pm10","nitrogen_dioxide","ozone","sulphur_dioxide","carbon_monoxide","alder_pollen","birch_pollen","grass_pollen","mugwort_pollen","olive_pollen","ragweed_pollen")
private val Hm:DateTimeFormatter=DateTimeFormatter.ofPattern("HH:mm")
private val Dm:DateTimeFormatter=DateTimeFormatter.ofPattern(appPattern("d. M. HH:mm"),appLocale())
private val CellIcon=intArrayOf(R.id.nt_c0i,R.id.nt_c1i,R.id.nt_c2i)
private val CellValue=intArrayOf(R.id.nt_c0v,R.id.nt_c1v,R.id.nt_c2v)
private val HourTime=intArrayOf(R.id.nt_h0t,R.id.nt_h1t,R.id.nt_h2t,R.id.nt_h3t,R.id.nt_h4t,R.id.nt_h5t)
private val HourIcon=intArrayOf(R.id.nt_h0i,R.id.nt_h1i,R.id.nt_h2i,R.id.nt_h3i,R.id.nt_h4i,R.id.nt_h5i)
private val HourTemp=intArrayOf(R.id.nt_h0v,R.id.nt_h1v,R.id.nt_h2v,R.id.nt_h3v,R.id.nt_h4v,R.id.nt_h5v)
private val HourProb=intArrayOf(R.id.nt_h0p,R.id.nt_h1p,R.id.nt_h2p,R.id.nt_h3p,R.id.nt_h4p,R.id.nt_h5p)
private class HourCell(val time:String,val icon:Bitmap,val temp:String,val prob:String)
/** WMO weather codes per forecast phenomenon (model forecast, not observation). */
private val EventCodes=mapOf(
    "ev_storm" to setOf(95,96,99),"ev_hail" to setOf(96,99),"ev_fog" to setOf(45,48),
    "ev_snow" to setOf(71,73,75,77,85,86),"ev_freezing" to setOf(56,57,66,67),"ev_heavy" to setOf(65,82),
)
private fun glyphBitmap(param:String,color:Int,size:Int):Bitmap = widgetIconBitmap(param,color,size)

/** Pictograms are drawn with the platform canvas, so notifications need no extra image assets. */
private fun weatherBitmap(code:Int,day:Boolean,size:Int):Bitmap {
    val bmp=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);val cv=Canvas(bmp);val s=size.toFloat()
    val p=Paint(Paint.ANTI_ALIAS_FLAG)
    fun fill(color:Int) {p.style=Paint.Style.FILL;p.color=color}
    fun sun(cx:Float,cy:Float,r:Float) {
        fill(SunYellow);cv.drawCircle(cx,cy,r,p)
        p.style=Paint.Style.STROKE;p.strokeWidth=r*.3f;p.strokeCap=Paint.Cap.ROUND
        for(k in 0 until 8) {val a=Math.toRadians(k*45.0);val ca=cos(a).toFloat();val sa=sin(a).toFloat();cv.drawLine(cx+ca*r*1.5f,cy+sa*r*1.5f,cx+ca*r*1.9f,cy+sa*r*1.9f,p)}
    }
    fun moon(cx:Float,cy:Float,r:Float) {
        fill(MoonBlue)
        val a=Path().apply {addCircle(cx,cy,r,Path.Direction.CW)}
        val b=Path().apply {addCircle(cx+r*.55f,cy-r*.3f,r*.85f,Path.Direction.CW)}
        a.op(b,Path.Op.DIFFERENCE);cv.drawPath(a,p)
    }
    fun cloud(x:Float,y:Float,w:Float,color:Int) {
        fill(color);val h=w*.6f
        cv.drawCircle(x+w*.30f,y+h*.60f,h*.30f,p);cv.drawCircle(x+w*.52f,y+h*.42f,h*.40f,p);cv.drawCircle(x+w*.74f,y+h*.62f,h*.28f,p)
        cv.drawRoundRect(RectF(x+w*.10f,y+h*.58f,x+w*.90f,y+h*.92f),h*.17f,h*.17f,p)
    }
    fun drops(x:Float,y:Float,w:Float) {
        p.style=Paint.Style.STROKE;p.color=DropBlue;p.strokeWidth=w*.07f;p.strokeCap=Paint.Cap.ROUND
        for(k in 0 until 3) {val dx=x+w*(.28f+.22f*k);cv.drawLine(dx,y,dx-w*.06f,y+w*.16f,p)}
    }
    fun flakes(x:Float,y:Float,w:Float) {
        fill(0xFFFFFFFF.toInt())
        for(k in 0 until 3) cv.drawCircle(x+w*(.28f+.22f*k),y+w*(.06f+.07f*(k%2)),w*.04f,p)
    }
    fun bolt(x:Float,y:Float,w:Float) {
        fill(SunYellow)
        val b=Path().apply {moveTo(x+w*.52f,y);lineTo(x+w*.36f,y+w*.20f);lineTo(x+w*.48f,y+w*.20f);lineTo(x+w*.40f,y+w*.38f);lineTo(x+w*.64f,y+w*.14f);lineTo(x+w*.52f,y+w*.14f);close()}
        cv.drawPath(b,p)
    }
    fun fog(x:Float,y:Float,w:Float) {
        p.style=Paint.Style.STROKE;p.color=CloudGrey;p.strokeWidth=w*.07f;p.strokeCap=Paint.Cap.ROUND
        for(k in 0 until 3) cv.drawLine(x+w*.15f,y+w*.10f*k,x+w*.85f,y+w*.10f*k,p)
    }
    when {
        code==0 -> {if(day) sun(.5f*s,.5f*s,.2f*s) else moon(.5f*s,.5f*s,.3f*s)}
        code in 1..2 -> {if(day) sun(.38f*s,.36f*s,.15f*s) else moon(.36f*s,.36f*s,.22f*s);cloud(.16f*s,.34f*s,.76f*s,CloudLight)}
        code==45 || code==48 -> {cloud(.12f*s,.10f*s,.76f*s,CloudGrey);fog(.12f*s,.62f*s,.76f*s)}
        code>=95 -> {cloud(.10f*s,.08f*s,.80f*s,CloudDark);bolt(.10f*s,.50f*s,.80f*s)}
        code in setOf(71,73,75,77,85,86) -> {cloud(.10f*s,.12f*s,.80f*s,CloudLight);flakes(.10f*s,.62f*s,.80f*s)}
        code>=51 -> {cloud(.10f*s,.12f*s,.80f*s,CloudGrey);drops(.10f*s,.60f*s,.80f*s)}
        else -> {cloud(.10f*s,.25f*s,.80f*s,CloudGrey)}
    }
    return bmp
}
/** Status-bar icon showing a short value; the system uses only its alpha channel. */
private fun textIcon(text:String):IconCompat {
    val size=96;val bmp=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);val cv=Canvas(bmp)
    val p=Paint(Paint.ANTI_ALIAS_FLAG);p.color=0xFFFFFFFF.toInt();p.typeface=Typeface.create(Typeface.DEFAULT,Typeface.BOLD);p.textAlign=Paint.Align.CENTER
    var ts=82f;p.textSize=ts
    while(p.measureText(text)>size-4 && ts>24f) {ts-=4f;p.textSize=ts}
    val fm=p.fontMetrics
    cv.drawText(text,size/2f,size/2f-(fm.ascent+fm.descent)/2f,p)
    return IconCompat.createWithBitmap(bmp)
}
private fun smallView(c:Context,icon:Bitmap,title:String,text:String,temp:String?):RemoteViews {
    val v=RemoteViews(c.packageName,R.layout.nt_small)
    v.setImageViewBitmap(R.id.nt_icon,icon);v.setTextViewText(R.id.nt_title,title);v.setTextViewText(R.id.nt_text,text)
    if(temp==null) v.setViewVisibility(R.id.nt_temp,View.GONE) else v.setTextViewText(R.id.nt_temp,temp)
    return v
}
private fun bigView(c:Context,icon:Bitmap,title:String,text:String,temp:String,cells:List<Pair<Bitmap,String>>,hours:List<HourCell>,foot:String?):RemoteViews {
    val v=RemoteViews(c.packageName,R.layout.nt_big)
    v.setImageViewBitmap(R.id.nt_icon,icon);v.setTextViewText(R.id.nt_title,title);v.setTextViewText(R.id.nt_text,text);v.setTextViewText(R.id.nt_temp,temp)
    cells.take(3).forEachIndexed {i,cell->v.setImageViewBitmap(CellIcon[i],cell.first);v.setTextViewText(CellValue[i],cell.second)}
    if(hours.isEmpty()) v.setViewVisibility(R.id.nt_hours,View.GONE) else hours.take(6).forEachIndexed {i,h->
        v.setTextViewText(HourTime[i],h.time);v.setImageViewBitmap(HourIcon[i],h.icon);v.setTextViewText(HourTemp[i],h.temp);v.setTextViewText(HourProb[i],h.prob)
    }
    if(foot.isNullOrBlank()) v.setViewVisibility(R.id.nt_foot,View.GONE) else v.setTextViewText(R.id.nt_foot,foot)
    return v
}
/** Alert row: icon chosen by phenomenon and tinted by warning level; text keeps the system colour. */
private fun alertView(c:Context,icon:Bitmap,line:String,detail:String?):RemoteViews {
    val v=RemoteViews(c.packageName,R.layout.nt_alert)
    v.setImageViewBitmap(R.id.nt_icon,icon);v.setTextViewText(R.id.nt_title,line)
    if(detail.isNullOrBlank()) v.setViewVisibility(R.id.nt_detail,View.GONE) else v.setTextViewText(R.id.nt_detail,detail)
    return v
}
private fun alertColor(level:String):Int = when(level) {"red"->0xFFE53935.toInt();"orange"->0xFFFB8C00.toInt();"yellow"->0xFFF2B600.toInt();else->0xFF8294AD.toInt()}
/** The warning icon of the app (AlertHazards.kt) in the colour of the level. */
private fun alertIcon(event:String,color:Int,size:Int,awareness:String=""):Bitmap = alertHazardBitmap(alertHazard(event,awareness),color,size)
private fun levelName(level:String):String = when(level) {"red"->uiText(R.string.ui_cervena_f1d8f5c8);"orange"->uiText(R.string.ui_oranzova_ab35bb41);"yellow"->uiText(R.string.ui_zlta_64273c3a);else->uiText(R.string.ui_informacia_b277d884)}
private fun localAlertName(event:String):String = when(event) {
    "Thunderstorm risk"->uiText(R.string.ui_riziko_burok_8fa6514a);"Strong wind gusts"->uiText(R.string.ui_silne_narazy_vetra_7b7b006f);"Heavy precipitation"->uiText(R.string.ui_silne_zrazky_2630cc03)
    "Extreme heat"->uiText(R.string.ui_extremne_teplo_cdcd5041);"Extreme cold"->uiText(R.string.ui_extremny_chlad_d4aeb237);else->event
}
private fun locMatch(sel:String,key:String,active:Boolean):Boolean = sel.split(";").any { if(it=="active") active else it==key }

/** Notifications are produced only by background work; rule thresholds are stored in canonical units. */
internal object Notifier {
    const val PrefsName="perun_notifications"
    val Specs:List<RuleMetric> = listOf(
        RuleMetric("temp",K_TEMP,G_WEATHER,0,1.0) {uiText(R.string.nt_metric_temp)},
        RuleMetric("feels",K_TEMP,G_WEATHER,0,1.0) {LongMetric.Feels.label},
        RuleMetric("precip",K_RAIN,G_WEATHER,1,0.2) {uiText(R.string.nt_metric_precip)},
        RuleMetric("snow",K_SNOW,G_WEATHER,1,0.2) {uiText(R.string.nt_metric_snow)},
        RuleMetric("prob",K_PERCENT,G_WEATHER,0,5.0) {uiText(R.string.nt_metric_prob)},
        RuleMetric("wind",K_WIND,G_WEATHER,0,2.0) {uiText(R.string.nt_metric_wind)},
        RuleMetric("gust",K_WIND,G_WEATHER,0,2.0) {uiText(R.string.nt_metric_gust)},
        RuleMetric("cloud",K_PERCENT,G_WEATHER,0,5.0) {LongMetric.Cloud.label},
        RuleMetric("uv",K_PLAIN,G_WEATHER,1,0.5) {uiText(R.string.nt_metric_uv)},
        RuleMetric("humidity",K_PERCENT,G_WEATHER,0,5.0) {LongMetric.Humidity.label},
        RuleMetric("dew",K_TEMP,G_WEATHER,0,1.0) {LongMetric.Dew.label},
        RuleMetric("visibility",K_KM,G_WEATHER,1,500.0) {LongMetric.Visibility.label},
        RuleMetric("pressure",K_PRESSURE,G_WEATHER,0,1.0) {LongMetric.Pressure.label},
        RuleMetric("aqi",K_PLAIN,G_AIR,0,5.0) {uiText(R.string.nt_metric_aqi)},
        RuleMetric("pm2_5",K_DUST,G_AIR,1,2.0) {uiText(R.string.nt_metric_pm25)},
        RuleMetric("pm10",K_DUST,G_AIR,1,3.0) {uiText(R.string.nt_metric_pm10)},
        RuleMetric("nitrogen_dioxide",K_GAS,G_AIR,1,5.0) {uiText(R.string.nt_metric_no2)},
        RuleMetric("ozone",K_GAS,G_AIR,1,5.0) {uiText(R.string.nt_metric_o3)},
        RuleMetric("sulphur_dioxide",K_GAS,G_AIR,1,5.0) {uiText(R.string.nt_metric_so2)},
        RuleMetric("carbon_monoxide",K_GAS,G_AIR,1,100.0) {uiText(R.string.nt_metric_co)},
        RuleMetric("alder_pollen",K_POLLEN,G_POLLEN,0,5.0) {PollenNames["alder"] ?: "alder"},
        RuleMetric("birch_pollen",K_POLLEN,G_POLLEN,0,5.0) {PollenNames["birch"] ?: "birch"},
        RuleMetric("grass_pollen",K_POLLEN,G_POLLEN,0,5.0) {PollenNames["grass"] ?: "grass"},
        RuleMetric("mugwort_pollen",K_POLLEN,G_POLLEN,0,5.0) {PollenNames["mugwort"] ?: "mugwort"},
        RuleMetric("ragweed_pollen",K_POLLEN,G_POLLEN,0,5.0) {PollenNames["ragweed"] ?: "ragweed"},
        RuleMetric("olive_pollen",K_POLLEN,G_POLLEN,0,5.0) {PollenNames["olive"] ?: "olive"},
        RuleMetric("ev_storm",K_EVENT,G_EVENTS,0,0.0) {uiText(R.string.nt_ev_storm)},
        RuleMetric("ev_hail",K_EVENT,G_EVENTS,0,0.0) {uiText(R.string.nt_ev_hail)},
        RuleMetric("ev_fog",K_EVENT,G_EVENTS,0,0.0) {uiText(R.string.nt_ev_fog)},
        RuleMetric("ev_snow",K_EVENT,G_EVENTS,0,0.0) {uiText(R.string.nt_ev_snow)},
        RuleMetric("ev_freezing",K_EVENT,G_EVENTS,0,0.0) {uiText(R.string.nt_ev_freezing)},
        RuleMetric("ev_heavy",K_EVENT,G_EVENTS,0,0.0) {uiText(R.string.nt_ev_heavy)},
    )
    val CurrentOptions=listOf("wind","gust","rain","prob","uv","humidity","pressure","cloud","visibility","dew","feels")
    val StatusOptions=listOf("feels","precip","snow","prob","wind","gust","cloud","uv","humidity","dew","pressure","visibility","aqi","pm2_5","pm10","nitrogen_dioxide","ozone","sulphur_dioxide","carbon_monoxide","alder_pollen","birch_pollen","grass_pollen","mugwort_pollen","ragweed_pollen","olive_pollen")
    /** Group headers (index of the first option in a group) for the status-bar picker. */
    fun statusHeaders():Map<Int,String> {
        val out=LinkedHashMap<Int,String>();var last=-1
        StatusOptions.forEachIndexed { i,key -> val g=spec(key).group;if(g!=last) { out[i]=uiText(when(g) { G_AIR->R.string.nt_group_air2;G_POLLEN->R.string.nt_group_pollen;else->R.string.nt_group_weather });last=g } }
        return out
    }
    val EventWindows=listOf(1,3,6)
    val Levels=listOf("yellow","orange","red")
    val DailyHours=listOf(5,6,7,8,9)
    val TomorrowHours=listOf(17,18,19,20,21)
    private const val ChCurrent="perun_current"
    private const val ChAlerts="perun_alerts"
    private const val ChRules="perun_rules"
    private const val ChSummary="perun_summary"
    private const val SummaryWork="perun-summary-"
    private const val IdCurrent=1
    private const val IdStatus=5

    fun prefs(c:Context):SharedPreferences = c.applicationContext.getSharedPreferences(PrefsName,Context.MODE_PRIVATE)
    fun read(p:SharedPreferences):NotificationSettings {
        val rules=runCatching {
            val a=JSONArray(p.getString("rules","[]") ?: "[]")
            (0 until a.length()).map { val o=a.getJSONObject(it);NotifyRule(o.getString("id"),o.getString("metric"),o.getBoolean("above"),o.getDouble("threshold"),o.optString("loc","active").ifBlank { "active" }) }
        }.getOrDefault(emptyList())
        val defaults=listOf("wind","rain","uv")
        val params=(p.getString("curParams","") ?: "").split(",").map { it.trim() }.filter { it in CurrentOptions }.take(3)
        val padded=(params+defaults.filter { it !in params }).take(3)
        return NotificationSettings(
            p.getBoolean("enabled",false),p.getBoolean("current",false),p.getBoolean("alerts",true),
            (p.getString("alertLevel","yellow") ?: "yellow").takeIf { it in Levels } ?: "yellow",(p.getString("alertScope","active") ?: "active").takeIf { it in listOf("active","all","custom") } ?: "active",
            rules,p.getBoolean("daily",false),p.getInt("dailyHour",7),p.getBoolean("tomorrow",false),p.getInt("tomorrowHour",18),
            p.getStringSet("alertLocations",null)?.toSet() ?: emptySet(),
            padded,p.getBoolean("status",false),(p.getString("statusParam","feels") ?: "feels").takeIf { it in StatusOptions } ?: "feels",
            p.getString("statusLoc","active") ?: "active",p.getString("dailyLoc","active") ?: "active",p.getString("tomorrowLoc","active") ?: "active",
        )
    }
    fun writeRules(p:SharedPreferences,rules:List<NotifyRule>) {
        val a=JSONArray();rules.forEach { a.put(JSONObject().put("id",it.id).put("metric",it.metric).put("above",it.above).put("threshold",it.threshold).put("loc",it.loc)) }
        p.edit().putString("rules",a.toString()).apply()
    }
    /** True when something enabled here can only work through background refreshes. */
    fun needsBackground(c:Context):Boolean { val s=read(prefs(c));return s.enabled && (s.current || s.alerts || s.rules.isNotEmpty() || s.status) }
    /** Locations the worker must load: the refresh scope plus every place a notification is configured for. */
    fun workerLocations(c:Context,base:List<Location>):List<Location> {
        val s=read(prefs(c))
        if(!s.enabled) return base
        val store=LocationStore(c);val all=store.load()
        val keys=HashSet<String>();var activeNeeded=s.current || s.alerts
        s.rules.forEach { r -> r.loc.split(";").forEach { if(it=="active") activeNeeded=true else keys+=it } }
        if(s.status) { if(s.statusLoc=="active") activeNeeded=true else keys+=s.statusLoc }
        val extra=ArrayList<Location>()
        if(activeNeeded) (all.getOrNull(store.loadSelected()) ?: all.firstOrNull())?.let { extra+=it }
        extra+=all.filter { BackgroundRefresh.locationKey(it) in keys }
        if(s.alerts) when(s.alertScope) {
            "all"->extra+=all
            "custom"->extra+=all.filter { BackgroundRefresh.locationKey(it) in s.alertLocations }
        }
        return (base+extra).distinctBy { BackgroundRefresh.locationKey(it) }
    }

    fun spec(key:String):RuleMetric = Specs.firstOrNull { it.key==key } ?: Specs.first()
    fun metricLabel(key:String):String = spec(key).label()
    fun isEvent(key:String):Boolean = spec(key).kind==K_EVENT
    fun display(m:RuleMetric,v:Double,u:WeatherUnits,gas:AirGasUnit):Double = when(m.kind) {
        K_TEMP->u.temperature.fromCelsius(v);K_WIND->u.wind.fromKmh(v);K_RAIN->u.precipitation.fromMillimetres(v);K_SNOW->u.precipitation.fromSnowCentimetres(v)
        K_PRESSURE->u.pressure.fromHpa(v);K_KM->v/1000.0;K_GAS->airConvert(m.key,v,gas);else->v
    }
    fun toCanonical(m:RuleMetric,x:Double,u:WeatherUnits,gas:AirGasUnit):Double = when(m.kind) {
        K_TEMP->if(u.temperature==TemperatureUnit.Fahrenheit) (x-32.0)/1.8 else x
        K_WIND->x/u.wind.fromKmh(1.0);K_RAIN->x/u.precipitation.fromMillimetres(1.0);K_SNOW->x/u.precipitation.fromSnowCentimetres(1.0)
        K_PRESSURE->x/u.pressure.fromHpa(1.0);K_KM->x*1000.0;K_GAS->x/airConvert(m.key,1.0,gas);else->x
    }
    fun unitOf(m:RuleMetric,u:WeatherUnits,gas:AirGasUnit):String = when(m.kind) {
        K_TEMP->u.temperature.symbol;K_WIND->u.wind.symbol;K_RAIN->u.precipitation.liquidSymbol+"/h";K_SNOW->u.precipitation.snowSymbol+"/h";K_PRESSURE->u.pressure.symbol
        K_PERCENT->"%";K_KM->"km";K_GAS->airUnitLabel(m.key,gas);K_DUST->"µg/m³";K_POLLEN->uiText(R.string.nt_unit_pollen);else->""
    }
    private fun decimals(m:RuleMetric,u:WeatherUnits,gas:AirGasUnit):Int = when {
        m.kind==K_PRESSURE->if(u.pressure==PressureUnit.InchesMercury) 2 else 0
        m.kind==K_RAIN || m.kind==K_SNOW->if(u.precipitation==PrecipitationUnit.Imperial) 2 else 1
        m.kind==K_GAS && m.key=="carbon_monoxide" && gas==AirGasUnit.Ppb->2
        else->m.decimals
    }
    fun valueText(m:RuleMetric,v:Double,u:WeatherUnits,gas:AirGasUnit,compact:Boolean=false):String {
        val x=display(m,v,u,gas);val unit=unitOf(m,u,gas);val dec=decimals(m,u,gas)
        val shown=if(compact) weatherUnitNumber(x,if(x.isFinite() && abs(x-x.roundToInt())<0.05) 0 else maxOf(dec,1)) else weatherUnitNumber(x,dec)
        return shown+(if(unit.isEmpty()) "" else " "+unit)
    }
    fun ruleLabel(r:NotifyRule,u:WeatherUnits,gas:AirGasUnit):String =
        if(isEvent(r.metric)) metricLabel(r.metric)+" · "+uiText(R.string.nt_within_hours,r.threshold.toInt())
        else metricLabel(r.metric)+(if(r.above) " ≥ " else " ≤ ")+valueText(spec(r.metric),r.threshold,u,gas,true)
    fun paramLabel(key:String):String = when(key) {"rain"->LongMetric.Rain.label;"prob"->uiText(R.string.nt_metric_prob);else->metricLabel(key)}

    fun ensureChannels(c:Context) {
        val nm=c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(ChCurrent,uiText(R.string.nt_channel_current),NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(ChAlerts,uiText(R.string.nt_channel_alerts),NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(ChRules,uiText(R.string.nt_channel_rules),NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(ChSummary,uiText(R.string.nt_channel_summary),NotificationManager.IMPORTANCE_DEFAULT))
    }
    fun permitted(c:Context):Boolean =
        ContextCompat.checkSelfPermission(c,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED && NotificationManagerCompat.from(c).areNotificationsEnabled()

    /** small==null uses the system template (BigTextStyle); otherwise the custom views sit inside the system frame. */
    private fun show(c:Context,channel:String,id:Int,title:String,text:String,small:RemoteViews?,big:RemoteViews?,ongoing:Boolean=false,icon:IconCompat?=null,target:String="overview",location:String="",detail:String=text,color:Int=0xFF4FC3F7.toInt()) {
        if(ContextCompat.checkSelfPermission(c,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) return
        val launch=c.packageManager.getLaunchIntentForPackage(c.packageName)?.apply {
            data=android.net.Uri.parse("perun://notification/"+id+"/"+android.net.Uri.encode(location))
            putExtra("perun_route",target);putExtra("perun_location",location)
            addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pending=launch?.let { PendingIntent.getActivity(c,id,it,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE) }
        val b=NotificationCompat.Builder(c,channel)
        if(icon!=null) b.setSmallIcon(icon) else b.setSmallIcon(R.drawable.ic_notification)
        b.setContentTitle(title).setContentText(text).setColor(color)
            .setContentIntent(pending).setOngoing(ongoing).setOnlyAlertOnce(ongoing).setAutoCancel(!ongoing)
        if(small!=null) {
            b.setStyle(NotificationCompat.DecoratedCustomViewStyle()).setCustomContentView(small)
            if(big!=null) b.setCustomBigContentView(big)
        } else b.setStyle(NotificationCompat.BigTextStyle().bigText(detail))
        try { NotificationManagerCompat.from(c).notify(id,b.build()) } catch(e:SecurityException) { }
    }
    fun cancelCurrent(c:Context) { NotificationManagerCompat.from(c).cancel(IdCurrent) }
    fun cancelStatus(c:Context) { NotificationManagerCompat.from(c).cancel(IdStatus) }

    private fun units(c:Context):WeatherUnits = readForecastPreferences(c.getSharedPreferences("perun_forecast",Context.MODE_PRIVATE)).units
    private fun airSettings(c:Context):Pair<AirQualityScale,AirGasUnit> {
        val p=c.getSharedPreferences("perun_air_detail",Context.MODE_PRIVATE)
        return (AirQualityScale.entries.firstOrNull { it.name==p.getString("scale","") } ?: AirQualityScale.European) to (AirGasUnit.entries.firstOrNull { it.name==p.getString("gasUnit","") } ?: AirGasUnit.Micrograms)
    }
    private fun temp(u:WeatherUnits,v:Double)=weatherUnitNumber(u.temperature.fromCelsius(v),0)+"°"
    private fun wind(u:WeatherUnits,v:Double)=weatherUnitNumber(u.wind.fromKmh(v),0)+" "+u.wind.symbol
    private fun rain(u:WeatherUnits,v:Double)=weatherUnitNumber(u.precipitation.fromMillimetres(v),if(u.precipitation==PrecipitationUnit.Imperial) 2 else 1)+" "+u.precipitation.liquidSymbol
    private fun percent(v:Double)=weatherUnitNumber(v,0)+" %"
    private suspend fun loadAirNow(l:Location):Map<String,Double> = withContext(Dispatchers.IO) {
        try {
            val tz=URLEncoder.encode(l.timezone.ifBlank { "auto" },"UTF-8")
            val root=JSONObject(String(fetchBytes("https://air-quality-api.open-meteo.com/v1/air-quality?latitude=${l.latitude}&longitude=${l.longitude}&timezone=$tz&current="+AirKeys.joinToString(",")),Charsets.UTF_8))
            val cur=root.getJSONObject("current")
            AirKeys.associateWith { cur.optDouble(it,Double.NaN) }
        } catch(e:CancellationException) { throw e } catch(e:Exception) { emptyMap() }
    }
    private fun metricValue(d:OverviewData,ex:OverviewExtra?,air:Map<String,Double>,key:String,scale:AirQualityScale):Double = when(key) {
        "temp"->d.temperature;"feels"->d.apparent;"precip"->d.precipitation;"snow"->d.snowfall;"prob"->d.probability;"wind"->d.wind;"gust"->d.gust;"cloud"->d.cloud;"uv"->d.uv
        "humidity"->ex?.humidity ?: Double.NaN;"dew"->ex?.dewPoint ?: Double.NaN;"visibility"->ex?.visibility ?: Double.NaN;"pressure"->ex?.pressure ?: Double.NaN
        "aqi"->air[scale.apiVariable] ?: d.air?.aqi ?: Double.NaN
        else->air[key] ?: Double.NaN
    }
    private fun paramValue(key:String,d:OverviewData,ex:OverviewExtra?,u:WeatherUnits,gas:AirGasUnit):String = when(key) {
        "rain"->if(d.precipitation.isFinite() && d.precipitation>0.0) rain(u,d.precipitation)+" · "+percent(d.probability) else percent(d.probability)
        "prob"->percent(d.probability)
        else->valueText(spec(key),metricValue(d,ex,emptyMap(),key,AirQualityScale.European),u,gas)
    }
    /** Short value for the status-bar icon; the full value with unit goes to the notification text. */
    private fun statusText(key:String,d:OverviewData,ex:OverviewExtra?,u:WeatherUnits,air:Map<String,Double> = emptyMap(),scale:AirQualityScale=AirQualityScale.European,gas:AirGasUnit=AirGasUnit.Micrograms):String {
        val v=metricValue(d,ex,air,key,scale)
        if(!v.isFinite()) return "–"
        val m=spec(key)
        return when(m.kind) {
            K_TEMP->weatherUnitNumber(u.temperature.fromCelsius(v),0)+"°"
            K_PERCENT->weatherUnitNumber(v,0)+"%"
            K_PLAIN->weatherUnitNumber(v,if(key=="uv" && v<10) 1 else 0)
            K_RAIN,K_SNOW->weatherUnitNumber(display(m,v,u,gas),1)
            K_KM->weatherUnitNumber(v/1000.0,if(v<10000) 1 else 0)
            else->weatherUnitNumber(display(m,v,u,gas),0)
        }
    }
    private fun chartFor(metric:String):String = when(metric) {
        "temp","feels","dew"->"temperature";"precip","snow"->"precipitation";"prob"->"probability";"gust","wind"->"wind";else->metric
    }

    /** Called by RefreshWorker for every refreshed location. */
    suspend fun onOverview(c:Context,l:Location,d:OverviewData,ex:OverviewExtra?,active:Boolean) {
        val p=prefs(c);val s=read(p)
        if(!s.enabled) return
        val u=units(c);val key=BackgroundRefresh.locationKey(l);val (scale,gas)=airSettings(c)
        val statusHere=s.status && locMatch(s.statusLoc,key,active)
        val merged=statusHere && s.current && active
        val statusAir=if(statusHere && spec(s.statusParam).group.let { it==G_AIR || it==G_POLLEN }) loadAirNow(l) else emptyMap()
        val statusValue=if(statusHere) statusText(s.statusParam,d,ex,u,statusAir,scale,gas) else null
        if(s.current && active) postCurrent(c,l,d,ex,u,gas,s.currentParams,if(merged) statusValue else null)
        if(merged) cancelStatus(c) else if(statusHere) postStatus(c,l,d,ex,u,s.statusParam,statusValue ?: "–",statusAir,scale,gas)
        val alertsHere=when(s.alertScope) {"all"->true;"custom"->key in s.alertLocations;else->active}
        if(s.alerts && alertsHere) {
            val sent=(p.getStringSet("sentAlerts",null) ?: emptySet()).toMutableSet()
            val min=Levels.indexOf(s.alertLevel);var changed=false
            d.alerts.forEach { a ->
                if(Levels.indexOf(a.level)<min) return@forEach
                val id=key+"|"+a.identifier.ifBlank { a.event+"|"+a.level }
                if(sent.add(id)) { changed=true;postAlert(c,l,d,a,2000+(id.hashCode() and 0xFFFF)) }
            }
            if(changed) p.edit().putStringSet("sentAlerts",sent.toList().takeLast(300).toSet()).apply()
        }
        val rules=s.rules.filter { locMatch(it.loc,key,active) }
        if(rules.isNotEmpty()) {
            val air=if(rules.any { spec(it.metric).group==G_AIR || spec(it.metric).group==G_POLLEN }) loadAirNow(l) else emptyMap()
            val ed=p.edit()
            rules.forEach { r ->
                val m=spec(r.metric);val flag="rule_"+r.id+"_"+key
                val was=p.getBoolean(flag,false)
                if(m.kind==K_EVENT) {
                    val codes=EventCodes[r.metric] ?: emptySet();val h=r.threshold.toInt().coerceIn(1,6)
                    val now=d.code in codes || d.hours.take(h).any { it.code in codes }
                    if(now!=was) ed.putBoolean(flag,now)
                    if(now && !was) postEvent(c,l,r,m,h)
                    return@forEach
                }
                val v=metricValue(d,ex,air,r.metric,scale);if(!v.isFinite()) return@forEach
                // Hysteresis: a rule re-arms only after the value moves back by 10 % of the threshold (at least the metric's own minimum band).
                val band=maxOf(abs(r.threshold)*0.1,m.band)
                val now=if(r.above) (if(was) v>r.threshold-band else v>=r.threshold) else (if(was) v<r.threshold+band else v<=r.threshold)
                if(now!=was) ed.putBoolean(flag,now)
                if(now && !was) postRule(c,l,r,m,v,u,gas)
            }
            ed.apply()
        }
    }

    private fun postCurrent(c:Context,l:Location,d:OverviewData,ex:OverviewExtra?,u:WeatherUnits,gas:AirGasUnit,params:List<String>,statusParam:String?) {
        val cond=overviewCondition(d.code);val t=temp(u,d.temperature)
        val fg=ContextCompat.getColor(c,R.color.nt_system_text)
        val icon=weatherBitmap(d.code,d.isDayFlag,96)
        val small=smallView(c,icon,l.name,cond+" · "+uiText(R.string.nt_feels,temp(u,d.apparent)),t)
        val cells=params.map { glyphBitmap(it,fg,48) to paramValue(it,d,ex,u,gas) }
        val hours=d.hours.map { h -> HourCell(h.time.format(Hm),weatherBitmap(h.code,h.time.hour in 7..19,56),temp(u,h.temperature),if(h.probability.isFinite() && h.probability>=10) percent(h.probability) else "") }
        val big=bigView(c,icon,l.name,cond,t,cells,hours,uiText(R.string.nt_updated,d.time.format(Hm)))
        val bar=textIcon(statusParam ?: t)
        show(c,ChCurrent,IdCurrent,l.name+" · "+t,cond,small,big,ongoing=true,icon=bar,location=BackgroundRefresh.locationKey(l))
    }
    private fun postStatus(c:Context,l:Location,d:OverviewData,ex:OverviewExtra?,u:WeatherUnits,param:String,short:String,air:Map<String,Double>,scale:AirQualityScale,gas:AirGasUnit) {
        val v=metricValue(d,ex,air,param,scale)
        val text=paramLabel(param)+": "+(if(v.isFinite()) valueText(spec(param),v,u,gas) else "–")
        show(c,ChCurrent,IdStatus,l.name+" · "+temp(u,d.temperature),overviewCondition(d.code)+" · "+text,null,null,ongoing=true,icon=textIcon(short),location=BackgroundRefresh.locationKey(l))
    }
    private fun postAlert(c:Context,l:Location,d:OverviewData,a:AlertItem,id:Int) {
        val color=alertColor(a.level);val icon=alertIcon(a.event,color,72,a.awareness)
        val event=if(a.local) localAlertName(a.event) else a.event
        val line=event+" · "+levelName(a.level)+(if(a.local) uiText(R.string.ui_odhad_5dde2d79) else "")
        val zone=d.zone
        val period=when {
            a.start!=null && a.end!=null->uiText(R.string.nt_alert_period,a.start.atZone(zone).format(Dm),a.end.atZone(zone).format(Dm))
            a.end!=null->uiText(R.string.nt_alert_until,a.end.atZone(zone).format(Dm))
            a.start!=null->uiText(R.string.nt_alert_from,a.start.atZone(zone).format(Dm))
            else->""
        }
        val headline=listOf(a.title.takeIf { !a.local && it.isNotBlank() && it!=a.event } ?: "",a.issuer).filter { it.isNotBlank() }.joinToString(" · ")
        val detail=listOf(l.name,period,headline).filter { it.isNotBlank() }.joinToString(" · ")
        show(c,ChAlerts,id,event,line,alertView(c,icon,line,null),alertView(c,icon,line,detail),target="alerts",location=BackgroundRefresh.locationKey(l),detail=detail,color=color)
    }
    private fun postRule(c:Context,l:Location,r:NotifyRule,m:RuleMetric,v:Double,u:WeatherUnits,gas:AirGasUnit) {
        val title=ruleLabel(r,u,gas);val now=uiText(R.string.nt_rule_text,m.label(),valueText(m,v,u,gas))
        val target=if(m.group==G_AIR || m.group==G_POLLEN) "more/air" else "forecast?chart="+chartFor(r.metric)
        show(c,ChRules,3000+((r.id+BackgroundRefresh.locationKey(l)).hashCode() and 0xFFF),title,l.name,null,null,target=target,location=BackgroundRefresh.locationKey(l),detail=l.name+" · "+now)
    }
    private fun postEvent(c:Context,l:Location,r:NotifyRule,m:RuleMetric,h:Int) {
        val title=m.label()+" · "+uiText(R.string.nt_within_hours,h)
        show(c,ChRules,3000+((r.id+BackgroundRefresh.locationKey(l)).hashCode() and 0xFFF),title,l.name,null,null,target="forecast",location=BackgroundRefresh.locationKey(l),detail=l.name+" · "+uiText(R.string.nt_event_detail))
    }
    fun postSummary(c:Context,l:Location,d:OverviewData,kind:String) {
        val day=d.days.getOrNull(if(kind=="daily") 0 else 1) ?: return
        val u=units(c);val fg=ContextCompat.getColor(c,R.color.nt_system_text)
        val title=uiText(if(kind=="daily") R.string.nt_summary_today_title else R.string.nt_summary_tomorrow_title,l.name)
        val cond=overviewCondition(day.code);val range=temp(u,day.tmin)+" / "+temp(u,day.tmax)
        val icon=weatherBitmap(day.code,true,96)
        val small=smallView(c,icon,title,cond+" · "+range,null)
        val rainCell=(if(day.liquid>0.05) rain(u,day.liquid) else "0 "+u.precipitation.liquidSymbol)+" · "+percent(day.probability)
        val cells=listOf(glyphBitmap("rain",fg,48) to rainCell,glyphBitmap("gust",fg,48) to wind(u,day.gust),glyphBitmap("uv",fg,48) to weatherUnitNumber(day.uv,0))
        val foot=listOfNotNull(
            if(kind=="daily" && d.sunrise!=null && d.sunset!=null) uiText(R.string.nt_sun_line,d.sunrise.atZone(d.zone).format(Hm),d.sunset.atZone(d.zone).format(Hm)) else null,
            if(day.snow>0.1) uiText(R.string.nt_summary_snow,weatherUnitNumber(u.precipitation.fromSnowCentimetres(day.snow),1)+" "+u.precipitation.snowSymbol) else null,
        ).joinToString(" · ")
        val big=bigView(c,icon,title,cond,range,cells,emptyList(),foot)
        show(c,ChSummary,if(kind=="daily") 10 else 11,title,cond+" · "+range,small,big,target="forecast",location=BackgroundRefresh.locationKey(l))
    }

    /** One-time work per summary kind, delayed until the next chosen hour; the worker re-enqueues itself. */
    fun scheduleSummaries(c:Context) {
        val s=read(prefs(c));val wm=WorkManager.getInstance(c.applicationContext)
        schedule(wm,"daily",s.enabled && s.daily,s.dailyHour)
        schedule(wm,"tomorrow",s.enabled && s.tomorrow,s.tomorrowHour)
        if(!(s.enabled && s.current)) cancelCurrent(c)
        if(!(s.enabled && s.status)) cancelStatus(c)
    }
    private fun schedule(wm:WorkManager,kind:String,on:Boolean,hour:Int) {
        if(!on) { wm.cancelUniqueWork(SummaryWork+kind);return }
        val now=LocalDateTime.now()
        var at=now.toLocalDate().atTime(hour.coerceIn(0,23),0)
        if(!at.isAfter(now)) at=at.plusDays(1)
        val request=OneTimeWorkRequestBuilder<SummaryWorker>().setInitialDelay(Duration.between(now,at).toMillis(),TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("kind" to kind)).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        wm.enqueueUniqueWork(SummaryWork+kind,ExistingWorkPolicy.REPLACE,request)
    }
}

internal class SummaryWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        val c=applicationContext;val kind=inputData.getString("kind") ?: return Result.success()
        try {
            val s=Notifier.read(Notifier.prefs(c));val sel=if(kind=="daily") s.dailyLoc else s.tomorrowLoc
            val store=LocationStore(c);val all=store.load()
            val l=all.firstOrNull { sel!="active" && BackgroundRefresh.locationKey(it)==sel } ?: all.getOrNull(store.loadSelected()) ?: all.firstOrNull()
            if(l!=null) {
                val src=LocationSourceStore(c).read(l)
                Notifier.postSummary(c,l,loadOverview(l,src.alertSource,src.weatherModel),kind)
            }
        } catch(e:CancellationException) { throw e } catch(e:Exception) {
            if(runAttemptCount<2) return Result.retry()
        }
        Notifier.scheduleSummaries(c)
        return Result.success()
    }
}

@Composable internal fun rememberNotificationSettings():NotificationSettings {
    val context=LocalContext.current
    val p=remember(context) { Notifier.prefs(context) }
    var value by remember(p) { mutableStateOf(Notifier.read(p)) }
    DisposableEffect(p) {
        val listener=SharedPreferences.OnSharedPreferenceChangeListener { _,_ -> value=Notifier.read(p) }
        p.registerOnSharedPreferenceChangeListener(listener);value=Notifier.read(p)
        onDispose { p.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return value
}
internal fun notificationSummary(s:NotificationSettings):String {
    if(!s.enabled) return uiText(R.string.nt_summary_off)
    val parts=mutableListOf<String>()
    if(s.current) parts+=uiText(R.string.nt_current)
    if(s.status) parts+=uiText(R.string.nt_status)
    if(s.alerts) parts+=uiText(R.string.nt_alerts)
    if(s.rules.isNotEmpty()) parts+=uiText(R.string.nt_rules_count,s.rules.size)
    if(s.daily) parts+=uiText(R.string.nt_daily_short,s.dailyHour)
    if(s.tomorrow) parts+=uiText(R.string.nt_tomorrow_short,s.tomorrowHour)
    return if(parts.isEmpty()) uiText(R.string.nt_summary_nothing) else parts.joinToString(" · ")
}
@Composable private fun SwitchRow(text:String,checked:Boolean,info:String?=null,onChange:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) { Text(text,Modifier.weight(1f));if(info!=null) SettingsInfo(info);Switch(checked,onChange) }
}
@Composable private fun Label(text:String) = Text(text,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
@Composable private fun Note(text:String) = Text(text,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
private fun hourLabel(h:Int)=String.format(Locale.ROOT,"%d:00",h)
private fun locationLabel(sel:String,locations:List<Location>):String =
    sel.split(";").map { k -> if(k=="active") uiText(R.string.nt_loc_active) else locations.firstOrNull { BackgroundRefresh.locationKey(it)==k }?.name ?: uiText(R.string.nt_loc_active) }.distinct().joinToString(", ")

/** Compact one-line picker: label on the left, current value opens a dropdown. */
@Composable private fun Picker(label:String,labels:List<String>,selected:Int,textColor:Color=Color.Unspecified,headers:Map<Int,String> = emptyMap(),onPick:(Int)->Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().heightIn(min=44.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium,color=textColor)
        Box {
            TextButton(onClick={ open=true }) { Text(labels.getOrNull(selected) ?: labels.firstOrNull().orEmpty());Spacer(Modifier.width(2.dp));PickerChevron(open) }
            DropdownMenu(expanded=open,onDismissRequest={ open=false }) {
                labels.forEachIndexed { i,l ->
                    headers[i]?.let { h -> Text(h,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(horizontal=12.dp,vertical=6.dp)) }
                    DropdownMenuItem(text={ Text(l) },onClick={ open=false;onPick(i) })
                }
            }
        }
    }
}
@Composable private fun LocationPicker(label:String,locations:List<Location>,selected:String,textColor:Color=Color.Unspecified,onPick:(String)->Unit) {
    val keys=listOf("active")+locations.map { BackgroundRefresh.locationKey(it) }
    val labels=listOf(uiText(R.string.nt_loc_active))+locations.map { it.name }
    Picker(label,labels,keys.indexOf(selected).coerceAtLeast(0),textColor) { onPick(keys[it]) }
}

// Same dark blue-grey panel as the location (pencil) editor.
private val DialogPanel=Color(0xFF202833)
private val DialogText=Color(0xFFE8EAF0)
private val DialogMuted=Color(0xFFBBC4D2)
private val DialogAccent=Color(0xFF4FC3F7)
private val DialogEdge=Color(0xFF4B586C)

/** Rule editor in three short steps: group, metric (searchable chips), condition with location. */
@Composable private fun RuleDialog(initial:NotifyRule?,units:WeatherUnits,gas:AirGasUnit,locations:List<Location>,onDismiss:()->Unit,onSave:(NotifyRule)->Unit) {
    val first=initial?.let { Notifier.spec(it.metric) }
    var group by remember { mutableStateOf(first?.group ?: G_WEATHER) }
    var query by remember { mutableStateOf("") }
    var metric by remember { mutableStateOf(initial?.metric ?: Notifier.Specs.first { it.group==G_WEATHER }.key) }
    var above by remember { mutableStateOf(if(initial?.above==false) 1 else 0) }
    var threshold by remember { mutableStateOf(
        if(initial==null || first==null || first.kind==K_EVENT) "" else {
            val x=Notifier.display(first,initial.threshold,units,gas)
            weatherUnitNumber(x,if(x.isFinite() && abs(x-x.roundToInt())<0.005) 0 else 2)
        }
    ) }
    var window by remember { mutableStateOf(if(initial!=null && first?.kind==K_EVENT) initial.threshold.toInt() else 3) }
    var loc by remember { mutableStateOf(initial?.loc ?: "active") }
    val m=Notifier.spec(metric);val event=m.kind==K_EVENT
    val parsed=threshold.replace(',','.').toDoubleOrNull()
    val candidates=Notifier.Specs.filter { it.group==group && (query.isBlank() || it.label().contains(query.trim(),ignoreCase=true)) }
    val fieldColors=OutlinedTextFieldDefaults.colors(focusedTextColor=DialogText,unfocusedTextColor=DialogText,focusedBorderColor=DialogAccent,unfocusedBorderColor=DialogEdge,focusedLabelColor=DialogAccent,unfocusedLabelColor=DialogMuted,cursorColor=DialogAccent)
    AlertDialog(onDismissRequest=onDismiss,containerColor=DialogPanel,titleContentColor=DialogText,textContentColor=DialogText,
        title={ Text(uiText(if(initial==null) R.string.nt_rule_add else R.string.nt_rule_edit)) },
        text={
            Column(Modifier.heightIn(max=520.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                PerunChips(listOf(uiText(R.string.nt_group_weather),uiText(R.string.nt_group_air2),uiText(R.string.nt_group_pollen),uiText(R.string.nt_group_events)),group,{ g ->
                    group=g;query="";metric=Notifier.Specs.first { it.group==g }.key;threshold=""
                })
                if(Notifier.Specs.count { it.group==group }>8) OutlinedTextField(value=query,onValueChange={ query=it },singleLine=true,label={ Text(uiText(R.string.nt_rule_search)) },colors=fieldColors,modifier=Modifier.fillMaxWidth())
                if(candidates.isEmpty()) Text(uiText(R.string.nt_empty_search),color=DialogMuted,style=MaterialTheme.typography.bodySmall)
                else PerunChips(candidates.map { it.label() },candidates.indexOfFirst { it.key==metric },{ i -> metric=candidates[i].key;threshold="" })
                HorizontalDivider(color=DialogEdge)
                if(event) {
                    Text(uiText(R.string.nt_rule_window),style=MaterialTheme.typography.labelLarge,color=DialogMuted)
                    PerunChips(Notifier.EventWindows.map { uiText(R.string.nt_hours_n,it) },Notifier.EventWindows.indexOf(window).coerceAtLeast(0),{ window=Notifier.EventWindows[it] })
                    Text(uiText(R.string.nt_events_note),style=MaterialTheme.typography.bodySmall,color=DialogMuted)
                } else Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    PerunChips(listOf("≥","≤"),above,{ above=it })
                    OutlinedTextField(value=threshold,onValueChange={ threshold=it },singleLine=true,modifier=Modifier.weight(1f),
                        label={ Text(Notifier.unitOf(m,units,gas).ifEmpty { uiText(R.string.nt_rule_value) }) },keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),colors=fieldColors)
                }
                Text(uiText(R.string.nt_location),style=MaterialTheme.typography.labelLarge,color=DialogMuted)
                val chosen=loc.split(";").filter { it.isNotBlank() }
                (listOf("active" to uiText(R.string.nt_loc_active))+locations.map { BackgroundRefresh.locationKey(it) to it.name }).forEach { (k,name) ->
                    Row(Modifier.fillMaxWidth().heightIn(min=40.dp).clickable { val next=if(k in chosen) chosen-k else chosen+k;if(next.isNotEmpty()) loc=next.joinToString(";") },verticalAlignment=Alignment.CenterVertically) {
                        Checkbox(checked=k in chosen,onCheckedChange=null,colors=CheckboxDefaults.colors(checkedColor=DialogAccent,uncheckedColor=DialogMuted,checkmarkColor=DialogPanel))
                        Text(name,Modifier.padding(start=8.dp),color=DialogText)
                    }
                }
            }
        },
        confirmButton={ TextButton(enabled=event || parsed!=null,onClick={
            val value=if(event) window.toDouble() else Notifier.toCanonical(m,parsed ?: 0.0,units,gas)
            onSave(NotifyRule(initial?.id ?: java.lang.Long.toString(System.currentTimeMillis(),36),metric,event || above==0,value,loc))
        }) { Text(uiText(R.string.nt_rule_save),color=if(event || parsed!=null) DialogAccent else DialogMuted) } },
        dismissButton={ TextButton(onClick=onDismiss) { Text(uiText(R.string.ui_zrusit_520e257a),color=DialogMuted) } })
}

@Composable internal fun NotificationSettingsPane() {
    val context=LocalContext.current
    val p=remember(context) { Notifier.prefs(context) }
    val s=rememberNotificationSettings()
    val bg=rememberBackgroundRefresh()
    val units=rememberForecastPreferences().units
    val gas=rememberAirPreferences().gas
    val locations=remember(context) { LocationStore(context).load() }
    val permittedNow=rememberOnResume { Notifier.permitted(context) }
    var granted by remember { mutableStateOf(permittedNow) }
    LaunchedEffect(permittedNow) { granted=permittedNow }
    // refresh=true also runs one refresh right away, so a newly enabled notification appears without waiting for the interval.
    fun edit(refresh:Boolean=false,block:SharedPreferences.Editor.()->Unit) {
        p.edit().apply(block).apply();Notifier.scheduleSummaries(context);BackgroundRefresh.schedule(context)
        if(refresh && Notifier.needsBackground(context)) BackgroundRefresh.refreshNow(context)
    }
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted=Notifier.permitted(context)
        if(granted) edit(true) { putBoolean("enabled",true) }
    }
    SwitchRow(uiText(R.string.nt_enable),s.enabled) { on ->
        if(on && !Notifier.permitted(context)) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else edit(on) { putBoolean("enabled",on) }
    }
    PerunStatusRow(granted,if(granted) uiText(R.string.nt_permission_granted) else uiText(R.string.nt_permission_missing))
    val needs=s.current || s.alerts || s.rules.isNotEmpty() || s.status
    PerunStatusRow(bg.intervalMinutes>0 || needs,when {
        bg.intervalMinutes>0->uiText(R.string.nt_bg_on,backgroundIntervalLabel(bg.intervalMinutes))
        needs->uiText(R.string.nt_bg_fallback)
        else->uiText(R.string.nt_bg_off)
    })
    if(!s.enabled) return
    val divider=MaterialTheme.colorScheme.outline.copy(alpha=.5f)
    HorizontalDivider(color=divider)
    SwitchRow(uiText(R.string.nt_current),s.current,uiText(R.string.nt_current_note)) { edit(it) { putBoolean("current",it) } }
    if(s.current) for(i in 0..2) {
        Picker(uiText(R.string.nt_param_n,i+1),Notifier.CurrentOptions.map { Notifier.paramLabel(it) },Notifier.CurrentOptions.indexOf(s.currentParams[i]).coerceAtLeast(0)) { pick ->
            val next=s.currentParams.toMutableList();val chosen=Notifier.CurrentOptions[pick]
            val other=next.indexOf(chosen);if(other>=0 && other!=i) next[other]=next[i]
            next[i]=chosen;edit(true) { putString("curParams",next.joinToString(",")) }
        }
    }
    HorizontalDivider(color=divider)
    SwitchRow(uiText(R.string.nt_status),s.status,uiText(R.string.nt_status_note)) { edit(it) { putBoolean("status",it) } }
    if(s.status) {
        Picker(uiText(R.string.nt_status_param),Notifier.StatusOptions.map { Notifier.paramLabel(it) },Notifier.StatusOptions.indexOf(s.statusParam).coerceAtLeast(0),headers=Notifier.statusHeaders()) { edit(true) { putString("statusParam",Notifier.StatusOptions[it]) } }
        LocationPicker(uiText(R.string.nt_location),locations,s.statusLoc) { edit(true) { putString("statusLoc",it) } }
    }
    HorizontalDivider(color=divider)
    SwitchRow(uiText(R.string.nt_alerts),s.alerts) { edit(it) { putBoolean("alerts",it) } }
    if(s.alerts) {
        Label(uiText(R.string.nt_alert_level))
        PerunChips(listOf(uiText(R.string.nt_level_yellow),uiText(R.string.nt_level_orange),uiText(R.string.nt_level_red)),Notifier.Levels.indexOf(s.alertLevel),{ edit { putString("alertLevel",Notifier.Levels[it]) } })
        Label(uiText(R.string.bg_locations))
        val scopes=listOf("active","all","custom")
        PerunChips(listOf(uiText(R.string.bg_scope_active),uiText(R.string.bg_scope_all),uiText(R.string.bg_scope_custom)),scopes.indexOf(s.alertScope).coerceAtLeast(0),{ edit(true) { putString("alertScope",scopes[it]) } })
        if(s.alertScope=="custom") Column {
            locations.forEach { l ->
                val key=BackgroundRefresh.locationKey(l)
                Row(Modifier.fillMaxWidth().heightIn(min=44.dp),verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(checked=key in s.alertLocations,onCheckedChange={ on -> val next=s.alertLocations.toMutableSet();if(on) next.add(key) else next.remove(key);edit(on) { putStringSet("alertLocations",next) } })
                    Text(l.name,Modifier.padding(start=8.dp))
                }
            }
        }
    }
    HorizontalDivider(color=divider)
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Box(Modifier.weight(1f)) { Label(uiText(R.string.nt_rules)) };SettingsInfo(uiText(R.string.nt_rules_note)) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<NotifyRule?>(null) }
    s.rules.forEach { r ->
        Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clickable { editing=r },verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Notifier.ruleLabel(r,units,gas),style=MaterialTheme.typography.bodyMedium)
                Text(locationLabel(r.loc,locations),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick={ Notifier.writeRules(p,s.rules.filter { it.id!=r.id });edit { } }) { Text(uiText(R.string.nt_rule_remove)) }
        }
    }
    if(s.rules.isEmpty()) Note(uiText(R.string.nt_rules_empty))
    OutlinedButton(onClick={ adding=true },modifier=Modifier.fillMaxWidth()) { Text(uiText(R.string.nt_rule_add)) }
    HorizontalDivider(color=divider)
    SwitchRow(uiText(R.string.nt_daily),s.daily,uiText(R.string.nt_summary_note)) { edit { putBoolean("daily",it) } }
    if(s.daily) {
        PerunChips(Notifier.DailyHours.map { hourLabel(it) },Notifier.DailyHours.indexOf(s.dailyHour).coerceAtLeast(0),{ edit { putInt("dailyHour",Notifier.DailyHours[it]) } })
        LocationPicker(uiText(R.string.nt_location),locations,s.dailyLoc) { edit { putString("dailyLoc",it) } }
    }
    SwitchRow(uiText(R.string.nt_tomorrow),s.tomorrow,uiText(R.string.nt_summary_note)) { edit { putBoolean("tomorrow",it) } }
    if(s.tomorrow) {
        PerunChips(Notifier.TomorrowHours.map { hourLabel(it) },Notifier.TomorrowHours.indexOf(s.tomorrowHour).coerceAtLeast(0),{ edit { putInt("tomorrowHour",Notifier.TomorrowHours[it]) } })
        LocationPicker(uiText(R.string.nt_location),locations,s.tomorrowLoc) { edit { putString("tomorrowLoc",it) } }
    }
    val dialogRule=editing
    if(adding || dialogRule!=null) RuleDialog(dialogRule,units,gas,locations,onDismiss={ adding=false;editing=null },onSave={ rule ->
        val list=if(s.rules.any { it.id==rule.id }) s.rules.map { if(it.id==rule.id) rule else it } else s.rules+rule
        val ed=p.edit();p.all.keys.filter { it.startsWith("rule_"+rule.id) }.forEach { ed.remove(it) };ed.apply()
        Notifier.writeRules(p,list);adding=false;editing=null;edit(true) { }
    })
}

/** Shared with the home-screen widgets. */
internal fun widgetWeatherBitmap(code:Int,day:Boolean,size:Int):Bitmap = weatherBitmap(code,day,size)
internal fun widgetGlyphBitmap(param:String,color:Int,size:Int):Bitmap = glyphBitmap(param,color,size)
internal fun widgetAlertBitmap(event:String,level:String,size:Int,awareness:String=""):Bitmap = alertIcon(event,alertColor(level),size,awareness)

/** Same chevron as the day rows of the Forecast screen: points down when closed, up when open. */
@Composable private fun PickerChevron(open:Boolean) {
    val color=Color(0xFF4FC3F7)
    androidx.compose.foundation.Canvas(Modifier.size(20.dp)) {
        val path=androidx.compose.ui.graphics.Path().apply { moveTo(size.width*.25f,size.height*(if(open) .6f else .4f));lineTo(size.width*.5f,size.height*(if(open) .4f else .6f));lineTo(size.width*.75f,size.height*(if(open) .6f else .4f)) }
        drawPath(path,color,style=androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx(),cap=androidx.compose.ui.graphics.StrokeCap.Round,join=androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}
