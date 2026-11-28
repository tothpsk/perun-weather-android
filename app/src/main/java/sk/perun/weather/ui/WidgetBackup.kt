package sk.perun.weather.ui

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * The settings of the widgets in the backup.
 *
 * Android gives every placed widget a new number, so the settings stored under the old numbers do not fit the widgets of a
 * new phone or of a new installation. The backup therefore also keeps every widget as its kind and its settings. After
 * a restore these wait in [PendingStore]; the next widget of the same kind placed without its own settings takes the first
 * of them into its form. The entry is used up when the form is saved and freed again when the widget is removed unsaved.
 */
internal object WidgetBackup {
    const val PendingStore = "perun_widget_restore"

    /** The preference store of a widget kind; the widgets without their own store keep their settings in perun_widgets. */
    fun storeOf(kind: String): String = when (kind) {
        "DayHoursWidget" -> "perun_widget_day"
        "AirWidget" -> "perun_widget_air"
        "SkyWidget" -> "perun_widget_sky"
        else -> "perun_widgets"
    }

    /** Keys that only hold the state of a widget (the shown day, the downloaded data); they are never backed up. */
    val StatePrefixes: Map<String, List<String>> = mapOf("perun_widget_day" to listOf("date_", "key_", "at_", "alert_"))

    fun isState(store: String, key: String): Boolean = StatePrefixes[store].orEmpty().any { key.startsWith(it) }

    /** The settings of one widget: its keys without the "_number" ending. */
    private fun settingsOf(p: SharedPreferences, store: String, id: Int): Map<String, Any?> {
        val end = "_$id"
        return p.all.filterKeys { it.endsWith(end) && !isState(store, it) }.mapKeys { it.key.removeSuffix(end) }
    }

    /** Every placed widget with its kind and settings. */
    fun export(c: Context, typed: (Any?) -> JSONObject?): JSONArray {
        val out = JSONArray()
        PerunWidgets.Listed.forEach { kind ->
            val name = kind.simpleName;val store = storeOf(name)
            val p = c.getSharedPreferences(store, Context.MODE_PRIVATE)
            PerunWidgets.ids(c, kind).forEach { id ->
                val values = JSONObject()
                settingsOf(p, store, id).forEach { (k, v) -> typed(v)?.let { values.put(k, it) } }
                if (values.length() > 0) out.put(JSONObject().put("kind", name).put("id", id).put("values", values))
            }
        }
        return out
    }

    /**
     * Keeps the widgets of a backup for the widgets placed later. A widget that still stands on this phone under the same
     * number and kind already has its settings from the stores, so it is left out.
     */
    fun keep(c: Context, widgets: JSONArray?) {
        val manager = AppWidgetManager.getInstance(c)
        val pending = JSONArray()
        if (widgets != null) for (i in 0 until widgets.length()) {
            val w = widgets.optJSONObject(i) ?: continue
            val kind = w.optString("kind");val id = w.optInt("id", -1)
            if (kind.isEmpty() || w.optJSONObject("values") == null) continue
            if (manager.getAppWidgetInfo(id)?.provider?.className?.substringAfterLast('.') == kind) continue
            pending.put(JSONObject().put("kind", kind).put("values", w.getJSONObject("values")).put("taken", -1))
        }
        c.getSharedPreferences(PendingStore, Context.MODE_PRIVATE).edit().putString("pending", pending.toString()).commit()
    }

    private fun pending(c: Context): JSONArray =
        runCatching { JSONArray(c.getSharedPreferences(PendingStore, Context.MODE_PRIVATE).getString("pending", "[]")) }.getOrDefault(JSONArray())

    private fun save(c: Context, a: JSONArray) {
        c.getSharedPreferences(PendingStore, Context.MODE_PRIVATE).edit().putString("pending", a.toString()).apply()
    }

    /**
     * Called when the form of a new widget opens: a widget without its own settings gets the first waiting settings of its
     * kind, written under its number, so the form shows them. Returns whether it did.
     */
    fun prefill(c: Context, id: Int, read: (JSONObject) -> Any?): Boolean {
        val kind = AppWidgetManager.getInstance(c).getAppWidgetInfo(id)?.provider?.className?.substringAfterLast('.') ?: return false
        val store = storeOf(kind)
        val p = c.getSharedPreferences(store, Context.MODE_PRIVATE)
        if (p.contains("loc_$id")) return false
        val all = pending(c)
        val index = (0 until all.length()).firstOrNull { all.getJSONObject(it).let { e -> e.optString("kind") == kind && e.optInt("taken", -1) == id } }
            ?: (0 until all.length()).firstOrNull { all.getJSONObject(it).let { e -> e.optString("kind") == kind && e.optInt("taken", -1) == -1 } }
            ?: return false
        val entry = all.getJSONObject(index)
        val values = entry.getJSONObject("values")
        val edit = p.edit()
        values.keys().forEach { k -> putTyped(edit, k + "_$id", read(values.getJSONObject(k))) }
        edit.commit()
        entry.put("taken", id);save(c, all)
        return true
    }

    /** The form of the widget was saved: its waiting settings are used up. */
    fun commit(c: Context, id: Int) {
        val all = pending(c);val next = JSONArray()
        for (i in 0 until all.length()) all.getJSONObject(i).let { if (it.optInt("taken", -1) != id) next.put(it) }
        if (next.length() != all.length()) save(c, next)
    }

    /** Widgets were removed: settings they took but never saved wait again. */
    fun release(c: Context, ids: IntArray) {
        val all = pending(c);var changed = false
        for (i in 0 until all.length()) all.getJSONObject(i).let { if (it.optInt("taken", -1) in ids) { it.put("taken", -1);changed = true } }
        if (changed) save(c, all)
    }

    private fun putTyped(e: SharedPreferences.Editor, k: String, v: Any?) {
        when (v) {
            is Boolean -> e.putBoolean(k, v)
            is Int -> e.putInt(k, v)
            is Long -> e.putLong(k, v)
            is Float -> e.putFloat(k, v)
            is String -> e.putString(k, v)
            is Set<*> -> e.putStringSet(k, v.map { it.toString() }.toSet())
        }
    }
}
