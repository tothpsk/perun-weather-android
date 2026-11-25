package sk.perun.weather.ui

import android.content.Context
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The levels of the official warnings that the widgets show, from the strongest. Local estimates stay in the app. */
internal val WidgetAlertLevels=listOf("red","orange","yellow")

/**
 * The warning of a widget: the strongest official warning whose validity overlaps the period that the widget shows
 * (from inclusive, to exclusive); at an equal level the one that starts first.
 */
internal fun widgetAlert(alerts:List<UnifiedAlert>,from:Instant,to:Instant):UnifiedAlert? =
    alerts.filter { a -> !a.local && a.level.key in WidgetAlertLevels && a.start.isBefore(to) && a.end.isAfter(from) }
        .minWithOrNull(compareBy<UnifiedAlert>({ WidgetAlertLevels.indexOf(it.level.key) },{ it.start }))

/** The period of whole local days from the day of `from` on, `days` days long. */
internal fun widgetDays(from:Instant,zone:ZoneId,days:Long):Pair<Instant,Instant> {
    val day=from.atZone(zone).toLocalDate()
    return from to day.plusDays(days).atStartOfDay(zone).toInstant()
}

/** The next 24 hours: the period of the widgets without a time axis (Compact, Sun and Moon, Air and pollen). */
internal fun widgetNextDay(now:Instant=Instant.now()):Pair<Instant,Instant> = now to now.plusSeconds(24*3600)

/**
 * The official warnings of a place for the widgets that do not load the forecast (Sun and Moon, Air and pollen), kept for
 * 25 minutes so that the widgets download them at most once in that time. A failed download keeps the last ones for up to
 * six hours. Network: call it from a worker only.
 */
internal fun widgetPlaceAlerts(c:Context,l:Location):List<UnifiedAlert> {
    val p=c.applicationContext.getSharedPreferences("perun_widget_alerts",Context.MODE_PRIVATE)
    val key=BackgroundRefresh.locationKey(l);val at=p.getLong(key+"_at",0L);val now=System.currentTimeMillis()/1000
    if(now-at<25*60) return widgetAlertsFrom(p.getString(key,null))
    return try {
        val fresh=widgetAlertsJson(fetchOfficialAlerts(l,LocationSourceStore(c).read(l).alertSource))
        p.edit().putString(key,fresh).putLong(key+"_at",now).apply()
        widgetAlertsFrom(fresh)
    } catch(e:CancellationException) { throw e } catch(e:Exception) { if(now-at<6*3600) widgetAlertsFrom(p.getString(key,null)) else emptyList() }
}

/** One local day, from its midnight to the next one. */
internal fun widgetDay(date:LocalDate,zone:ZoneId):Pair<Instant,Instant> = date.atStartOfDay(zone).toInstant() to date.plusDays(1).atStartOfDay(zone).toInstant()

/** Official warnings as kept by the widgets between two downloads: level, short name, validity and the kinds of the hazard. */
internal fun widgetAlertsJson(alerts:List<UnifiedAlert>):String {
    val a=JSONArray()
    alerts.filter { !it.local && it.level.key in WidgetAlertLevels }.forEach {
        a.put(JSONObject().put("level",it.level.key).put("event",alertName(it)).put("start",it.start.epochSecond).put("end",it.end.epochSecond)
            .put("hazards",it.hazards.joinToString(",") { h -> h.name }))
    }
    return a.toString()
}

/**
 * The stored warnings. Entries of earlier versions have no "hazards" and give the kind by "awareness" or the name;
 * a missing start or end means an open validity.
 */
internal fun widgetAlertsFrom(raw:String?):List<UnifiedAlert> {
    val list=raw?.let { runCatching { JSONArray(it) }.getOrNull() } ?: return emptyList()
    return (0 until list.length()).mapNotNull { i ->
        val o=list.optJSONObject(i) ?: return@mapNotNull null
        val level=AlertLevel.entries.firstOrNull { it.key==o.optString("level") } ?: return@mapNotNull null
        val start=o.optLong("start",0L);val end=o.optLong("end",0L);val event=o.optString("event")
        val hazards=o.optString("hazards").split(',').mapNotNull { n -> AlertHazard.entries.firstOrNull { it.name==n.trim() } }
            .ifEmpty { listOf(alertHazard(event,o.optString("awareness"))) }
        val from=if(start>0L) Instant.ofEpochSecond(start) else Instant.EPOCH
        val to=if(end>0L) Instant.ofEpochSecond(end) else Instant.ofEpochSecond(253402300799L)
        UnifiedAlert(null,emptySet(),"Alert",hazards,Basis.AwarenessCode,level,Basis.AwarenessCode,from,to,to,from,emptySet(),"","",
            listOf(AlertText("",event,"","","","")),emptyList())
    }
}
