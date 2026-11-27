package sk.perun.weather.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.LocationManager
import android.location.LocationRequest
import android.os.CancellationSignal
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.coroutines.resume
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/** A position of the device and the time it was taken. */
internal class DeviceFix(val latitude: Double, val longitude: Double, val time: Long)

/** Distance in kilometres between two positions (haversine). */
internal fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = Math.toRadians(1.0)
    val a = sin((lat2 - lat1) * r / 2).pow(2) + cos(lat1 * r) * cos(lat2 * r) * sin((lon2 - lon1) * r / 2).pow(2)
    return 2 * 6371.0 * asin(sqrt(a.coerceIn(0.0, 1.0)))
}

/** Coordinates sent to the services: three decimals, about 100 m. */
internal fun roundPosition(x: Double): Double = round(x * 1000) / 1000

/**
 * What the Current location card does with a new position: the coordinates move after at least [MoveKm], so small
 * jitter of the position does not reload the screens; the name of the place is looked up again after more than
 * [NameKm] from the place where it was looked up last, or when the card has no country yet.
 */
internal class CurrentMove(val move: Boolean, val rename: Boolean)
internal const val MoveKm = 0.3
internal const val NameKm = 2.0
internal fun currentMove(card: Location, namedLat: Double?, namedLon: Double?, lat: Double, lon: Double): CurrentMove {
    val move = distanceKm(card.latitude, card.longitude, lat, lon) >= MoveKm
    val rename = card.countryCode.isBlank() || namedLat == null || namedLon == null || distanceKm(namedLat, namedLon, lat, lon) > NameKm
    return CurrentMove(move, rename)
}

/**
 * The Current location: one saved place whose position follows the device. A precise position (GPS) is taken when it
 * is added, when the app opens and when a widget is refreshed with its button; background refreshes take the
 * power-saving one (network and Wi-Fi). Only the last position is kept, without any history.
 */
internal object CurrentLocation {
    const val Key = "gps"
    private const val PrefsName = "perun_current_location"
    private val lock = Mutex()

