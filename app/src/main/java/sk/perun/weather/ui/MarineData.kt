package sk.perun.weather.ui

import sk.perun.weather.R

import java.net.URLEncoder
import java.time.ZoneId
import kotlin.math.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal val MarineKeys=listOf("sea_surface_temperature","wave_height","wave_period","wave_direction","sea_level_height_msl")
internal fun marineDistance(a:Double,b:Double,c:Double,d:Double):Double {
    require(listOf(a,b,c,d).all { it.isFinite() } && a in -90.0..90.0 && c in -90.0..90.0 && b in -180.0..180.0 && d in -180.0..180.0)
    val x=sin((c-a)*PI/360).pow(2)+cos(a*PI/180)*cos(c*PI/180)*sin((d-b)*PI/360).pow(2)
    return 12742*asin(sqrt(x.coerceIn(0.0,1.0)))
}
internal fun marineValue(key:String,v:Double):Double=when {
    !v.isFinite() -> Double.NaN
    key in listOf("wave_height","wave_period") && v<0 -> Double.NaN
    key=="wave_direction" && v !in 0.0..360.0 -> Double.NaN
    else -> v
}
internal fun marineTravelBearing(from:Double)=if(from.isFinite() && from in 0.0..360.0) (from+180)%360 else Double.NaN
internal data class MarineExtreme(val index:Int,val maximum:Boolean)
internal fun marineExtremes(v:List<Double>):List<MarineExtreme> {
    val out=mutableListOf<MarineExtreme>();var i=1
    while(i<v.size-1) {
        val current=v[i]
        if(!current.isFinite()) { i++;continue }
        var end=i
        while(end+1<v.size && v[end+1].isFinite() && abs(v[end+1]-current)<=.01) end++
        val before=v[i-1];val after=v.getOrNull(end+1) ?: Double.NaN
        if(before.isFinite() && after.isFinite()) {
            val high=current-before>.01 && current-after>.01
            val low=before-current>.01 && after-current>.01
            if(high || low) out+=MarineExtreme((i+end)/2,high)
        }
        i=end+1
    }
    return out
}
internal data class MarineData(val times:List<Long>,val zone:ZoneId,val latitude:Double,val longitude:Double,val distance:Double,val values:Map<String,List<Double>>) {
    fun at(key:String,i:Int)=values[key]?.getOrNull(i) ?: Double.NaN
    val relevant:Boolean get()=distance<=30 && listOf("wave_height","sea_surface_temperature").any { values[it].orEmpty().any { v->v.isFinite() } }
}
internal suspend fun loadMarine(l:Location,days:Int):MarineData=withContext(Dispatchers.IO) {
    require(days in 1..8)
    require(l.latitude.isFinite() && l.longitude.isFinite() && l.latitude in -90.0..90.0 && l.longitude in -180.0..180.0)
    val tz=URLEncoder.encode(l.timezone.ifBlank { "auto" },"UTF-8")
    val url="https://marine-api.open-meteo.com/v1/marine?latitude=${l.latitude}&longitude=${l.longitude}&timezone=$tz&timeformat=unixtime&cell_selection=sea&length_unit=metric&forecast_days=$days&hourly="+MarineKeys.joinToString(",")
    val root=JSONObject(String(fetchBytes(url),Charsets.UTF_8));val lat=root.getDouble("latitude");val lon=root.getDouble("longitude")
    val h=root.getJSONObject("hourly");val a=h.getJSONArray("time");require(a.length()>1) { uiText(R.string.ui_chybaju_hodinove_morske_udaje_0054120c) }
    val times=List(a.length()) { a.getLong(it) };require(times.zipWithNext().all { (x,y)->y>x }) { uiText(R.string.ui_neplatne_casove_poradie_efcd08b4) }
    val values=MarineKeys.associateWith { key -> val v=h.optJSONArray(key);List(times.size) { i->marineValue(key,if(v==null || v.isNull(i)) Double.NaN else v.optDouble(i,Double.NaN)) } }
    MarineData(times,ZoneId.of(root.getString("timezone")),lat,lon,marineDistance(l.latitude,l.longitude,lat,lon),values)
}
