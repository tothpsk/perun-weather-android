package sk.perun.weather.ui

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.os.CancellationSignal
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import kotlin.coroutines.resume

// Port of _COORD_PATTERN / _parse_coordinates from the desktop main.py.
private val COORDINATES = Regex(
    "^\\s*(-?\\d+(?:\\.\\d+)?)\\s*°?\\s*([NSEW])?\\.?\\s*[,;\\s]+\\s*(-?\\d+(?:\\.\\d+)?)\\s*°?\\s*([NSEW])?\\.?\\s*$",
    RegexOption.IGNORE_CASE,
)

internal fun parseCoordinates(query: String): Pair<Double, Double>? {
    val m = COORDINATES.matchEntire(query.trim()) ?: return null
    val a = m.groupValues[1].toDouble()
    val aHem = m.groupValues[2].uppercase()
    val b = m.groupValues[3].toDouble()
    val bHem = m.groupValues[4].uppercase()
    var lat: Double
    var lon: Double
    var latHem: String
    var lonHem: String
    if (aHem == "N" || aHem == "S" || bHem == "E" || bHem == "W") {
        lat = a; latHem = aHem; lon = b; lonHem = bHem
    } else if (aHem == "E" || aHem == "W" || bHem == "N" || bHem == "S") {
        lon = a; lonHem = aHem; lat = b; latHem = bHem
    } else {
        lat = a; latHem = ""; lon = b; lonHem = ""
    }
    if (latHem == "S") lat = -Math.abs(lat) else if (latHem == "N") lat = Math.abs(lat)
    if (lonHem == "W") lon = -Math.abs(lon) else if (lonHem == "E") lon = Math.abs(lon)
    if (lat < -90 || lat > 90 || lon < -180 || lon > 180) return null
    return Math.round(lat * 10000) / 10000.0 to Math.round(lon * 10000) / 10000.0
}

internal fun coordinateLabel(lat: Double, lon: Double): String = String.format(Locale.US, "%.4f, %.4f", lat, lon)

private fun JSONObject.str(key: String): String = if (isNull(key)) "" else optString(key, "")

// Port of api.reverse_geocode (Nominatim). Returns null when the lookup fails.
internal fun reversePlace(lat: Double, lon: Double): Location? {
    return try {
        val url = "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lon&format=jsonv2&zoom=10&addressdetails=1&accept-language=en"
        val data = JSONObject(String(fetchBytes(url), Charsets.UTF_8))
        if (data.has("error")) return null
        val address = data.optJSONObject("address") ?: JSONObject()
        val name = listOf("city", "town", "village", "municipality", "hamlet").map { address.str(it) }.firstOrNull { it.isNotEmpty() }
            ?: data.str("name").ifEmpty { coordinateLabel(lat, lon) }
        val region = listOf("county", "state_district", "state").map { address.str(it) }.firstOrNull { it.isNotEmpty() } ?: ""
        Location(name, address.str("country"), lat, lon, null, "auto", address.str("country_code").uppercase(), region)
    } catch (e: Exception) {
        null
    }
}

// Coordinates are resolved through reverse geocoding, everything else through Open-Meteo geocoding.
internal fun searchPlaces(query: String): List<Location> {
    val coordinates = parseCoordinates(query)
    if (coordinates != null) {
        val (lat, lon) = coordinates
        return listOf(reversePlace(lat, lon) ?: Location(coordinateLabel(lat, lon), "", lat, lon))
    }
    val url = "https://geocoding-api.open-meteo.com/v1/search?name=" + URLEncoder.encode(query.trim(), "UTF-8") + "&count=8&language=en&format=json"
    val results = JSONObject(String(fetchBytes(url), Charsets.UTF_8)).optJSONArray("results") ?: return emptyList()
    return List(results.length()) { i ->
        val r = results.getJSONObject(i)
        Location(
            r.str("name"), r.str("country"), r.getDouble("latitude"), r.getDouble("longitude"),
            if (r.has("elevation") && !r.isNull("elevation")) r.getDouble("elevation") else null,
            r.str("timezone").ifEmpty { "auto" }, r.str("country_code").uppercase(), r.str("admin1"), r.str("admin2"),
        )
    }
}

@SuppressLint("MissingPermission")
internal suspend fun currentFix(context: Context): Pair<Double, Double>? = suspendCancellableCoroutine { cont ->
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val signal = CancellationSignal()
    cont.invokeOnCancellation { signal.cancel() }
    try {
        manager.getCurrentLocation(LocationManager.FUSED_PROVIDER, signal, context.mainExecutor) { fix ->
            if (cont.isActive) cont.resume(fix?.let { it.latitude to it.longitude })
        }
    } catch (e: Exception) {
        if (cont.isActive) cont.resume(null)
    }
}