    fun prefs(c: Context): SharedPreferences = c.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
    private fun granted(c: Context, permission: String) = ContextCompat.checkSelfPermission(c, permission) == PackageManager.PERMISSION_GRANTED
    fun foreground(c: Context) = granted(c, Manifest.permission.ACCESS_COARSE_LOCATION) || granted(c, Manifest.permission.ACCESS_FINE_LOCATION)
    fun precise(c: Context) = granted(c, Manifest.permission.ACCESS_FINE_LOCATION)
    fun background(c: Context) = foreground(c) && granted(c, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    /** Time of the last position of the card, in milliseconds; 0 when unknown. */
    fun fixTime(c: Context): Long = prefs(c).getLong("fixTime", 0L)

    /** The position of the device: up to 10 s for a new one, then the last known one. Null without permission or with location off. */
    @SuppressLint("MissingPermission")
    suspend fun fix(c: Context, precise: Boolean): DeviceFix? {
        if (!foreground(c)) return null
        val manager = c.getSystemService(LocationManager::class.java) ?: return null
        if (!manager.isLocationEnabled) return null
        val quality = if (precise && precise(c)) LocationRequest.QUALITY_HIGH_ACCURACY else LocationRequest.QUALITY_BALANCED_POWER_ACCURACY
        val request = LocationRequest.Builder(0L).setQuality(quality).setDurationMillis(10_000L).build()
        val fresh = withTimeoutOrNull(11_000L) {
            suspendCancellableCoroutine<android.location.Location?> { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                try {
                    manager.getCurrentLocation(LocationManager.FUSED_PROVIDER, request, signal, c.mainExecutor) { if (cont.isActive) cont.resume(it) }
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
        val known = fresh ?: try { manager.getLastKnownLocation(LocationManager.FUSED_PROVIDER) } catch (e: Exception) { null }
        return known?.let { DeviceFix(it.latitude, it.longitude, it.time) }
    }

    /** A new Current location card at the device position, or null when the position is not available. */
    suspend fun create(c: Context): Location? {
        val fix = fix(c, precise = true) ?: return null
        val lat = roundPosition(fix.latitude); val lon = roundPosition(fix.longitude)
        val place = withContext(Dispatchers.IO) { try { reversePlace(lat, lon) } catch (e: Exception) { null } }
        val card = (place ?: Location(coordinateLabel(lat, lon), "", lat, lon)).copy(latitude = lat, longitude = lon, elevation = null, current = true)
        val edit = prefs(c).edit().putLong("fixTime", fix.time)
        if (place != null) edit.putFloat("namedLat", lat.toFloat()).putFloat("namedLon", lon.toFloat()) else edit.remove("namedLat").remove("namedLon")
        edit.apply()
        return card
    }

    /**
     * Moves the saved Current location card to the device position. [fromBackground] runs need the permission to use the
     * position in the background. A new position is not taken when the last one is younger than [minAgeMs].
     * Returns true when the saved card changed.
     */
    suspend fun update(c: Context, precise: Boolean, fromBackground: Boolean, minAgeMs: Long = if (precise) 60_000L else 5 * 60_000L): Boolean = lock.withLock {
        try {
            val store = LocationStore(c)
            val card = store.load().firstOrNull { it.current } ?: return@withLock false
            if (if (fromBackground) !background(c) else !foreground(c)) return@withLock false
            if (System.currentTimeMillis() - fixTime(c) < minAgeMs) return@withLock false
            val fix = fix(c, precise) ?: return@withLock false
            val p = prefs(c)
            p.edit().putLong("fixTime", maxOf(fix.time, fixTime(c))).apply()
            val lat = roundPosition(fix.latitude); val lon = roundPosition(fix.longitude)
            val namedLat = if (p.contains("namedLat")) p.getFloat("namedLat", 0f).toDouble() else null
            val namedLon = if (p.contains("namedLon")) p.getFloat("namedLon", 0f).toDouble() else null
            val step = currentMove(card, namedLat, namedLon, lat, lon)
            var next = card
            if (step.move) next = next.copy(latitude = lat, longitude = lon, elevation = null)
            if (step.rename) {
                val place = withContext(Dispatchers.IO) { try { reversePlace(lat, lon) } catch (e: Exception) { null } }
                if (place != null) {
                    next = next.copy(name = place.name, country = place.country, countryCode = place.countryCode, region = place.region,
                        admin = place.admin, admin3 = place.admin3, latitude = lat, longitude = lon, elevation = null, timezone = "auto")
                    p.edit().putFloat("namedLat", lat.toFloat()).putFloat("namedLon", lon.toFloat()).apply()
                }
            }
            if (next == card) return@withLock false
            // The list is read again, so that a change made meanwhile in the app is kept.
            val latest = store.load()
            val at = latest.indexOfFirst { it.current }
            if (at < 0) return@withLock false
            store.save(latest.toMutableList().also { it[at] = next }, store.loadSelected())
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }
}

/** Time of the last position of the Current location, following background updates. */
@Composable internal fun rememberCurrentFixTime(): Long {
    val context = LocalContext.current
    val p = remember(context) { CurrentLocation.prefs(context) }
    var value by remember(p) { mutableLongStateOf(p.getLong("fixTime", 0L)) }
    DisposableEffect(p) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> value = p.getLong("fixTime", 0L) }
        p.registerOnSharedPreferenceChangeListener(listener)
        onDispose { p.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return value
}

/** "Current location · 14:20", or only "Current location" before the first position. */
internal fun currentLocationLabel(fixTime: Long): String {
    if (fixTime <= 0L) return uiText(sk.perun.weather.R.string.loc_current)
    val at = Instant.ofEpochMilli(fixTime).atZone(ZoneId.systemDefault())
    val today = at.toLocalDate() == java.time.LocalDate.now(ZoneId.systemDefault())
    val pattern = if (today) "HH:mm" else appPattern("d. M. HH:mm")
    return uiText(sk.perun.weather.R.string.loc_current_at, at.format(DateTimeFormatter.ofPattern(pattern, appLocale())))
}
