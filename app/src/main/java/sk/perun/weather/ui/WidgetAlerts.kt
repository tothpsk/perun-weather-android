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
 * (from inclusive, to exclusive); at an equal level the one that starts first. A warning without a start is valid
 * from the beginning, one without an end until the end of the period.
 */
internal fun widgetAlert(alerts:List<AlertItem>,from:Instant,to:Instant):AlertItem? =
    alerts.filter { a -> !a.local && a.level in WidgetAlertLevels && (a.start?.isBefore(to) ?: true) && (a.end?.isAfter(from) ?: true) }
        .minWithOrNull(compareBy<AlertItem>({ WidgetAlertLevels.indexOf(it.level) },{ it.start ?: Instant.MIN }))

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
internal fun widgetPlaceAlerts(c:Context,l:Location):List<AlertItem> {
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

/** Official warnings as kept by the widgets between two downloads: level, event, validity and the type of the hazard. */
internal fun widgetAlertsJson(alerts:List<AlertItem>):String {
    val a=JSONArray()
    alerts.filter { !it.local && it.level in WidgetAlertLevels }.forEach {
        a.put(JSONObject().put("level",it.level).put("event",it.event).put("start",it.start?.epochSecond ?: 0L).put("end",it.end?.epochSecond ?: 0L).put("awareness",it.awareness))
    }
    return a.toString()
}

internal fun widgetAlertsFrom(raw:String?):List<AlertItem> {
    val list=raw?.let { runCatching { JSONArray(it) }.getOrNull() } ?: return emptyList()
    return (0 until list.length()).mapNotNull { i ->
        val o=list.optJSONObject(i) ?: return@mapNotNull null
        val start=o.optLong("start",0L);val end=o.optLong("end",0L)
        AlertItem(event=o.optString("event"),level=o.optString("level"),local=false,
            start=if(start>0L) Instant.ofEpochSecond(start) else null,end=if(end>0L) Instant.ofEpochSecond(end) else null,awareness=o.optString("awareness"))
    }
}
