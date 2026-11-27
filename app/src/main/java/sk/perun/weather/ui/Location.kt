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
    val admin3: String = "",
)

internal class LocationStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("perun_locations", Context.MODE_PRIVATE)

    // There is no preset place: an empty list means that the user has not added a location yet.
    fun load(): List<Location> {
        val raw = prefs.getString("locations", null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            List(array.length()) { i ->
                val o = array.getJSONObject(i)
                Location(
                    o.getString("name"), o.optString("country", ""), o.getDouble("latitude"), o.getDouble("longitude"),
                    if (o.has("elevation") && !o.isNull("elevation")) o.getDouble("elevation") else null,
                    o.optString("timezone", "auto"), o.optString("countryCode", ""), o.optString("region", ""), o.optString("admin", ""), o.optString("admin3", ""),
                )
            }
        } catch (e: Exception) {
            emptyList()
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
            o.put("admin3", l.admin3)
            array.put(o)
        }
        prefs.edit().putString("locations", array.toString()).putInt("selected", selected).apply()
    }
}

internal class LocationState(private val store: LocationStore) {
    var locations by mutableStateOf(store.load())
        private set
    var selected by mutableIntStateOf(store.loadSelected().coerceIn(0, maxOf(0, locations.lastIndex)))
        private set

    // Only valid when locations is not empty; PerunApp shows the first-run screen otherwise.
    val current: Location get() = locations[selected]

    private fun commit(list: List<Location>, index: Int) {
        locations = list
        selected = index.coerceIn(0, maxOf(0, list.lastIndex))
        store.save(list, selected)
    }

    fun select(index: Int) = commit(locations, index)

    // Fills in what a failed lookup left out (country, country code, region, district) once the lookup succeeds.
    // The position, and everything keyed by it, stays the same; the name is replaced only while it is still the coordinate label.
    fun enrich(old: Location, fresh: Location) {
        if (fresh.countryCode.isBlank()) return
        val i = locations.indexOfFirst { abs(it.latitude - old.latitude) < 1e-9 && abs(it.longitude - old.longitude) < 1e-9 }
        if (i < 0) return
        val current = locations[i]
        if (current.countryCode.isNotBlank()) return
        val merged = current.copy(
            name = if (current.name == coordinateLabel(current.latitude, current.longitude)) fresh.name else current.name,
            country = current.country.ifBlank { fresh.country },
            countryCode = fresh.countryCode,
            region = current.region.ifBlank { fresh.region },
            admin = current.admin.ifBlank { fresh.admin },
            admin3 = current.admin3.ifBlank { fresh.admin3 },
        )
        commit(locations.toMutableList().also { it[i] = merged }, selected)
    }

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

    // Undo of a removal: the place returns to its position, and is selected again when it was selected.
    fun restore(index: Int, location: Location, wasSelected: Boolean) {
        if (locations.any { abs(it.latitude - location.latitude) < 1e-9 && abs(it.longitude - location.longitude) < 1e-9 }) return
        val at = index.coerceIn(0, locations.size)
        val list = locations.toMutableList().also { it.add(at, location) }
        commit(list, if (wasSelected) at else if (selected >= at) selected + 1 else selected)
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
