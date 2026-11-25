package sk.perun.weather.ui

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.security.MessageDigest
import java.time.Instant
import java.time.OffsetDateTime
import javax.xml.parsers.DocumentBuilderFactory

private val CapNamespaces = setOf("urn:oasis:names:tc:emergency:cap:1.2", "urn:oasis:names:tc:emergency:cap:1.1")

internal data class CapArea(
    val description: String,
    val geocodes: List<Pair<String, String>>,
    val polygons: List<String>,
    val circles: List<String>,
)

internal data class CapInfo(
    val language: String, val categories: List<String>, val event: String, val headline: String,
    val description: String, val instruction: String, val severity: String, val senderName: String, val web: String,
    val effective: Instant?, val onset: Instant?, val expires: Instant?,
    val eventCodes: List<Pair<String, String>>, val parameters: List<Pair<String, String>>, val areas: List<CapArea>,
) {
    fun parameter(name: String): String? = parameters.firstOrNull { it.first == name }?.second
}

internal data class CapMessage(
    val identifier: String, val sender: String, val sent: Instant?,
    val status: String, val msgType: String, val scope: String,
    val references: List<CapKey>, val infos: List<CapInfo>,
)

private fun Element.kids(name: String): List<Element> {
    val out = mutableListOf<Element>(); var n: Node? = firstChild
    while (n != null) {
        if (n is Element && n.namespaceURI in CapNamespaces && n.localName == name) out += n
        n = n.nextSibling
    }
    return out
}
private fun Element.kidText(name: String): String = kids(name).firstOrNull()?.textContent?.trim().orEmpty()
private fun Element.pairs(name: String): List<Pair<String, String>> = kids(name).mapNotNull {
    val k = it.kidText("valueName"); val v = it.kidText("value")
    if (k.isEmpty()) null else k to v
}
internal fun capTime(value: String?): Instant? = value?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }

/** "sender,identifier,sent" triples separated by white space. */
private fun references(value: String): List<CapKey> = value.trim().split(Regex("\\s+")).mapNotNull { triple ->
    val parts = triple.split(",")
    if (parts.size < 2) null else capKey(parts[0], parts[1])
}

/** A CAP 1.1 or 1.2 message, or null when the bytes are not one. Document type declarations are refused. */
internal fun parseCap(bytes: ByteArray): CapMessage? {
    val factory = DocumentBuilderFactory.newInstance()
    factory.isNamespaceAware = true
    factory.isExpandEntityReferences = false
    runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
    val root = runCatching { factory.newDocumentBuilder().parse(bytes.inputStream()).documentElement }.getOrNull() ?: return null
    if (root.namespaceURI !in CapNamespaces || root.localName != "alert") return null
    val infos = root.kids("info").map { info ->
        CapInfo(
            language = info.kidText("language"),
            categories = info.kids("category").map { it.textContent.trim() },
            event = info.kidText("event"), headline = info.kidText("headline"),
            description = info.kidText("description"), instruction = info.kidText("instruction"),
            severity = info.kidText("severity"), senderName = info.kidText("senderName"), web = info.kidText("web"),
            effective = capTime(info.kidText("effective")), onset = capTime(info.kidText("onset")), expires = capTime(info.kidText("expires")),
            eventCodes = info.pairs("eventCode"), parameters = info.pairs("parameter"),
            areas = info.kids("area").map { a ->
                CapArea(a.kidText("areaDesc"), a.pairs("geocode"), a.kids("polygon").map { it.textContent.trim() }, a.kids("circle").map { it.textContent.trim() })
            },
        )
    }
    return CapMessage(
        identifier = root.kidText("identifier"), sender = root.kidText("sender"), sent = capTime(root.kidText("sent")),
        status = root.kidText("status"), msgType = root.kidText("msgType"), scope = root.kidText("scope"),
        references = references(root.kidText("references")), infos = infos,
    )
}

private fun shapeHash(text: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(text.replace(Regex("\\s+"), " ").toByteArray(Charsets.UTF_8))
    return digest.take(8).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
}

