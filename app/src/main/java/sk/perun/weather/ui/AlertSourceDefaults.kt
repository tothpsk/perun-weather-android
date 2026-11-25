package sk.perun.weather.ui

import android.content.Context

/** Sources of an installation made before 1.4.0: local estimates, MeteoAlarm and the FOSS public alert server. */
internal const val LegacyAlertSources = "v2:local,meteo,kde"
/** Sources of a new installation: all of them. */
internal const val AllAlertSources = "v2:local,meteo,libre,kde"
private const val DefaultKey = "alertSourceDefault"

/**
 * Sources used for a place without its own choice. Decided once, at the first start of 1.4.0 or later, and stored:
 * a new installation gets all sources; an update keeps the sources it had, so LibreWXR is not turned on silently.
 */
internal fun initialAlertSources(firstInstall: Long, lastUpdate: Long, hasSettings: Boolean): String =
    if (firstInstall == lastUpdate && !hasSettings) AllAlertSources else LegacyAlertSources

internal object AlertSourceDefaults {
    @Volatile var value: String = LegacyAlertSources
        private set

    fun install(context: Context) {
        val forecast = context.getSharedPreferences("perun_forecast", Context.MODE_PRIVATE)
        val stored = forecast.getString(DefaultKey, null)
        if (stored != null) { value = normalizeAlertSource(stored); return }
        val places = context.getSharedPreferences("perun_location_preferences", Context.MODE_PRIVATE)
        val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        val decided = initialAlertSources(info?.firstInstallTime ?: 0L, info?.lastUpdateTime ?: 1L, forecast.all.isNotEmpty() || places.all.isNotEmpty())
        forecast.edit().putString(DefaultKey, decided).apply()
        value = decided
    }
}
