package sk.perun.weather.ui

import sk.perun.weather.R

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

internal data class OutlookDay(
    val date: LocalDate, val code: Int, val tmin: Double, val tmax: Double,
    val liquid: Double, val snow: Double, val rain: Double, val showers: Double, val probability: Double,
    val wind: Double, val gust: Double, val windDirection: Double,
    val cloudMean: Double, val cloudMax: Double, val uv: Double, val aqi: Double, val daylightCloud: Double = Double.NaN,
)

internal data class OverviewAir(val aqi: Double, val pm25: Double, val pm10: Double, val pollen: Map<String, Double>)

internal data class OverviewHour(val time: LocalDateTime, val temperature: Double, val code: Int, val probability: Double, val precipitation: Double, val cloud: Double = Double.NaN, val wind: Double = Double.NaN, val gust: Double = Double.NaN, val direction: Double = Double.NaN)

internal data class OverviewData(
    val time: LocalDateTime, val zone: ZoneId, val latitude: Double, val longitude: Double,
    val temperature: Double, val apparent: Double, val code: Int, val isDayFlag: Boolean,
    val wind: Double, val gust: Double, val direction: Double, val cloud: Double, val uv: Double,
    val precipitation: Double, val rain: Double, val showers: Double, val snowfall: Double,
    val probability: Double, val intervalSeconds: Int,
    val sunrise: Instant?, val sunset: Instant?,
    val days: List<OutlookDay>, val air: OverviewAir?, val alerts: List<UnifiedAlert>,
    val fallback: List<String> = emptyList(),
    val hours: List<OverviewHour> = emptyList(),
    val series: List<OverviewHour> = emptyList(),
    val sunTimes: List<Pair<Instant, Instant>> = emptyList(),
)

private val POLLEN_KEYS = listOf("alder", "birch", "grass", "mugwort", "ragweed", "olive")
private val SNOW_CODES = setOf(71, 73, 75, 77, 85, 86)
private val CURRENT_KEYS = listOf("temperature_2m", "apparent_temperature", "is_day", "weather_code", "cloud_cover", "wind_speed_10m", "wind_direction_10m", "wind_gusts_10m", "precipitation", "rain", "showers", "snowfall", "uv_index", "precipitation_probability")
private val HOURLY_KEYS = listOf("is_day", "temperature_2m", "weather_code", "precipitation", "rain", "showers", "snowfall", "precipitation_probability", "cloud_cover", "wind_speed_10m", "wind_direction_10m", "wind_gusts_10m", "uv_index")

private class AirBundle(val now: OverviewAir, val hourlyAqi: Map<String, Double>)

// Every network request in the app goes through here, so the shared cache (NetCache) applies to all screens.
internal fun fetchBytes(url: String): ByteArray {
    val cacheable = NetCache.cacheable(url)
    if (cacheable && !NetCache.forceNetwork) NetCache.read(url, NetCache.freshSeconds, honourInvalidation = true)?.let { return it }
    try {
        val data = fetchNetwork(url)
        if (cacheable) NetCache.write(url, data)
        return data
    } catch (e: Exception) {
        // Offline or API failure: last known response, if it is not too old.
        if (cacheable && !NetCache.alertUrl(url)) NetCache.read(url, NetCache.staleSeconds)?.let { return it }
        throw e
    }
}

private fun fetchNetwork(url: String): ByteArray {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.setRequestProperty("User-Agent", "PerunWeatherAndroid/0.1 (+https://github.com/tothpsk/perun-weather-android)")
        check(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
        return connection.inputStream.use { it.readBytes() }
    } finally {
        connection.disconnect()
    }
}

private fun fetchJson(url: String): JSONObject = JSONObject(String(fetchBytes(url), Charsets.UTF_8))

private fun JSONObject.series(key: String, n: Int): DoubleArray {
    val a = optJSONArray(key)
    return DoubleArray(n) { i -> a?.optDouble(i, Double.NaN) ?: Double.NaN }
}

private fun priority(code: Int): Int = when {
    code >= 95 -> 6
    code in SNOW_CODES -> 5
    code >= 51 -> 4
    code == 45 || code == 48 -> 3
    code == 3 -> 2
    code >= 1 -> 1
    else -> 0
}

