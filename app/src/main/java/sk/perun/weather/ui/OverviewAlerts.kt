package sk.perun.weather.ui

import org.json.JSONArray
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.net.URLEncoder
import java.time.Instant
import java.time.OffsetDateTime
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

internal data class AlertItem(
    val event: String, val level: String, val local: Boolean,
    val start: Instant? = null, val end: Instant? = null,
    val issuer: String = "", val title: String = "",
    val identifier: String = "", val senderId: String = "",
    val language: String = "", val eventCode: String = "",
    val areaKey: String = "", val originUri: String = "",
    val via: Set<String> = emptySet(),
    // MeteoAlarm awareness_type of the CAP message ("1; Wind"), when the issuer sends it.
    val awareness: String = "",
    // The national service that issued a warning passed on by MeteoAlarm (senderName of its CAP message), when the message could be read.
    val origin: String = "",
)
internal data class OfficialAlerts(val items: List<AlertItem>, val incomplete: Boolean)
private const val ATOM_NS = "http://www.w3.org/2005/Atom"
private const val CAP_NS = "urn:oasis:names:tc:emergency:cap:1.2"
private const val KDE_AREA_URL = "https://alerts.kde.org/alert/area"
private const val KDE_POINT_MARGIN = 0.005
private val FEEDS = mapOf(
    "AD" to "andorra", "AT" to "austria", "BE" to "belgium", "BA" to "bosnia-herzegovina", "BG" to "bulgaria",
    "HR" to "croatia", "CY" to "cyprus", "CZ" to "czechia", "DK" to "denmark", "EE" to "estonia",
    "FI" to "finland", "FR" to "france", "DE" to "germany", "GR" to "greece", "HU" to "hungary",
    "IS" to "iceland", "IE" to "ireland", "IL" to "israel", "IT" to "italy", "LV" to "latvia",
    "LT" to "lithuania", "LU" to "luxembourg", "MT" to "malta", "MD" to "moldova", "ME" to "montenegro",
    "NL" to "netherlands", "MK" to "republic-of-north-macedonia", "NO" to "norway", "PL" to "poland",
    "PT" to "portugal", "RO" to "romania", "RS" to "serbia", "SK" to "slovakia", "SI" to "slovenia",
    "ES" to "spain", "SE" to "sweden", "CH" to "switzerland", "UA" to "ukraine", "GB" to "united-kingdom",
)
internal fun hasMeteoAlarm(countryCode: String) = countryCode.uppercase() in FEEDS
// Region used to pick the MeteoAlarm entries of a place. A place saved without a region falls back to its district or name, so that it never gets the alerts of the whole country.
internal fun alertRegion(l: Location): String? = listOf(l.admin, l.admin3, l.region, l.name).firstOrNull { it.isNotBlank() }
private val REGION_FROM_TITLE = Regex("issued for [^-]+-\\s*(.+)$", RegexOption.IGNORE_CASE)
private fun parseXml(bytes: ByteArray): Document {
    val f = DocumentBuilderFactory.newInstance(); f.isNamespaceAware = true
    runCatching { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
    return f.newDocumentBuilder().parse(bytes.inputStream())
}
private fun Element.text(ns: String, name: String): String? = getElementsByTagNameNS(ns, name).item(0)?.textContent?.trim()
private fun Element.children(ns: String, name: String): List<Element> {
    val out = mutableListOf<Element>(); var n: Node? = firstChild
    while (n != null) {
        if (n is Element && n.namespaceURI == ns && n.localName == name) out += n
        n = n.nextSibling
    }
    return out
}
private fun Element.childText(name: String) = children(CAP_NS, name).firstOrNull()?.textContent?.trim()?.takeIf { it.isNotEmpty() }
private fun Element.capParameter(name: String): String? = children(CAP_NS, "parameter").firstOrNull { it.childText("valueName") == name }?.childText("value")
private fun cleanRegion(v: String) = v.replace(Regex("^\\s*okres\\s+", RegexOption.IGNORE_CASE), "").replace(Regex("\\s+[IVX]+$"), "").trim().lowercase()
private fun Element.childNamed(name: String): String? {
    var n: Node? = firstChild
    while (n != null) {
        if (n is Element && (n.localName ?: n.nodeName.substringAfter(':')) == name) return n.textContent?.trim()
        n = n.nextSibling
    }
    return null
}
private val EMMA_IN_LINK = Regex("EMMA_ID(?::|%3A)([A-Za-z0-9_]+)", RegexOption.IGNORE_CASE)
// EMMA_ID of a feed entry: from cap:geocode, or from the entry link when the geocode carries another code (France uses NUTS3).
private fun entryEmmaId(e: Element): String? {
    val geocodes = e.getElementsByTagNameNS(CAP_NS, "geocode")
    for (i in 0 until geocodes.length) {
        val g = geocodes.item(i) as? Element ?: continue
        val value = g.childNamed("value")
        if (g.childNamed("valueName") == "EMMA_ID" && !value.isNullOrEmpty()) return value
    }
    val links = e.getElementsByTagNameNS(ATOM_NS, "link")
    for (i in 0 until links.length) {
        val href = (links.item(i) as? Element)?.getAttribute("href") ?: continue
        EMMA_IN_LINK.find(href)?.let { return it.groupValues[1] }
    }
    return null
}
private fun capInstant(v: String?): Instant? = v?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }
private fun codes(info: Element): String = info.children(CAP_NS, "eventCode").mapNotNull {
    val k = it.childText("valueName"); val v = it.childText("value")
    if (k != null && v != null) "$k=$v" else null
}.sorted().joinToString(";")
// Identity uses geocodes/geometry, never the translated areaDesc.
private fun areaIdentity(areas: List<Element>): String = areas.map { a ->
    val geo = a.children(CAP_NS, "geocode").mapNotNull { g ->
        val k = g.childText("valueName"); val v = g.childText("value")
        if (k != null && v != null) "$k=$v" else null
    }.sorted()
    if (geo.isNotEmpty()) "geo:" + geo.joinToString(";") else {
        val shapes = listOf("polygon", "circle").flatMap { tag -> a.children(CAP_NS, tag).map { tag + ":" + it.textContent.trim().replace(Regex("\\s+"), " ") } }
        shapes.sorted().joinToString(";")
    }
}.filter { it.isNotBlank() }.sorted().joinToString("|")

