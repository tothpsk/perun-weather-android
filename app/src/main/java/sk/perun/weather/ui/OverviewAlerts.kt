package sk.perun.weather.ui

import org.json.JSONArray
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.net.URLEncoder
import java.time.Instant
import java.time.OffsetDateTime
import java.util.concurrent.atomic.AtomicInteger
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
    // MeteoAlarm awareness_type of the CAP message ("1; Wind"), or HazardPrefix + the kind when the unified model knows it.
    val awareness: String = "",
    // The national service that issued a warning passed on by MeteoAlarm (senderName of its CAP message), when the message could be read.
    val origin: String = "",
)
internal data class OfficialAlerts(val alerts: List<UnifiedAlert>, val incomplete: Boolean) {
    val items: List<AlertItem> by lazy { officialItems(alerts) }
}
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
// Position of the area of a feed entry in its CAP message: the entry id ends with ?index_info=1&index_area=61.
private fun entryIndex(id: String, name: String): Int? = Regex("[?&]$name=([0-9]+)").find(id)?.groupValues?.get(1)?.toIntOrNull()
private fun capLink(e: Element): String? {
    val links = e.getElementsByTagNameNS(ATOM_NS, "link")
    for (i in 0 until links.length) {
        val link = links.item(i) as? Element ?: continue
        if (link.getAttribute("type") == "application/cap+xml") return link.getAttribute("href").takeIf { it.startsWith("https://") }?.substringBefore('?')
    }
    return null
}
private class MeteoEntry(val capUrl: String?, val infoIndex: Int?, val areaIndex: Int?, val emma: String?, val area: String?, val fallback: UnifiedAlert?)

