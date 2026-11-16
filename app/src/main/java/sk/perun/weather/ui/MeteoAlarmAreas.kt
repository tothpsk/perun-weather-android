package sk.perun.weather.ui

import android.content.res.AssetManager
import org.json.JSONObject
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap

// Outlines of the MeteoAlarm warning areas (EMMA_ID), one asset per country, built by tools/meteoalarm_areas.py.
// A place is matched to the warning areas by its position; names are only a fallback.
internal object MeteoAlarmAreas {
    private class Area(val code: String, val rings: List<DoubleArray>, val west: Double, val south: Double, val east: Double, val north: Double)
    @Volatile private var assets: AssetManager? = null
    private val countries = ConcurrentHashMap<String, List<Area>>()

    fun install(manager: AssetManager) { assets = manager }

    private fun load(country: String): List<Area> {
        val key = country.uppercase()
        countries[key]?.let { return it }
        val manager = assets ?: return emptyList()
        if (!Regex("[A-Z]{2}").matches(key)) return emptyList()
        val areas = try {
            val root = JSONObject(manager.open("meteoalarm/$key.json").use { String(it.readBytes(), Charsets.UTF_8) })
            val out = ArrayList<Area>()
            for (code in root.keys()) {
                val list = root.getJSONArray(code)
                val rings = ArrayList<DoubleArray>()
                var west = 180.0; var south = 90.0; var east = -180.0; var north = -90.0
                for (i in 0 until list.length()) {
                    val a = list.getJSONArray(i)
                    if (a.length() < 8 || a.length() % 2 != 0) continue
                    val ring = DoubleArray(a.length()) { a.getDouble(it) }
                    for (k in ring.indices step 2) {
                        west = minOf(west, ring[k]); east = maxOf(east, ring[k])
                        south = minOf(south, ring[k + 1]); north = maxOf(north, ring[k + 1])
                    }
                    rings += ring
                }
                if (rings.isNotEmpty()) out += Area(code, rings, west, south, east, north)
            }
            out
        } catch (e: Exception) {
            // A country without an outline file: its places are matched by name.
            emptyList()
        }
        countries[key] = areas
        return areas
    }

    // Even-odd rule over all rings of an area, so that holes and separate parts need no special handling.
    private fun inside(rings: List<DoubleArray>, lon: Double, lat: Double): Boolean {
        var odd = false
        for (r in rings) {
            val n = r.size / 2
            var j = n - 1
            for (i in 0 until n) {
                val xi = r[2 * i]; val yi = r[2 * i + 1]; val xj = r[2 * j]; val yj = r[2 * j + 1]
                if ((yi > lat) != (yj > lat) && lon < xi + (lat - yi) * (xj - xi) / (yj - yi)) odd = !odd
                j = i
            }
        }
        return odd
    }

    // Codes of all areas that contain the point; empty when the country has no outlines or the point lies in none.
    fun codes(country: String, lat: Double, lon: Double): Set<String> =
        load(country).filter { lon >= it.west && lon <= it.east && lat >= it.south && lat <= it.north && inside(it.rings, lon, lat) }.map { it.code }.toSet()

    // Whether the outlines know this code at all. An unknown code is matched by name, so a new area is never dropped silently.
    fun knows(country: String, code: String): Boolean = load(country).any { it.code == code }
}

// Words that only name the kind of an administrative unit. "Region" and "kraj" stay on purpose: a region must never match a district of the same name.
private val AREA_KIND = Regex("\\b(okres|district of|district|county|powiat|landkreis|kreis und stadt|kreis|stadt)\\b")
internal fun areaCore(v: String): String {
    var s = Normalizer.normalize(v, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()
    s = s.substringBefore(" - ").replace(Regex("^.*\\bprovince\\s+"), "")
    s = s.replace(AREA_KIND, " ").replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
    return s.replace(Regex("\\s+[ivx]+$"), "").trim()
}
// Names used when the position cannot decide: the district levels, or for a place saved without them its region or name.
internal fun alertAreaNames(l: Location): Set<String> {
    val districts = listOf(l.admin3, l.admin).filter { it.isNotBlank() }
    val names = districts.ifEmpty { listOfNotNull(listOf(l.region, l.name).firstOrNull { it.isNotBlank() }) }
    return names.map { areaCore(it) }.filter { it.isNotEmpty() }.toSet()
}