private class MeteoOrigin(val sender: String, val web: String, val awareness: String)
// The CAP message behind a feed entry names the national service that issued the warning. One message is read for a warning
// and at most eight for a place; a message that cannot be read leaves the warning without its origin.
private fun meteoOrigin(e: Element, seen: HashMap<String, MeteoOrigin?>): MeteoOrigin? {
    val links = e.getElementsByTagNameNS(ATOM_NS, "link"); var url: String? = null
    for (i in 0 until links.length) {
        val link = links.item(i) as? Element ?: continue
        if (link.getAttribute("type") == "application/cap+xml") { url = link.getAttribute("href"); break }
    }
    val href = url?.takeIf { it.startsWith("https://") } ?: return null
    val key = href.substringBefore('?')
    if (seen.containsKey(key)) return seen[key]
    if (seen.size >= 8) return null
    val origin = try {
        val infos = parseXml(fetchBytes(href)).documentElement.children(CAP_NS, "info")
        val index = href.substringAfter("index_info=", "").takeWhile { it.isDigit() }.toIntOrNull()
        val info = index?.let { infos.getOrNull(it) } ?: infos.firstOrNull()
        if (info == null) null else MeteoOrigin(info.childText("senderName").orEmpty(), info.childText("web").orEmpty(), info.capParameter("awareness_type").orEmpty())
    } catch (x: Exception) { null }
    seen[key] = origin
    return origin
}