// A place gets the entries of the warning areas it lies in. Names decide only when the position cannot:
// no outlines for the country, a point outside all outlines, or an entry whose code the outlines do not know.
// Each warning is then read from its CAP message; an entry whose message cannot be read keeps the data of the feed.
internal fun meteoAlarm(l: Location, stats: IntArray? = null, budget: CapBudget = CapBudget()): OfficialAlerts {
    val country = l.countryCode
    val slug = FEEDS[country.uppercase()] ?: return OfficialAlerts(emptyList(), false)
    val placeCodes = MeteoAlarmAreas.codes(country, l.latitude, l.longitude)
    val placeNames = alertAreaNames(l)
    val doc = parseXml(fetchBytes("https://feeds.meteoalarm.org/feeds/meteoalarm-legacy-atom-$slug"))
    val entries = doc.getElementsByTagNameNS(ATOM_NS, "entry"); val matched = mutableListOf<MeteoEntry>()
    for (i in 0 until entries.length) {
        val e = entries.item(i) as? Element ?: continue
        val title = e.text(ATOM_NS, "title") ?: continue
        if ("no alert" in title.lowercase()) continue
        if (stats != null) stats[0]++
        val text = (title + " " + (e.text(ATOM_NS, "summary") ?: "")).lowercase()
        if (Regex("\\bgreen\\b").containsMatchIn(text)) continue
        val level = when { "red" in text -> "red"; "orange" in text -> "orange"; "yellow" in text -> "yellow"; else -> "neutral" }
        val region = REGION_FROM_TITLE.find(title)?.groupValues?.get(1)?.trim()
        val area = e.text(CAP_NS, "areaDesc")?.takeIf { it.isNotBlank() } ?: region
        val code = entryEmmaId(e)
        val matches = if (placeCodes.isNotEmpty() && code != null && MeteoAlarmAreas.knows(country, code)) code in placeCodes
            else area != null && areaCore(area).let { it.isNotEmpty() && it in placeNames }
        if (!matches) continue
        val atomId = e.text(ATOM_NS, "id").orEmpty()
        val id = e.text(CAP_NS, "identifier")?.takeIf { it.isNotBlank() } ?: atomId.takeIf { it.isNotBlank() }?.let { "atom:$it" }.orEmpty()
        val event = e.text(CAP_NS, "event")?.takeIf { it.isNotBlank() } ?: title
        val (kinds, kindBasis) = classifyHazards(HazardInput(null, emptyList(), event, "", title))
        val start = capInstant(e.text(CAP_NS, "onset") ?: e.text(CAP_NS, "effective")) ?: capInstant(e.text(CAP_NS, "sent")) ?: Instant.now()
        val fallback = capInstant(e.text(CAP_NS, "expires"))?.let { end ->
            UnifiedAlert(
                key = null, supersedes = emptySet(), msgType = e.text(CAP_NS, "message_type")?.takeIf { it.isNotBlank() } ?: "Alert",
                hazards = kinds, hazardBasis = kindBasis,
                level = when (level) { "red" -> AlertLevel.Red; "orange" -> AlertLevel.Orange; "yellow" -> AlertLevel.Yellow; else -> AlertLevel.Neutral },
                levelBasis = if (level == "neutral") Basis.Fallback else Basis.EnglishEvent,
                start = start, end = end, expires = end, sent = start, areas = emptySet(), issuer = "", web = "",
                texts = listOf(AlertText((e.text(CAP_NS, "language") ?: e.getAttributeNS("http://www.w3.org/XML/1998/namespace", "lang")).ifBlank { "en" }, event, title, "", "", area.orEmpty())),
                sources = listOf(AlertSourceRef("MeteoAlarm", id, capLink(e).orEmpty())),
            )
        }
        matched += MeteoEntry(capLink(e), entryIndex(atomId, "index_info"), entryIndex(atomId, "index_area"), code, area, fallback)
    }
    val byMessage = matched.filter { it.capUrl != null }.groupBy { it.capUrl!! }
    var incomplete = false
    val fromMessages = inParallel(byMessage.keys.toList()) { url -> url to fetchCap(url, budget) }.flatMap { (url, read) ->
        val group = byMessage.getValue(url)
        val message = (read as? CapFetch.Read)?.message
        if (read == CapFetch.OverBudget) incomplete = true
        val alerts = message?.let { m ->
            // The area of the entry by its position in the message, otherwise by its EMMA_ID or its name.
            val exact = group.mapNotNull { g -> g.infoIndex?.let { ii -> g.areaIndex?.let { ai -> m.infos.getOrNull(ii)?.areas?.getOrNull(ai) } } }.toSet()
            val codes = group.mapNotNull { it.emma }.toSet(); val names = group.mapNotNull { it.area }.toSet()
            val pick: (CapArea) -> Boolean = if (exact.size == group.size) { a -> a in exact } else { a ->
                a.geocodes.any { it.first == "EMMA_ID" && it.second in codes } || a.description in names
            }
            m.toUnified(AlertSourceRef("MeteoAlarm", url, url), m.areasLike(pick))
        }
        // A message that cannot be read, or whose areas are not found, keeps the warnings of the feed.
        val keepFeed = message == null || (message.msgType != "Cancel" && message.infos.none { leadingNumber(it.parameter("awareness_level")) == 1 })
        if (alerts.isNullOrEmpty() && keepFeed) group.mapNotNull { it.fallback } else alerts.orEmpty()
    }
    val withoutMessage = matched.filter { it.capUrl == null }.mapNotNull { it.fallback }
    return OfficialAlerts(fromMessages + withoutMessage, incomplete)
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
    val fetched = AtomicInteger(); val failed = AtomicInteger(); val overBudget = AtomicInteger(); val inactive = AtomicInteger(); val accepted = AtomicInteger()
    fun summary(ids: Int, alerts: Int): String =
        "KDE summary: ids=$ids read=${fetched.get()} failed=${failed.get()} overBudget=${overBudget.get()} inactive=${inactive.get()} accepted=${accepted.get()} alerts=$alerts"
}
// Only the info blocks in force now: effective (or sent) has passed and expires has not.
private fun CapMessage.inForce(now: Instant): CapMessage = copy(infos = infos.filter { i ->
    val effective = i.effective ?: sent; val expires = i.expires
    effective != null && expires != null && !effective.isAfter(now) && now.isBefore(expires)
})
internal fun kdeAlerts(lat: Double, lon: Double, budget: CapBudget = CapBudget()): OfficialAlerts {
    if (lat !in -90.0..90.0 || lon !in -180.0..180.0) { kdeDiagnostic("KDE request skipped: invalid coordinates"); return OfficialAlerts(emptyList(),false) }
    val m=KDE_POINT_MARGIN
    val url=KDE_AREA_URL+"?min_lat=${max(-90.0,lat-m)}&max_lat=${min(90.0,lat+m)}&min_lon=${max(-180.0,lon-m)}&max_lon=${min(180.0,lon+m)}"
    val listBytes=try { fetchBytes(url) } catch (e: Exception) { kdeDiagnostic("KDE list fetch failed: "+kdeFailure(e)); throw e }
    val ids=try { JSONArray(String(listBytes,Charsets.UTF_8)) } catch (e: Exception) { kdeDiagnostic("KDE list parse failed: "+kdeFailure(e)); throw e }
    kdeDiagnostic("KDE list received: entries=${ids.length()}")
    val diagnostics=KdeDiagnostics(); val now=Instant.now()
    val uuids=(0 until ids.length()).map { ids.opt(it) as? String }
    var incomplete=uuids.any { it==null }
    val inPlace: (CapArea)->Boolean = { a -> a.polygons.any { pointInPolygon(lat,lon,it) } || a.circles.any { pointInCircle(lat,lon,it) } }
    val result=inParallel(uuids.filterNotNull()) { uuid ->
        val detail="https://alerts.kde.org/alert/"+URLEncoder.encode(uuid,"UTF-8")
        when (val read=fetchCap(detail,budget)) {
            is CapFetch.Read -> {
                diagnostics.fetched.incrementAndGet()
                val message=read.message.inForce(now)
                if (message.infos.isEmpty()) diagnostics.inactive.incrementAndGet()
                message.toUnified(AlertSourceRef("KDE",uuid,detail),message.areasLike(inPlace)).also { diagnostics.accepted.addAndGet(it.size) }
            }
            CapFetch.OverBudget -> { diagnostics.overBudget.incrementAndGet(); null }
            CapFetch.Failed -> { diagnostics.failed.incrementAndGet(); null }
        }
    }
    if (result.any { it==null }) incomplete=true
    val alerts=result.filterNotNull().flatten()
    kdeDiagnostic(diagnostics.summary(ids.length(),alerts.size))
    return OfficialAlerts(alerts,incomplete)
}
private fun libreOfficialAlerts(location: Location,budget: CapBudget): OfficialAlerts {
    val report=libreReport(location,budget)
    if (report.error!=null) throw IllegalStateException(report.error)
    val now=Instant.now()
    return OfficialAlerts(report.alerts.filter { it.end.isAfter(now) },report.incomplete)
}
private fun extraOfficialAlerts(location: Location,source: String,budget: CapBudget): OfficialAlerts =
    if (source=="libre") libreOfficialAlerts(location,budget) else kdeAlerts(location.latitude,location.longitude,budget)