// Local estimates from the forecast. One rule set for the Now screen, notifications, widgets and the Alerts screen:
// the first matching hour whose validity window (hours after that hour) has not passed yet.
internal class LocalAlertHit(val event: String, val level: String, val start: Instant, val end: Instant)
private class LocalAlertRule(val event: String, val level: String, val hours: Long, val series: Int, val test: (Double) -> Boolean)
private val LOCAL_ALERT_RULES = listOf(
    LocalAlertRule("Thunderstorm risk", "red", 6, 0) { it.toInt() in setOf(95, 96, 99) },
    LocalAlertRule("Strong wind gusts", "orange", 12, 1) { it >= 60.0 },
    LocalAlertRule("Heavy precipitation", "orange", 6, 2) { it >= 10.0 },
    LocalAlertRule("Extreme heat", "yellow", 24, 3) { it >= 35.0 },
    LocalAlertRule("Extreme cold", "yellow", 24, 3) { it <= -15.0 },
)
internal fun localAlertHits(times: List<Instant>, code: DoubleArray, gust: DoubleArray, precip: DoubleArray, temp: DoubleArray, now: Instant): List<LocalAlertHit> {
    val series = listOf(code, gust, precip, temp)
    return LOCAL_ALERT_RULES.mapNotNull { r ->
        val values = series[r.series]
        val window = Duration.ofHours(r.hours)
        val i = times.indices.firstOrNull { it < values.size && values[it].isFinite() && r.test(values[it]) && times[it].plus(window).isAfter(now) } ?: return@mapNotNull null
        LocalAlertHit(r.event, r.level, times[i], times[i].plus(window))
    }
}