// A place gets the entries of the warning areas it lies in. Names decide only when the position cannot:
// no outlines for the country, a point outside all outlines, or an entry whose code the outlines do not know.
internal fun meteoAlarm(l: Location, stats: IntArray? = null): List<AlertItem> {
    val country = l.countryCode
    val slug = FEEDS[country.uppercase()] ?: return emptyList()
    val placeCodes = MeteoAlarmAreas.codes(country, l.latitude, l.longitude)
    val placeNames = alertAreaNames(l)
    val doc = parseXml(fetchBytes("https://feeds.meteoalarm.org/feeds/meteoalarm-legacy-atom-$slug"))
    val entries = doc.getElementsByTagNameNS(ATOM_NS, "entry"); val result = mutableListOf<AlertItem>(); val origins = HashMap<String, MeteoOrigin?>()
    for (i in 0 until entries.length) {
        val e = entries.item(i) as? Element ?: continue
        val title = e.text(ATOM_NS, "title") ?: continue
        if ("no alert" in title.lowercase()) continue
        if (stats != null) stats[0]++
        val text = (title + " " + (e.text(ATOM_NS, "summary") ?: "")).lowercase()
        val level = when { "red" in text -> "red"; "orange" in text -> "orange"; "yellow" in text -> "yellow"; else -> "neutral" }
        val region = REGION_FROM_TITLE.find(title)?.groupValues?.get(1)?.trim()
        val area = e.text(CAP_NS, "areaDesc")?.takeIf { it.isNotBlank() } ?: region
        val code = entryEmmaId(e)
        val matches = if (placeCodes.isNotEmpty() && code != null && MeteoAlarmAreas.knows(country, code)) code in placeCodes
            else area != null && areaCore(area).let { it.isNotEmpty() && it in placeNames }
        if (!matches) continue
        val id = e.text(CAP_NS, "identifier")?.takeIf { it.isNotBlank() } ?: e.text(ATOM_NS, "id")?.let { "atom:$it" }.orEmpty()
        val origin = meteoOrigin(e, origins)
        result += AlertItem(
            event = e.text(CAP_NS, "event")?.takeIf { it.isNotBlank() } ?: title, level = level, local = false,
            start = capInstant(e.text(CAP_NS, "onset") ?: e.text(CAP_NS, "effective")), end = capInstant(e.text(CAP_NS, "expires")),
            issuer = "MeteoAlarm", title = title, identifier = id,
            senderId = e.text(CAP_NS, "sender")?.takeIf { it.isNotBlank() } ?: "meteoalarm:$slug",
            language = e.text(CAP_NS, "language") ?: e.getAttributeNS("http://www.w3.org/XML/1998/namespace", "lang"),
            areaKey = region?.let { "region:" + cleanRegion(it) }.orEmpty(), via = setOf("MeteoAlarm"), originUri = origin?.web.orEmpty(), awareness = origin?.awareness.orEmpty(), origin = origin?.sender.orEmpty(),
        )
    }
    return mergeAlertRecords(selectAlertLanguages(result))
}
private fun pointInPolygon(lat: Double, lon: Double, text: String): Boolean {
    val p = try { text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.map { val a = it.split(",", limit = 2); a[1].toDouble() to a[0].toDouble() } } catch (e: Exception) { return false }
    if (p.size < 3) return false
    var inside = false
    for (i in p.indices) {
        val (x1,y1) = p[(i-1+p.size)%p.size]; val (x2,y2) = p[i]
        val cross = (lon-x1)*(y2-y1)-(lat-y1)*(x2-x1)
        if (abs(cross)<1e-10 && lon>=min(x1,x2)-1e-10 && lon<=max(x1,x2)+1e-10 && lat>=min(y1,y2)-1e-10 && lat<=max(y1,y2)+1e-10) return true
        if ((y1>lat)!=(y2>lat) && lon<x1+(lat-y1)*(x2-x1)/(y2-y1)) inside=!inside
    }
    return inside
}
private fun pointInCircle(lat: Double, lon: Double, text: String): Boolean = try {
    val p=text.trim().split(Regex("\\s+")); val c=p[0].split(","); val r=p[1].toDouble()
    if (p.size!=2 || r<0) false else {
        val a=Math.toRadians(lat); val b=Math.toRadians(c[0].toDouble()); val dl=Math.toRadians(c[1].toDouble()-lon); val dt=b-a
        val h=sin(dt/2)*sin(dt/2)+cos(a)*cos(b)*sin(dl/2)*sin(dl/2)
        6371.0088*2*Math.atan2(sqrt(h),sqrt(max(0.0,1-h)))<=r
    }
} catch (e: Exception) { false }
// Colour words (MeteoAlarm awareness levels, headlines) win over CAP severity, whose meaning differs between issuers;
// the fallback follows the MeteoAlarm convention: Extreme = red, Severe = orange, Moderate = yellow.
internal fun alertLevelFrom(severity: String?, vararg texts: String?): String {
    val joined = texts.filterNotNull().joinToString(" ").lowercase()
    Regex("\\b(red|orange|yellow)\\b").find(joined)?.let { return it.value }
    return when (severity) { "Extreme" -> "red"; "Severe" -> "orange"; "Moderate", "Minor" -> "yellow"; else -> "neutral" }
}