// Same sources as the Alerts screen: MeteoAlarm (where available) plus the selected extra source.
internal fun fetchOfficialAlertsStrict(location: Location,source: String=AlertSourceDefaults.value): OfficialAlerts {
    val set=alertSourceSet(source); val budget=CapBudget()
    val meteo=if ("meteo" in set && hasMeteoAlarm(location.countryCode)) meteoAlarm(location,budget=budget) else OfficialAlerts(emptyList(),false)
    val extras=listOf("libre","kde").filter { it in set }.map { extraOfficialAlerts(location,it,budget) }
    return OfficialAlerts(meteo.alerts+extras.flatMap { it.alerts },meteo.incomplete || extras.any { it.incomplete })
}
internal fun fetchOfficialAlerts(location: Location,source: String=AlertSourceDefaults.value): List<AlertItem> {
    val now=Instant.now()
    val set=alertSourceSet(source); val budget=CapBudget()
    val meteo=try { if ("meteo" in set && hasMeteoAlarm(location.countryCode)) meteoAlarm(location,budget=budget).alerts else emptyList() } catch (e: Exception) { emptyList() }
    val extra=listOf("libre","kde").filter { it in set }.flatMap { id -> try { extraOfficialAlerts(location,id,budget).alerts } catch (e: Exception) { emptyList() } }
    // One warning for all sources, as on the Alerts screen.
    return officialItems(meteo+extra).filter { it.end?.isAfter(now)!=false }
}
