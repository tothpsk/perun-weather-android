package sk.perun.weather.ui

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import android.content.SharedPreferences
import org.json.JSONObject
import java.security.MessageDigest

internal data class LocationSourcePreferences(
    val weatherModel:String="best_match",
    val ensembleModel:String=LocationModelCatalogue.AutomaticEnsemble,
    val alertSource:String="kde",
    val nowcastSource:String="libre",
)
internal val AlertSourceIds=listOf("local","meteo","libre","kde")
// "v2:" plus comma-separated ids. Older values ("kde" or "libre") meant local estimates, MeteoAlarm and that one source.
internal fun alertSourceSet(value:String):Set<String> = if(value.startsWith("v2:")) value.removePrefix("v2:").split(",").filter {it in AlertSourceIds}.toSet() else setOf("local","meteo",if(value=="libre") "libre" else "kde")
internal fun alertSourceValue(ids:Set<String>):String="v2:"+AlertSourceIds.filter {it in ids}.joinToString(",")
internal fun normalizeAlertSource(raw:String?):String=alertSourceValue(alertSourceSet(raw ?: "kde"))
internal fun locationSourceKey(location:Location):String {
    require(location.latitude.isFinite() && location.longitude.isFinite())
    require(location.latitude in -90.0..90.0 && location.longitude in -180.0..180.0)
    val lat=if(location.latitude==0.0) 0.0 else location.latitude
    val lon=if(location.longitude==0.0) 0.0 else location.longitude
    val bytes=MessageDigest.getInstance("SHA-256").digest("$lat,$lon".toByteArray(Charsets.UTF_8))
    return "location.v1."+bytes.joinToString("") {(it.toInt() and 255).toString(16).padStart(2,'0')}
}
internal class LocationSourceStore(context:Context) {
    internal val preferences:SharedPreferences=context.applicationContext.getSharedPreferences("perun_location_preferences",Context.MODE_PRIVATE)
    fun read(location:Location):LocationSourcePreferences {
        val raw=preferences.getString(locationSourceKey(location),null) ?: return LocationSourcePreferences()
        return runCatching {
            val o=JSONObject(raw)
            LocationSourcePreferences(
                o.optString("weatherModel","best_match").takeIf {it in LocationModelCatalogue.weatherIds} ?: "best_match",
                o.optString("ensembleModel",LocationModelCatalogue.AutomaticEnsemble).takeIf {it in LocationModelCatalogue.ensembleIds} ?: LocationModelCatalogue.AutomaticEnsemble,
                normalizeAlertSource(o.optString("alertSource","kde")),
                o.optString("nowcastSource","libre").takeIf {it in setOf("libre","openmeteo")} ?: "libre",
            )
        }.getOrDefault(LocationSourcePreferences())
    }
    fun save(location:Location,value:LocationSourcePreferences) {
        require(value.weatherModel in LocationModelCatalogue.weatherIds)
        require(value.ensembleModel in LocationModelCatalogue.ensembleIds)
        require(value.alertSource.length<=64)
        require(value.nowcastSource in setOf("libre","openmeteo"))
        val raw=JSONObject().put("version",1).put("weatherModel",value.weatherModel).put("ensembleModel",value.ensembleModel).put("alertSource",normalizeAlertSource(value.alertSource)).put("nowcastSource",value.nowcastSource).toString()
        preferences.edit().putString(locationSourceKey(location),raw).apply()
    }
}
internal val LocalLocationSourcePreferences=staticCompositionLocalOf<LocationSourcePreferences?> {null}
@Composable internal fun rememberLocationSourcePreferences(location:Location):LocationSourcePreferences {
    val context=LocalContext.current
    val store=remember(context) {LocationSourceStore(context)}
    val key=locationSourceKey(location)
    var value by remember(store,key) {mutableStateOf(store.read(location))}
    DisposableEffect(store,key) {
        val listener=SharedPreferences.OnSharedPreferenceChangeListener {_,changed->if(changed==key || changed==null) value=store.read(location)}
        store.preferences.registerOnSharedPreferenceChangeListener(listener)
        value=store.read(location)
        onDispose {store.preferences.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    return value
}