/** Keys of an area: its geocodes ("EMMA_ID=SK100"), otherwise a hash of its outline. Never the translated areaDesc. */
internal fun capAreaKeys(area: CapArea): Set<String> {
    val geo = area.geocodes.filter { it.second.isNotBlank() }.map { "${it.first}=${it.second}" }
    if (geo.isNotEmpty()) return geo.toSet()
    return (area.polygons.map { "polygon:" + shapeHash(it) } + area.circles.map { "circle:" + shapeHash(it) }).toSet()
}

private val ActiveTypes = setOf("Alert", "Update", "Cancel")

/**
 * The meteorological warnings of a message for one place. Language versions with the same validity and the same
 * areas form one warning; the areas are compared by their keys because their names are translated.
 * [areaMatches] picks the areas of the place; a warning without such an area is left out, and so is a green one.
 */
internal fun CapMessage.toUnified(source: AlertSourceRef, areaMatches: (CapArea) -> Boolean = { true }): List<UnifiedAlert> {
    if (status != "Actual" || scope != "Public" || msgType !in ActiveTypes) return emptyList()
    val key = capKey(sender, identifier) ?: return emptyList()
    class Version(val info: CapInfo, val areas: List<CapArea>, val start: Instant, val expires: Instant)
    val groups = linkedMapOf<String, MutableList<Version>>()
    for (info in infos) {
        if (info.categories.none { it == "Met" }) continue
        val matched = info.areas.filter(areaMatches)
        if (matched.isEmpty()) continue
        val start = info.onset ?: info.effective ?: sent ?: continue
        val expires = info.expires ?: continue
        val areaKeys = matched.flatMap { capAreaKeys(it) }.sorted()
        val group = "$start|$expires|" + areaKeys.ifEmpty { matched.map { "desc:" + it.description } }.joinToString(";")
        groups.getOrPut(group) { mutableListOf() } += Version(info, matched, start, expires)
    }
    return groups.values.mapNotNull { versions ->
        val english = versions.firstOrNull { it.info.language.lowercase().startsWith("en") }?.info
        val original = (versions.firstOrNull { !it.info.language.lowercase().startsWith("en") } ?: versions.first()).info
        val first = versions.first()
        fun any(name: String) = versions.firstNotNullOfOrNull { it.info.parameter(name)?.takeIf { v -> v.isNotBlank() } }
        val (level, levelBasis) = classifyLevel(any("awareness_level"), first.info.severity, sender, listOfNotNull(english?.event, english?.headline))
        if (level == AlertLevel.Green) return@mapNotNull null
        val (hazards, hazardBasis) = classifyHazards(HazardInput(
            awarenessType = any("awareness_type"),
            eventCodes = versions.flatMap { it.info.eventCodes }.distinct(),
            englishEvent = english?.event.orEmpty(),
            originalEvent = original.event,
            headline = english?.headline?.takeIf { it.isNotBlank() } ?: original.headline,
        ))
        // NWS: expires is the validity of the message, eventEndingTime the expected end of the event.
        val eventEnd = capTime(any("eventEndingTime"))
        UnifiedAlert(
            key = key, supersedes = references.toSet(), msgType = msgType,
            hazards = hazards, hazardBasis = hazardBasis, level = level, levelBasis = levelBasis,
            start = first.start, end = if (eventEnd != null && eventEnd.isAfter(first.start)) eventEnd else first.expires,
            expires = first.expires, sent = sent ?: first.start,
            areas = first.areas.flatMap { capAreaKeys(it) }.toSet(),
            issuer = original.senderName.ifBlank { english?.senderName.orEmpty() },
            web = versions.firstNotNullOfOrNull { it.info.web.takeIf { w -> w.isNotBlank() } }.orEmpty(),
            texts = versions.map { v ->
                AlertText(v.info.language, v.info.event, v.info.headline, v.info.description, v.info.instruction, v.areas.joinToString(", ") { it.description })
            },
            sources = listOf(source),
        )
    }
}
