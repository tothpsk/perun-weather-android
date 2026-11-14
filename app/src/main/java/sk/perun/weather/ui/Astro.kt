package sk.perun.weather.ui

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

internal data class MoonDay(val rise: Instant?, val set: Instant?, val illumination: Int, val waxing: Boolean, val southern: Boolean)

// Port of the sun/moon helpers in the desktop chart.html.
internal object Astro {
    private data class Equatorial(val ra: Double, val dec: Double, val elongation: Double)

    private fun rad(d: Double) = d * PI / 180.0
    private fun deg(r: Double) = r * 180.0 / PI
    private fun norm360(d: Double) = ((d % 360.0) + 360.0) % 360.0

    private fun julianDay(instant: Instant): Double {
        val t = instant.atZone(ZoneOffset.UTC)
        val day = t.dayOfMonth + (t.hour + t.minute / 60.0 + t.second / 3600.0) / 24.0
        var y = t.year
        var m = t.monthValue
        if (m <= 2) { y -= 1; m += 12 }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    private fun moonEquatorial(instant: Instant): Equatorial {
        val jd = julianDay(instant)
        val t = (jd - 2451545.0) / 36525.0
        val lp = norm360(218.3164477 + 481267.88123421 * t)
        val d = norm360(297.8501921 + 445267.1114034 * t)
        val m = norm360(357.5291092 + 35999.0502909 * t)
        val mp = norm360(134.9633964 + 477198.8675055 * t)
        val f = norm360(93.2720950 + 483202.0175233 * t)
        val dr = rad(d)
        val mr = rad(m)
        val mpr = rad(mp)
        val fr = rad(f)
        val lon = lp + (6.289 * sin(mpr) - 1.274 * sin(2 * dr - mpr) + 0.658 * sin(2 * dr) -
            0.186 * sin(mr) - 0.059 * sin(2 * mpr - 2 * dr) - 0.057 * sin(mpr - 2 * dr + mr) +
            0.053 * sin(mpr + 2 * dr) + 0.046 * sin(2 * dr - mr) + 0.041 * sin(mpr - mr) -
            0.035 * sin(dr) - 0.031 * sin(mpr + mr))
        val lat = 5.128 * sin(fr) + 0.281 * sin(mpr + fr) - 0.278 * sin(fr - mpr) -
            0.173 * sin(2 * dr - fr) + 0.055 * sin(2 * dr + fr - mpr) +
            0.046 * sin(2 * dr - fr - mpr) + 0.033 * sin(fr + 2 * mpr)
        val eps = rad(23.439281)
        val lonR = rad(norm360(lon))
        val latR = rad(lat)
        val ra = norm360(deg(atan2(sin(lonR) * cos(eps) - tan(latR) * sin(eps), cos(lonR))))
        val dec = deg(asin(sin(latR) * cos(eps) + cos(latR) * sin(eps) * sin(lonR)))
        val sunL = norm360(280.4665 + 36000.7698 * t)
        return Equatorial(ra, dec, norm360(lon - sunL))
    }

    private fun localSiderealDeg(instant: Instant, lonDeg: Double): Double {
        val jd = julianDay(instant)
        val t = (jd - 2451545.0) / 36525.0
        val gmst = 280.46061837 + 360.98564736629 * (jd - 2451545.0) + 0.000387933 * t * t - (t * t * t) / 38710000.0
        return norm360(gmst + lonDeg)
    }

    private fun altitude(raDeg: Double, decDeg: Double, instant: Instant, latDeg: Double, lonDeg: Double): Double {
        val lst = localSiderealDeg(instant, lonDeg)
        val haDeg = norm360(lst - raDeg)
        val ha = rad(if (haDeg > 180) haDeg - 360 else haDeg)
        val dec = rad(decDeg)
        val lat = rad(latDeg)
        val sinAlt = sin(lat) * sin(dec) + cos(lat) * cos(dec) * cos(ha)
        return deg(asin(max(-1.0, min(1.0, sinAlt))))
    }

    private fun moonAltitude(instant: Instant, lat: Double, lon: Double): Double {
        val e = moonEquatorial(instant)
        return altitude(e.ra, e.dec, instant, lat, lon)
    }

    fun moonDay(dayStart: Instant, noon: Instant, lat: Double, lon: Double): MoonDay {
        var prev = moonAltitude(dayStart, lat, lon)
        var rise: Instant? = null
        var set: Instant? = null
        for (i in 1..144) {
            val t = dayStart.plusSeconds(i * 600L)
            val alt = moonAltitude(t, lat, lon)
            if (prev < 0 && alt >= 0 && rise == null) rise = t
            if (prev >= 0 && alt < 0 && set == null) set = t
            prev = alt
        }
        val elongation = moonEquatorial(noon).elongation
        val illumination = (((1 - cos(rad(elongation))) / 2) * 100).roundToInt()
        return MoonDay(rise, set, illumination, elongation < 180, lat < 0)
    }

    // Port of pwSunVerticalPosition: +1 at solar noon, 0 at sunrise/sunset, -1 at midnight.
    fun sunVerticalPosition(rise: Instant?, set: Instant?, now: Instant, zone: ZoneId): Double? {
        if (rise == null || set == null || !set.isAfter(rise)) return null
        val day = now.atZone(zone).toLocalDate()
        val midnight = day.atStartOfDay(zone).toInstant()
        val nextMidnight = day.plusDays(1).atStartOfDay(zone).toInstant()
        if (rise.isBefore(midnight) || set.isAfter(nextMidnight)) return null
        fun span(a: Instant, b: Instant) = (b.toEpochMilli() - a.toEpochMilli()).toDouble()
        fun clamp(x: Double) = max(0.0, min(1.0, x))
        return when {
            !now.isBefore(rise) && !now.isAfter(set) -> sin(PI * clamp(span(rise, now) / span(rise, set)))
            now.isBefore(rise) ->
                if (!rise.isAfter(midnight)) null else -cos(PI * clamp(span(midnight, now) / span(midnight, rise)) / 2)
            else ->
                if (!nextMidnight.isAfter(set)) null else -sin(PI * clamp(span(set, now) / span(set, nextMidnight)) / 2)
        }
    }
}
