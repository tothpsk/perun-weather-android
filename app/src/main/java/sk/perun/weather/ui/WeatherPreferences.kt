package sk.perun.weather.ui

import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

internal data class ForecastPreferences(val daily:Boolean,val days:Int,val step:Int,val smooth:Int,val model:String,val units:WeatherUnits,val alertSource:String=AlertSourceDefaults.value,val ensembleModel:String=LocationModelCatalogue.AutomaticEnsemble)
internal fun readForecastPreferences(p:SharedPreferences):ForecastPreferences {
    fun <T:Enum<T>> choice(key:String,values:List<T>,default:T):T=values.firstOrNull {it.name==p.getString(key,default.name)} ?: default
    val storedModel=p.getString("model","best_match") ?: "best_match"
    val model=when(storedModel) {
        "gfs_seamless"->"ncep_gfs_seamless"
        "icon_seamless"->"dwd_icon_seamless"
        else->storedModel
    }.takeIf {it in LocationModelCatalogue.weatherIds} ?: "best_match"
    return ForecastPreferences(p.getBoolean("daily",false),p.getInt("days",5).takeIf {it in listOf(3,5,7,16)} ?: 5,
        p.getInt("step",1).takeIf {it in listOf(1,3,6)} ?: 1,p.getInt("smooth",1).coerceIn(0,3),model,
        WeatherUnits(choice("temperatureUnit",TemperatureUnit.entries,TemperatureUnit.Celsius),choice("windUnit",WindSpeedUnit.entries,WindSpeedUnit.KilometresPerHour),choice("pressureUnit",PressureUnit.entries,PressureUnit.Hectopascals),choice("precipitationUnit",PrecipitationUnit.entries,PrecipitationUnit.Metric)),
        alertSource=normalizeAlertSource(p.getString("alertSource",AlertSourceDefaults.value)))
}
@Composable internal fun rememberForecastPreferences():ForecastPreferences {
    val context=LocalContext.current
    val prefs=remember(context) {context.getSharedPreferences("perun_forecast",android.content.Context.MODE_PRIVATE)}
    var value by remember(prefs) {mutableStateOf(readForecastPreferences(prefs))}
    DisposableEffect(prefs) {
        val listener=SharedPreferences.OnSharedPreferenceChangeListener {_,_->value=readForecastPreferences(prefs)}
        prefs.registerOnSharedPreferenceChangeListener(listener);value=readForecastPreferences(prefs)
        onDispose {prefs.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    val local=LocalLocationSourcePreferences.current
    return if(local==null) value else value.copy(model=local.weatherModel,alertSource=local.alertSource,ensembleModel=local.ensembleModel)
}