// Technical diagnostics only: never log coordinates, URLs, CAP text or identifiers.
private fun kdeDiagnostic(message: String) {
    runCatching { android.util.Log.d("PerunAlerts",message) }
}
private fun kdeFailure(e: Exception): String = e.message?.takeIf { Regex("HTTP [0-9]{3}").matches(it) } ?: e.javaClass.simpleName
private class KdeDiagnostics {
    var invalidIds=0; var fetched=0; var parsed=0; var failedDetails=0
    var rootRejected=0; var metadataRejected=0; var infos=0; var categoryRejected=0
    var invalidTime=0; var inactive=0; var geometryMissing=0; var geometryNoMatch=0; var accepted=0
    fun summary(ids: Int, selected: Int, merged: Int): String =
        "KDE summary: ids=$ids invalidIds=$invalidIds fetched=$fetched parsed=$parsed failedDetails=$failedDetails " +
        "rootRejected=$rootRejected metadataRejected=$metadataRejected infos=$infos categoryRejected=$categoryRejected " +
        "invalidTime=$invalidTime inactive=$inactive geometryMissing=$geometryMissing geometryNoMatch=$geometryNoMatch " +
        "acceptedInfo=$accepted selected=$selected merged=$merged"
}
private fun capAlerts(bytes: ByteArray, lat: Double, lon: Double, diagnostics: KdeDiagnostics): List<AlertItem> {
    val root=parseXml(bytes).documentElement
    diagnostics.parsed++
    if (root.namespaceURI!=CAP_NS || root.localName!="alert") { diagnostics.rootRejected++; return emptyList() }
    if (root.childText("status")!="Actual" || root.childText("scope")!="Public" || root.childText("msgType") !in setOf("Alert","Update")) { diagnostics.metadataRejected++; return emptyList() }
    val now=Instant.now(); val id=root.childText("identifier").orEmpty(); val sender=root.childText("sender").orEmpty()
    val out=mutableListOf<AlertItem>()
    for (info in root.children(CAP_NS,"info")) {
        diagnostics.infos++
        if (info.children(CAP_NS,"category").none { it.textContent.trim()=="Met" }) { diagnostics.categoryRejected++; continue }
        val start=capInstant(info.childText("onset") ?: info.childText("effective") ?: root.childText("sent"))
        val expires=capInstant(info.childText("expires")); val effective=capInstant(info.childText("effective") ?: root.childText("sent"))
        if (start==null || effective==null || expires==null) { diagnostics.invalidTime++; continue }
        if (effective.isAfter(now) || !now.isBefore(expires)) { diagnostics.inactive++; continue }
        // NWS: expires = validity of the message, eventEndingTime = expected end of the event (often later for watches).
        val eventEnd=capInstant(info.capParameter("eventEndingTime"))
        val end=if (eventEnd!=null && eventEnd.isAfter(start)) eventEnd else expires
        val matching=info.children(CAP_NS,"area").filter { a -> a.children(CAP_NS,"polygon").any { pointInPolygon(lat,lon,it.textContent) } || a.children(CAP_NS,"circle").any { pointInCircle(lat,lon,it.textContent) } }
        if (matching.isEmpty()) {
            val hasGeometry=info.children(CAP_NS,"area").any { it.children(CAP_NS,"polygon").isNotEmpty() || it.children(CAP_NS,"circle").isNotEmpty() }
            if (hasGeometry) diagnostics.geometryNoMatch++ else diagnostics.geometryMissing++
            continue
        }
        diagnostics.accepted++
        val event=info.childText("event") ?: "Weather warning"
        out += AlertItem(
            event=event, level=alertLevelFrom(info.childText("severity"),info.capParameter("awareness_level"),info.childText("headline"),event), local=false, start=start, end=end,
            issuer=info.childText("senderName") ?: "KDE CAP", title=info.childText("headline") ?: event,
            identifier=id, senderId=sender, language=info.childText("language").orEmpty(), eventCode=codes(info),
            areaKey=areaIdentity(matching), originUri=info.childText("web").orEmpty(), via=setOf("KDE"),
            awareness=info.capParameter("awareness_type").orEmpty(),
        )
    }
    return selectAlertLanguages(out)
}
internal fun kdeAlerts(lat: Double, lon: Double): OfficialAlerts {
    if (lat !in -90.0..90.0 || lon !in -180.0..180.0) { kdeDiagnostic("KDE request skipped: invalid coordinates"); return OfficialAlerts(emptyList(),false) }
    val m=KDE_POINT_MARGIN
    val url=KDE_AREA_URL+"?min_lat=${max(-90.0,lat-m)}&max_lat=${min(90.0,lat+m)}&min_lon=${max(-180.0,lon-m)}&max_lon=${min(180.0,lon+m)}"
    val listBytes=try { fetchBytes(url) } catch (e: Exception) { kdeDiagnostic("KDE list fetch failed: "+kdeFailure(e)); throw e }
    val ids=try { JSONArray(String(listBytes,Charsets.UTF_8)) } catch (e: Exception) { kdeDiagnostic("KDE list parse failed: "+kdeFailure(e)); throw e }
    kdeDiagnostic("KDE list received: entries=${ids.length()}")
    val diagnostics=KdeDiagnostics(); val result=mutableListOf<AlertItem>(); var incomplete=false
    // Alert details are downloaded four at a time; parsing stays serialised.
    val pool=java.util.concurrent.Executors.newFixedThreadPool(4)
    try {
        val jobs=(0 until ids.length()).map { i -> pool.submit(java.util.concurrent.Callable<List<AlertItem>?> {
            val uuid=ids.opt(i) as? String
            if (uuid==null) { synchronized(diagnostics) { diagnostics.invalidIds++ }; emptyList<AlertItem>() } else {
                var stage="fetch"
                try {
                    val bytes=fetchBytes("https://alerts.kde.org/alert/"+URLEncoder.encode(uuid,"UTF-8"))
                    stage="parse"
                    synchronized(diagnostics) { diagnostics.fetched++; capAlerts(bytes,lat,lon,diagnostics) }
                } catch (e: Exception) {
                    synchronized(diagnostics) { diagnostics.failedDetails++ }
                    kdeDiagnostic("KDE detail index=$i stage=$stage failed: "+kdeFailure(e))
                    null
                }
            }
        }) }
        for (job in jobs) { val items=job.get(); if (items==null) incomplete=true else result+=items }
    } finally { pool.shutdown() }
    val merged=try { mergeAlertRecords(result) } catch (e: Exception) { kdeDiagnostic("KDE merge failed: "+kdeFailure(e)); throw e }
    kdeDiagnostic(diagnostics.summary(ids.length(),result.size,merged.size))
    return OfficialAlerts(merged,incomplete)
}
private fun libreOfficialAlerts(location: Location): OfficialAlerts {
    val report=libreReport(location)
    if (report.error!=null) throw IllegalStateException(report.error)
    val now=Instant.now()
    return OfficialAlerts(report.items.filter { it.end?.isAfter(now)!=false },report.incomplete)
}
private fun extraOfficialAlerts(location: Location,source: String): OfficialAlerts = if (source=="libre") libreOfficialAlerts(location) else kdeAlerts(location.latitude,location.longitude)
// Same sources as the Alerts screen: MeteoAlarm (where available) plus the selected extra source.
internal fun fetchOfficialAlertsStrict(location: Location,source: String="kde"): OfficialAlerts {
    val set=alertSourceSet(source)
    val meteo=if ("meteo" in set && hasMeteoAlarm(location.countryCode)) meteoAlarm(location) else emptyList()
    val extras=listOf("libre","kde").filter { it in set }.map { extraOfficialAlerts(location,it) }
    return OfficialAlerts(meteo+extras.flatMap { it.items },extras.any { it.incomplete })
}
internal fun fetchOfficialAlerts(location: Location,source: String="kde"): List<AlertItem> {
    val now=Instant.now()
    val set=alertSourceSet(source)
    val meteo=try { if ("meteo" in set && hasMeteoAlarm(location.countryCode)) meteoAlarm(location) else emptyList() } catch (e: Exception) { emptyList() }
    val extra=listOf("libre","kde").filter { it in set }.flatMap { id -> try { extraOfficialAlerts(location,id).items } catch (e: Exception) { emptyList() } }
    return (meteo+extra).filter { it.end?.isAfter(now)!=false }
}
