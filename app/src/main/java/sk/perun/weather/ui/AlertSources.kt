package sk.perun.weather.ui

import sk.perun.weather.R

import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.util.Locale

internal data class SourceReport(val name: String,val items: List<AlertItem>,val error: String?,val note: String?=null,val incomplete: Boolean=false)
internal fun meteoReport(l: Location): SourceReport = try {
    val stats=IntArray(1); val region=alertRegion(l); val items=meteoAlarm(l.countryCode,region,stats)
    val note=if (items.isEmpty() && stats[0]>0) uiQuantityText(R.plurals.unmatched_alert_count, stats[0], stats[0], region ?: "–") else null
    SourceReport("MeteoAlarm",items,null,note)
} catch (e: CancellationException) { throw e } catch (e: Exception) { SourceReport("MeteoAlarm",emptyList(),e.message ?: uiText(R.string.ui_chyba_b9f17311)) }
internal fun kdeReport(l: Location): SourceReport = try {
    val r=kdeAlerts(l.latitude,l.longitude); SourceReport("KDE",r.items,null,null,r.incomplete)
} catch (e: CancellationException) { throw e } catch (e: Exception) { SourceReport("KDE",emptyList(),e.message ?: uiText(R.string.ui_chyba_b9f17311)) }
private fun JSONObject.str(vararg keys: String): String? {
    for (k in keys) {
        if (!has(k) || isNull(k)) continue
        val v=opt(k)
        if (v is String && v.isNotBlank()) return v.trim()
        if (v is Number) return v.toString()
    }
    return null
}
// REST /v2/alerts exports time=CAP effective and expires as Unix seconds.
// A point query filters using full-resolution geometry on the server.
// It avoids re-filtering a simplified polygon near a boundary on the phone.
internal fun libreReport(l: Location): SourceReport = try {
    require(l.latitude.isFinite() && l.longitude.isFinite() && l.latitude in -90.0..90.0 && l.longitude in -180.0..180.0)
    val where=String.format(Locale.US,"lat=%.6f&lon=%.6f&simplify=0",l.latitude,l.longitude)
    val root=JSONObject(String(fetchBytes("https://api.librewxr.net/v2/alerts?$where"),Charsets.UTF_8))
    require(root.optString("type")=="FeatureCollection") { uiText(R.string.ui_neocakavany_format_odpovede_7aab4c98) }
    val features=root.getJSONArray("features"); val items=mutableListOf<AlertItem>(); var incomplete=false
    for (i in 0 until features.length()) {
        val f=features.optJSONObject(i); val p=f?.optJSONObject("properties")
        if (f==null || p==null) { incomplete=true; continue }
        val event=p.str("title")
        if (event==null) { incomplete=true; continue }
        val regions=p.optJSONArray("regions")?.let { a ->(0 until a.length()).mapNotNull { k ->a.optString(k).takeIf { it.isNotBlank() } } }.orEmpty()
        val title=event+(if (regions.isNotEmpty()) " ("+regions.joinToString(", ")+")" else "")
        val rawSeverity=p.str("severity")?.lowercase(Locale.ROOT)?.replaceFirstChar { it.uppercase() }
        val id=p.str("cap_identifier","capIdentifier","identifier") ?: (f.str("id") ?: p.str("id"))?.let { "libre:$it" }.orEmpty()
        items+=AlertItem(
            event=event,level=alertLevelFrom(rawSeverity,p.str("awareness_level","awarenessLevel"),title,event),local=false,
            start=parseAlertTimestamp(p.str("time")),end=parseAlertTimestamp(p.str("expires")),
            issuer="LibreWXR",title=title,identifier=id,
            senderId=p.str("cap_sender","capSender","sender").orEmpty(),
            language=p.str("language","lang").orEmpty(),eventCode=p.str("event_code","eventCode").orEmpty(),
            areaKey=p.str("area_id","areaId","areaCode")?.let { "area:$it" } ?: f.optJSONObject("geometry")?.let { "geojson:$it" }.orEmpty(),
            originUri=p.str("uri").orEmpty(),via=setOf("LibreWXR"),
        )
    }
    SourceReport("LibreWXR",mergeAlertRecords(selectAlertLanguages(items)),null,uiText(R.string.ui_casy_z_time_expires_povodny_jazyk_librewxr_sa_automaticky_0e440490),incomplete)
} catch (e: CancellationException) { throw e } catch (e: Exception) { SourceReport("LibreWXR",emptyList(),e.message ?: uiText(R.string.ui_chyba_b9f17311)) }
internal fun mergeAlerts(items: List<AlertItem>): List<AlertItem> = mergeAlertRecords(items)
