package sk.perun.weather.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.min

// Same fields as the desktop Location dataclass (config.py).
internal data class Location(
    val name: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    val elevation: Double? = null,
    val timezone: String = "auto",
    val countryCode: String = "",
    val region: String = "",
    val admin: String = "",
)

internal val DefaultLocation = Location("Bratislava", "Slovakia", 48.1482, 17.1067, null, "Europe/Bratislava", "SK", "Bratislava Region")

internal class LocationStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("perun_locations", Context.MODE_PRIVATE)

    fun load(): List<Location> {
        val raw = prefs.getString("locations", null) ?: return listOf(DefaultLocation)
        return try {
            val array = JSONArray(raw)
            val list = List(array.length()) { i ->
                val o = array.getJSONObject(i)
                Location(
                    o.getString("name"), o.optString("country", ""), o.getDouble("latitude"), o.getDouble("longitude"),
                    if (o.has("elevation") && !o.isNull("elevation")) o.getDouble("elevation") else null,
                    o.optString("timezone", "auto"), o.optString("countryCode", ""), o.optString("region", ""), o.optString("admin", ""),
                )
            }
            list.ifEmpty { listOf(DefaultLocation) }
        } catch (e: Exception) {
            listOf(DefaultLocation)
        }
    }

    fun loadSelected(): Int = prefs.getInt("selected", 0)

    fun save(list: List<Location>, selected: Int) {
        val array = JSONArray()
        for (l in list) {
            val o = JSONObject()
            o.put("name", l.name)
            o.put("country", l.country)
            o.put("latitude", l.latitude)
            o.put("longitude", l.longitude)
            if (l.elevation != null) o.put("elevation", l.elevation)
            o.put("timezone", l.timezone)
            o.put("countryCode", l.countryCode)
            o.put("region", l.region)
            o.put("admin", l.admin)
            array.put(o)
        }
        prefs.edit().putString("locations", array.toString()).putInt("selected", selected).apply()
    }
}

internal class LocationState(private val store: LocationStore) {
    var locations by mutableStateOf(store.load())
        private set
    var selected by mutableIntStateOf(store.loadSelected().coerceIn(0, locations.lastIndex))
        private set

    val current: Location get() = locations[selected]

    private fun commit(list: List<Location>, index: Int) {
        locations = list
        selected = index.coerceIn(0, list.lastIndex)
        store.save(list, selected)
    }

    fun select(index: Int) = commit(locations, index)

    // A place within about 100 m of a saved one just selects the saved one.
    fun add(location: Location) {
        val same = locations.indexOfFirst { abs(it.latitude - location.latitude) < 0.001 && abs(it.longitude - location.longitude) < 0.001 }
        if (same >= 0) select(same) else commit(locations + location, locations.size)
    }

    fun remove(index: Int) {
        if (locations.size <= 1 || index !in locations.indices) return
        val list = locations.toMutableList().also { it.removeAt(index) }
        val newSelected = when {
            selected > index -> selected - 1
            selected == index -> min(selected, list.lastIndex)
            else -> selected
        }
        commit(list, newSelected)
    }

    fun move(index: Int, delta: Int) {
        val target = index + delta
        if (index !in locations.indices || target !in locations.indices) return
        val list = locations.toMutableList()
        val item = list.removeAt(index)
        list.add(target, item)
        val newSelected = when (selected) {
            index -> target
            target -> index
            else -> selected
        }
        commit(list, newSelected)
    }

    // New order after dragging; the selected place stays selected.
    fun reorder(list: List<Location>) {
        if (list.size != locations.size || list.toSet() != locations.toSet()) return
        commit(list, list.indexOf(current))
    }
}
