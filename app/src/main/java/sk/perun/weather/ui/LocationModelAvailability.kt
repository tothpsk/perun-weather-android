package sk.perun.weather.ui

import sk.perun.weather.R

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URLEncoder

internal enum class ModelStatus { Unknown, Checking, Available, NoData, Failed }
private enum class ModelProbe { Available,NoData,Unknown }
private suspend fun probeLocationModel(l:Location,id:String,ensemble:Boolean):ModelProbe=withContext(Dispatchers.IO) {
    try {
        val base=if(ensemble) "https://ensemble-api.open-meteo.com/v1/ensemble" else "https://api.open-meteo.com/v1/forecast"
        val q="latitude=${l.latitude}&longitude=${l.longitude}&timezone=UTC&timeformat=unixtime&forecast_hours=24&hourly=temperature_2m&models="+URLEncoder.encode(id,"UTF-8")
        val root=JSONObject(String(fetchBytes("$base?$q"),Charsets.UTF_8))
        val lat=root.optDouble("latitude",Double.NaN);val rawLon=root.optDouble("longitude",Double.NaN)
        if(!lat.isFinite() || !rawLon.isFinite() || lat !in -90.0..90.0) return@withContext ModelProbe.Unknown
        val lon=((rawLon+180)%360+360)%360-180
        if(marineDistance(l.latitude,l.longitude,lat,lon)>150) return@withContext ModelProbe.NoData
        val h=root.optJSONObject("hourly") ?: return@withContext ModelProbe.Unknown
        val times=h.optJSONArray("time") ?: return@withContext ModelProbe.Unknown
        val pattern=Regex("temperature_2m(?:_member[0-9]+)?")
        val columns=h.keys().asSequence().filter {pattern.matches(it)}.mapNotNull {h.optJSONArray(it)}.toList()
        val now=System.currentTimeMillis()/1000
        val needed=if(ensemble) 2 else 1
        val valid=(0 until times.length()).any {i->val t=times.optLong(i,Long.MIN_VALUE)
            t>=now-3600 && t<=now+86400 && columns.count {a->!a.isNull(i) && a.optDouble(i,Double.NaN).isFinite()}>=needed
        }
        if(valid) ModelProbe.Available else ModelProbe.NoData
    } catch(e:CancellationException) {throw e} catch(e:Exception) {
        if(e.message=="HTTP 400" || e.message=="HTTP 404") ModelProbe.NoData else ModelProbe.Unknown
    }
}
// Verifies models only on demand: the saved pair when the editor opens, then whichever model the user taps.
internal class ModelAvailabilityController(private val location:Location,private val prefs:SharedPreferences,private val scope:CoroutineScope) {
    private val cacheKey=locationSourceKey(location)+".v2"
    val status=mutableStateMapOf<String,ModelStatus>()
    private val jobs=HashMap<String,Job>()
    init {
        runCatching {
            val o=JSONObject(prefs.getString(cacheKey,null) ?: return@runCatching)
            val now=System.currentTimeMillis()
            for(id in o.keys()) {
                val e=o.optJSONObject(id) ?: continue
                if(now-e.optLong("time") in 0L..21_600_000L && e.optString("status")=="available") status[id]=ModelStatus.Available
            }
        }
    }
    fun statusOf(id:String):ModelStatus=if(id=="best_match" || id==LocationModelCatalogue.AutomaticEnsemble) ModelStatus.Available else status[id] ?: ModelStatus.Unknown
    fun check(id:String,ensemble:Boolean,force:Boolean=false) {
        if(statusOf(id)==ModelStatus.Available && !force) return
        if(jobs[id]?.isActive==true) return
        status[id]=ModelStatus.Checking
        jobs[id]=scope.launch {
            val result=probeLocationModel(location,id,ensemble)
            status[id]=when(result) {ModelProbe.Available->ModelStatus.Available;ModelProbe.NoData->ModelStatus.NoData;ModelProbe.Unknown->ModelStatus.Failed}
            if(result==ModelProbe.Available) {
                val o=runCatching {JSONObject(prefs.getString(cacheKey,null) ?: "{}")}.getOrDefault(JSONObject())
                o.put(id,JSONObject().put("status","available").put("time",System.currentTimeMillis()))
                prefs.edit().putString(cacheKey,o.toString()).apply()
            }
        }
    }
}
@Composable internal fun LocationSourceEditorHost(location:Location,onDismiss:()->Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val key=locationSourceKey(location)
    val prefs=remember(context) {context.getSharedPreferences("perun_model_availability",Context.MODE_PRIVATE)}
    val saved=rememberLocationSourcePreferences(location)
    val controller=remember(key) {ModelAvailabilityController(location,prefs,scope)}
    LaunchedEffect(key) {
        controller.check(saved.weatherModel,false)
        controller.check(saved.ensembleModel,true)
    }
    LocationSourceEditor(location=location,controller=controller,onDismiss=onDismiss)
}
@Composable internal fun LocationSourcePencil() {
    val color=LocalContentColor.current
    val path=remember {Path().apply {moveTo(4f,16f);lineTo(16f,4f);lineTo(20f,8f);lineTo(8f,20f);lineTo(4f,20f);close();moveTo(14f,6f);lineTo(18f,10f)}}
    Canvas(Modifier.size(24.dp)) {val k=size.width/24f;scale(k,k,Offset.Zero) {drawPath(path,color,style=Stroke(1.8f,cap=StrokeCap.Round,join=StrokeJoin.Round))}}
}
