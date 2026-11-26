package sk.perun.weather.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Instant

/** Hourly forecast of the metrics of the notification rules: it tells how long a fulfilled rule will hold. */
internal class RuleSeries(private val data:Map<String,Pair<LongArray,DoubleArray>>) {
    /** The start of the first coming hour that is not [fulfilled]; null when the metric is missing or the rule holds to the end. */
    fun end(metric:String,now:Instant,fulfilled:(Double)->Boolean):Instant? {
        val pair=data[metric] ?: return null
        val times=pair.first;val values=pair.second
        for(i in times.indices) { if(times[i]<=now.epochSecond) continue;val x=values[i];if(x.isFinite() && !fulfilled(x)) return Instant.ofEpochSecond(times[i]) }
        return null
    }
}

// Open-Meteo hourly variables of the weather metrics, in the canonical units of the rules; the other metrics are air quality variables.
private val RuleWeatherHourly=mapOf("temp" to "temperature_2m","feels" to "apparent_temperature","precip" to "precipitation","snow" to "snowfall","prob" to "precipitation_probability",
    "wind" to "wind_speed_10m","gust" to "wind_gusts_10m","cloud" to "cloud_cover","uv" to "uv_index","humidity" to "relative_humidity_2m","dew" to "dew_point_2m","visibility" to "visibility","pressure" to "pressure_msl")

private fun ruleHourly(url:String,names:Map<String,String>):Map<String,Pair<LongArray,DoubleArray>> = try {
    val h=JSONObject(String(fetchBytes(url),Charsets.UTF_8)).getJSONObject("hourly");val t=h.getJSONArray("time")
    val times=LongArray(t.length()) { t.optLong(it) }
    val out=HashMap<String,Pair<LongArray,DoubleArray>>()
    names.forEach { (metric,name) -> val a=h.optJSONArray(name);if(a!=null) out[metric]=Pair(times,DoubleArray(times.size) { a.optDouble(it,Double.NaN) }) }
    out
} catch(e:CancellationException) { throw e } catch(e:Exception) { emptyMap() }

/** [metrics] are keys of rule metrics without the events; [aqi] is the variable of the chosen air quality index. */
internal suspend fun loadRuleSeries(l:Location,model:String,metrics:Set<String>,aqi:String):RuleSeries = withContext(Dispatchers.IO) {
    val where="latitude=${l.latitude}&longitude=${l.longitude}&timeformat=unixtime&forecast_days=3"
    val weather=metrics.filter { it in RuleWeatherHourly }.associateWith { RuleWeatherHourly.getValue(it) }
    val air=metrics.filter { it !in RuleWeatherHourly }.associateWith { if(it=="aqi") aqi else it }
    val elevation=l.elevation?.let { "&elevation=$it" } ?: ""
    val modelQuery=if(model=="best_match" || model.isBlank()) "" else "&models="+URLEncoder.encode(model,"UTF-8")
    val out=HashMap<String,Pair<LongArray,DoubleArray>>()
    if(weather.isNotEmpty()) out.putAll(ruleHourly("https://api.open-meteo.com/v1/forecast?$where$elevation$modelQuery&hourly="+weather.values.distinct().joinToString(","),weather))
    if(air.isNotEmpty()) out.putAll(ruleHourly("https://air-quality-api.open-meteo.com/v1/air-quality?$where&hourly="+air.values.distinct().joinToString(","),air))
    RuleSeries(out)
}