internal suspend fun loadOverview(location: Location, alertSource: String = AlertSourceDefaults.value, model: String = "best_match"): OverviewData = withContext(Dispatchers.IO) {
    require(model in LocationModelCatalogue.weatherIds) { uiText(R.string.ui_neznamy_predpovedny_model_value_28933271, model) }
    coroutineScope {
        val where = "latitude=${location.latitude}&longitude=${location.longitude}&timezone=" +
            URLEncoder.encode(location.timezone.ifBlank { "auto" }, "UTF-8")
        val elevation = location.elevation?.let { "&elevation=$it" } ?: ""
        val modelQuery = if (model == "best_match") "" else "&models=" + URLEncoder.encode(model, "UTF-8")
        val airTask = async {
            try {
                val root = fetchJson("https://air-quality-api.open-meteo.com/v1/air-quality?$where&forecast_days=5&current=european_aqi,pm2_5,pm10,alder_pollen,birch_pollen,grass_pollen,mugwort_pollen,olive_pollen,ragweed_pollen&hourly=european_aqi")
                val c = root.getJSONObject("current")
                val pollen = HashMap<String, Double>()
                for (key in POLLEN_KEYS) {
                    val v = c.optDouble(key + "_pollen")
                    if (v.isFinite()) pollen[key] = v
                }
                val hourly = HashMap<String, Double>()
                val h = root.optJSONObject("hourly")
                val t = h?.optJSONArray("time")
                val a = h?.optJSONArray("european_aqi")
                if (t != null && a != null) {
                    for (i in 0 until t.length()) {
                        val v = a.optDouble(i, Double.NaN)
                        if (v.isFinite()) hourly[t.getString(i)] = v
                    }
                }
                AirBundle(OverviewAir(c.optDouble("european_aqi"), c.optDouble("pm2_5"), c.optDouble("pm10"), pollen), hourly)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
        val alertTask = async { fetchOfficialAlerts(location, alertSource) }
        val forecastQuery = "&forecast_days=5&temperature_unit=celsius&wind_speed_unit=kmh&precipitation_unit=mm" +
            "&current=" + CURRENT_KEYS.joinToString(",") + "&hourly=" + HOURLY_KEYS.joinToString(",") + "&daily=sunrise,sunset"
        val root = fetchJson("https://api.open-meteo.com/v1/forecast?$where$elevation$modelQuery$forecastQuery")
        // Values the selected model does not provide are taken from best_match; the footer lists them.
        val filled = ArrayList<String>()
        if (model != "best_match") {
            val c0 = root.getJSONObject("current")
            val h0 = root.getJSONObject("hourly")
            val n0 = h0.getJSONArray("time").length()
            val missingCurrent = CURRENT_KEYS.filter { !c0.optDouble(it, Double.NaN).isFinite() }
            val missingHourly = HOURLY_KEYS.filter { key -> val a = h0.optJSONArray(key); a == null || (0 until n0).all { a.isNull(it) } }
            if (missingCurrent.isNotEmpty() || missingHourly.isNotEmpty()) try {
                val fb = fetchJson("https://api.open-meteo.com/v1/forecast?$where$elevation$forecastQuery")
                val fc = fb.getJSONObject("current")
                val fh = fb.getJSONObject("hourly")
                for (key in missingCurrent) { val v = fc.optDouble(key, Double.NaN); if (v.isFinite()) { c0.put(key, v); filled += key } }
                if (fh.getJSONArray("time").toString() == h0.getJSONArray("time").toString()) for (key in missingHourly) {
                    val a = fh.optJSONArray(key) ?: continue
                    if ((0 until n0).any { !a.isNull(it) }) { h0.put(key, a); if (key !in filled) filled += key }
                }
            } catch (e: CancellationException) { throw e } catch (e: Exception) { }
        }
        val zone = ZoneId.of(root.optString("timezone", "Europe/Bratislava"))
        val c = root.getJSONObject("current")
        val h = root.getJSONObject("hourly")
        val d = root.getJSONObject("daily")
        val timeArray = h.getJSONArray("time")
        val n = timeArray.length()
        val times = List(n) { timeArray.getString(it) }
        val dateKeys = times.map { it.substring(0, 10) }
        val temp = h.series("temperature_2m", n)
        val code = h.series("weather_code", n)
        val precip = h.series("precipitation", n)
        val rain = h.series("rain", n)
        val showers = h.series("showers", n)
        val snowfall = h.series("snowfall", n)
        val prob = h.series("precipitation_probability", n)
        val daylight = h.series("is_day", n)
        val cloud = h.series("cloud_cover", n)
        val wind = h.series("wind_speed_10m", n)
        val windDir = h.series("wind_direction_10m", n)
        val gust = h.series("wind_gusts_10m", n)
        val uv = h.series("uv_index", n)
        val nowTime = LocalDateTime.parse(c.getString("time"))
        val todayKey = nowTime.toLocalDate().toString()
        val keys = dateKeys.distinct().filter { it >= todayKey }.take(5)
        val air = airTask.await()
        val sunDates = d.optJSONArray("time")
        val sunriseArray = d.optJSONArray("sunrise")
        val sunsetArray = d.optJSONArray("sunset")
        fun sunAt(array: org.json.JSONArray?, key: String): Instant? {
            if (sunDates == null || array == null) return null
            for (i in 0 until sunDates.length()) {
                if (sunDates.getString(i) == key) {
                    return runCatching { LocalDateTime.parse(array.getString(i)).atZone(zone).toInstant() }.getOrNull()
                }
            }
            return null
        }

        val sunTimes = if (sunDates == null || sunriseArray == null || sunsetArray == null) emptyList() else (0 until sunDates.length()).mapNotNull { i ->
            runCatching { LocalDateTime.parse(sunriseArray.getString(i)).atZone(zone).toInstant() to LocalDateTime.parse(sunsetArray.getString(i)).atZone(zone).toInstant() }.getOrNull()
        }
        val days = keys.map { key ->
            val ix = times.indices.filter { dateKeys[it] == key }
            fun vals(a: DoubleArray) = ix.map { a[it] }.filter { it.isFinite() }
            val rainV = vals(rain)
            val showV = vals(showers)
            val snowV = vals(snowfall)
            val split = rainV.isNotEmpty() || showV.isNotEmpty() || snowV.isNotEmpty()
            val maxWindIndex = ix.filter { wind[it].isFinite() }.maxByOrNull { wind[it] }
            val counts = LinkedHashMap<Int, Int>()
            for (i in ix) {
                val hour = times[i].substring(11, 13).toInt()
                if (hour in 6..21 && code[i].isFinite()) {
                    val k = code[i].toInt()
                    counts[k] = (counts[k] ?: 0) + 1
                }
            }
            val repeated = counts.filter { it.value >= 2 }.keys
            val dayCode = when {
                repeated.isNotEmpty() -> repeated.reduce { a, b -> if (priority(b) > priority(a)) b else a }
                counts.isNotEmpty() -> counts.maxByOrNull { it.value }!!.key
                else -> -1
            }
            OutlookDay(
                date = LocalDate.parse(key), code = dayCode,
                tmin = vals(temp).minOrNull() ?: Double.NaN, tmax = vals(temp).maxOrNull() ?: Double.NaN,
                liquid = if (split) rainV.sum() + showV.sum() else vals(precip).sum(), snow = if (split) snowV.sum() else 0.0,
                rain = rainV.sum(), showers = showV.sum(), probability = vals(prob).maxOrNull() ?: Double.NaN,
                wind = maxWindIndex?.let { wind[it] } ?: Double.NaN, gust = vals(gust).maxOrNull() ?: Double.NaN,
                windDirection = maxWindIndex?.let { windDir[it] } ?: Double.NaN,
                daylightCloud = cloudMedian(ix.filter { daylight[it]==1.0 }.map { cloud[it] }),
                cloudMean = vals(cloud).average(), cloudMax = vals(cloud).maxOrNull() ?: Double.NaN,
                uv = vals(uv).maxOrNull() ?: Double.NaN,
                aqi = ix.mapNotNull { air?.hourlyAqi?.get(times[it]) }.maxOrNull() ?: Double.NaN,
            )
        }
        check(days.isNotEmpty()) { "Missing hourly data" }
        val hourNow = nowTime.withMinute(0).withSecond(0).withNano(0)
        val upcoming = times.indices.mapNotNull { i ->
            val t = runCatching { LocalDateTime.parse(times[i]) }.getOrNull()
            if (t == null || !t.isAfter(hourNow)) null
            else OverviewHour(t, temp[i], if (code[i].isFinite()) code[i].toInt() else -1, prob[i], precip[i], cloud[i], wind[i], gust[i], windDir[i])
        }
        val hours = upcoming.take(6)
        // An hour that cannot be parsed gets the epoch, so its window is long over and it never matches.
        val alertTimes = times.map { runCatching { LocalDateTime.parse(it).atZone(zone).toInstant() }.getOrDefault(Instant.EPOCH) }
        val alerts = alertTask.await() + (if ("local" in alertSourceSet(alertSource)) Instant.now().let { now -> localAlertHits(alertTimes, code, gust, precip, temp, now).map { localAlert(it, now) } } else emptyList())
        OverviewData(
            time = nowTime, zone = zone, latitude = location.latitude, longitude = location.longitude,
            temperature = c.optDouble("temperature_2m"), apparent = c.optDouble("apparent_temperature"), code = c.optInt("weather_code", -1), isDayFlag = c.optInt("is_day", 1) == 1,
            wind = c.optDouble("wind_speed_10m"), gust = c.optDouble("wind_gusts_10m"), direction = c.optDouble("wind_direction_10m"), cloud = c.optDouble("cloud_cover"), uv = c.optDouble("uv_index"),
            precipitation = c.optDouble("precipitation"), rain = c.optDouble("rain"), showers = c.optDouble("showers"), snowfall = c.optDouble("snowfall"),
            probability = c.optDouble("precipitation_probability"), intervalSeconds = c.optInt("interval", 900),
            sunrise = sunAt(sunriseArray, todayKey), sunset = sunAt(sunsetArray, todayKey),
            days = days, air = air?.now, alerts = alerts, fallback = filled, hours = hours, series = upcoming.take(48), sunTimes = sunTimes,
        )
    }
}

// weatherUnitNumber drops the sign of a value that rounds to zero, so 0 °C never shows as "-0".
internal fun overviewNumber(v: Double, unit: String = "", decimals: Int = 0): String =
    if (v.isFinite()) weatherUnitNumber(v, decimals, appLocale()) + unit else "—"

// Same branching order as the hero in chart.html.
internal fun overviewCondition(code: Int): String = when {
    code < 0 -> uiText(R.string.ui_stav_nie_je_dostupny_03c5a0b5)
    code == 0 -> uiText(R.string.ui_jasno_e78eee64)
    code <= 2 -> uiText(R.string.ui_polojasno_6ccdd079)
    code == 3 -> uiText(R.string.ui_zamracene_829159bb)
    code == 45 || code == 48 -> uiText(R.string.ui_hmla_386a47cf)
    code >= 95 -> uiText(R.string.ui_burka_da3df9bc)
    code in SNOW_CODES -> uiText(R.string.ui_snezenie_c9ed5546)
    code >= 80 -> uiText(R.string.ui_prehanky_ef949e7b)
    code >= 61 -> uiText(R.string.ui_dazd_8eda180e)
    code >= 51 -> uiText(R.string.ui_mrholenie_081d9f6f)
    else -> uiText(R.string.ui_stav_nie_je_dostupny_03c5a0b5)
}

internal fun cloudMedian(samples:List<Double>):Double {
    val a=samples.filter { it in 0.0..100.0 }.sorted(); if(a.isEmpty()) return Double.NaN
    return (a[(a.size-1)/2]+a[a.size/2])/2
}
