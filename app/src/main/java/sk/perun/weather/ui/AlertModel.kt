package sk.perun.weather.ui

import java.text.Normalizer
import java.time.Instant
import java.util.Locale

/** Level of a warning by the MeteoAlarm awareness_level: 1 green, 2 yellow, 3 orange, 4 red. */
internal enum class AlertLevel(val rank: Int, val key: String) {
    Neutral(0, "neutral"), Green(1, "green"), Yellow(2, "yellow"), Orange(3, "orange"), Red(4, "red")
}

/** Where the kind or the level of a warning comes from; the earlier entries are the more certain ones. */
internal enum class Basis { AwarenessCode, EventCode, EnglishEvent, OriginalText, Severity, Fallback }

/** Identity of a CAP message. The identifier is normalised: DWD publishes one warning as .DEU, .ENG and .MUL. */
internal data class CapKey(val sender: String, val identifier: String)

/** One language version of a warning (one CAP info block). */
internal data class AlertText(
    val language: String, val event: String, val headline: String,
    val description: String, val instruction: String, val area: String,
)

/** A source in which a warning was found. */
internal data class AlertSourceRef(
    val source: String,
    val sourceId: String,
    val capUri: String,
)

/** One warning, the same for every source. */
internal data class UnifiedAlert(
    val key: CapKey?,
    val supersedes: Set<CapKey>,
    val msgType: String,
    val hazards: List<AlertHazard>,
    val hazardBasis: Basis,
    val level: AlertLevel,
    val levelBasis: Basis,
    val start: Instant,
    val end: Instant,
    val expires: Instant,
    val sent: Instant,
    val areas: Set<String>,
    val issuer: String,
    val web: String,
    val texts: List<AlertText>,
    val sources: List<AlertSourceRef>,
    val local: Boolean = false,
)

private val DwdLanguageSuffix = Regex("\\.[A-Z]{3}$")

/** The key of a CAP message, or null without a sender or an identifier. Only DWD identifiers lose their language suffix. */
internal fun capKey(sender: String?, identifier: String?): CapKey? {
    val s = sender?.trim()?.lowercase(Locale.ROOT).orEmpty()
    val id = identifier?.trim().orEmpty()
    if (s.isEmpty() || id.isEmpty()) return null
    val core = if (".DWD." in id) id.replace(DwdLanguageSuffix, "") else id
    return CapKey(s, core)
}

/** The number in front of the first semicolon of a MeteoAlarm parameter ("2; yellow; Moderate" is 2). */
internal fun leadingNumber(value: String?): Int? = value?.substringBefore(';')?.trim()?.toIntOrNull()

private val ColourWord = Regex("\\b(red|orange|yellow)\\b")

/**
 * The level: the awareness_level number, then a colour word of the English name or headline, then the CAP severity.
 * DWD uses the severity one step lower than the other issuers (Minor is yellow, Moderate orange, Severe red).
 */
internal fun classifyLevel(awarenessLevel: String?, severity: String?, sender: String, texts: List<String>): Pair<AlertLevel, Basis> {
    when (leadingNumber(awarenessLevel)) {
        1 -> return AlertLevel.Green to Basis.AwarenessCode
        2 -> return AlertLevel.Yellow to Basis.AwarenessCode
        3 -> return AlertLevel.Orange to Basis.AwarenessCode
        4 -> return AlertLevel.Red to Basis.AwarenessCode
    }
    ColourWord.find(texts.joinToString(" ").lowercase(Locale.ROOT))?.let {
        val level = when (it.value) { "red" -> AlertLevel.Red; "orange" -> AlertLevel.Orange; else -> AlertLevel.Yellow }
        return level to Basis.EnglishEvent
    }
    val dwd = "dwd.de" in sender.lowercase(Locale.ROOT)
    val level = when (severity?.trim()) {
        "Extreme" -> AlertLevel.Red
        "Severe" -> if (dwd) AlertLevel.Red else AlertLevel.Orange
        "Moderate" -> if (dwd) AlertLevel.Orange else AlertLevel.Yellow
        "Minor" -> AlertLevel.Yellow
        else -> null
    }
    return if (level != null) level to Basis.Severity else AlertLevel.Neutral to Basis.Fallback
}

// DWD eventCode GROUP and met.no eventCode eventType. Codes not listed here fall through to the names.
private val DwdGroups = mapOf(
    "THUNDERSTORM" to AlertHazard.Storm, "WIND" to AlertHazard.Wind, "TORNADO" to AlertHazard.Tornado,
    "RAIN" to AlertHazard.Rain, "HAIL" to AlertHazard.Hail, "SNOWFALL" to AlertHazard.Snow, "SNOWDRIFT" to AlertHazard.Snow,
    "FOG" to AlertHazard.Fog, "FROST" to AlertHazard.Cold, "GLAZE" to AlertHazard.Ice, "SLIPPERINESS" to AlertHazard.Ice,
    "HEAT" to AlertHazard.Heat,
)
private val MetNoEventTypes = mapOf(
    "ice" to AlertHazard.Ice, "snow" to AlertHazard.Snow, "blowingsnow" to AlertHazard.Snow, "rain" to AlertHazard.Rain,
    "rainflood" to AlertHazard.RainFlood, "flood" to AlertHazard.Flood, "forestfire" to AlertHazard.Fire,
    "wind" to AlertHazard.Wind, "gale" to AlertHazard.Wind, "stormsurge" to AlertHazard.Coastal,
    "lightning" to AlertHazard.Storm, "avalanches" to AlertHazard.Avalanche,
)

internal fun codeHazards(codes: List<Pair<String, String>>): List<AlertHazard> = codes.mapNotNull { (name, value) ->
    when (name) {
        "GROUP" -> DwdGroups[value.trim().uppercase(Locale.ROOT)]
        "eventType" -> MetNoEventTypes[value.trim().lowercase(Locale.ROOT)]
        else -> null
    }
}.distinct()

private fun fold(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }.lowercase(Locale.ROOT)

// The shared group names of MeteoAlarm say "snow or ice" and decide neither.
private val SnowIceGroup = Regex("snow[- /]?ice|neige[- ]verglas|neve[- ]ghiaccio", RegexOption.IGNORE_CASE)
// A name lists more hazards with a comma or a conjunction: "Road ice and snow", "onos eso, hofuvas".
private val NameSeparators = Regex("\\s*[,;/&+]\\s*|\\s+(?:and|und|et|es|a|i|oraz)\\s+")
// Words the general list of AlertHazards.kt does not know yet.
private val ExtraNameTerms = listOf(
    "onos" to AlertHazard.Ice, "hofuvas" to AlertHazard.Snow, "havazas" to AlertHazard.Snow, "hovihar" to AlertHazard.Snow,
)

/** The hazards a warning name lists, in the order of the name. The description and the instruction are never used. */
internal fun nameHazards(name: String): List<AlertHazard> {
    val cleaned = fold(SnowIceGroup.replace(name, " "))
    return cleaned.split(NameSeparators).mapNotNull { part ->
        if (part.isBlank()) return@mapNotNull null
        ExtraNameTerms.firstOrNull { (term, _) -> term in part }?.second
            ?: alertHazard(part).takeIf { it != AlertHazard.Generic }
    }.distinct()
}

internal data class HazardInput(
    val awarenessType: String?,
    val eventCodes: List<Pair<String, String>>,
    val englishEvent: String,
    val originalEvent: String,
    val headline: String,
)

/**
 * The hazards of a warning, the main one first: the awareness_type, then the event codes, then the English name,
 * then the original name and the headline. The shared snow-ice type is split into snow and ice by the codes or the names.
 */
internal fun classifyHazards(input: HazardInput): Pair<List<AlertHazard>, Basis> {
    val base = leadingNumber(input.awarenessType)?.let { AwarenessTypes[it] }
    val fromCodes = codeHazards(input.eventCodes)
    if (base != null && base != AlertHazard.Snow) return listOf(base) to Basis.AwarenessCode
    if (base == AlertHazard.Snow) {
        val snowIce = setOf(AlertHazard.Snow, AlertHazard.Ice)
        fromCodes.filter { it in snowIce }.takeIf { it.isNotEmpty() }?.let { return it to Basis.EventCode }
        val english = nameHazards(input.englishEvent).filter { it in snowIce }
        val original = nameHazards(input.originalEvent).filter { it in snowIce }
        return when {
            english.size >= 2 -> english to Basis.EnglishEvent
            original.size >= 2 -> original to Basis.OriginalText
            english.isNotEmpty() -> english to Basis.EnglishEvent
            original.isNotEmpty() -> original to Basis.OriginalText
            else -> listOf(AlertHazard.Snow) to Basis.AwarenessCode
        }
    }
    if (fromCodes.isNotEmpty()) return fromCodes to Basis.EventCode
    nameHazards(input.englishEvent).takeIf { it.isNotEmpty() }?.let { return it to Basis.EnglishEvent }
    nameHazards(input.originalEvent).takeIf { it.isNotEmpty() }?.let { return it to Basis.OriginalText }
    nameHazards(input.headline).takeIf { it.isNotEmpty() }?.let { return it to Basis.OriginalText }
    return listOf(AlertHazard.Generic) to Basis.Fallback
}

private fun languageKey(s: String) = s.trim().replace('_', '-').lowercase(Locale.ROOT)
private fun textRank(language: String, preferred: String): Int {
    val key = languageKey(language); val wanted = languageKey(preferred)
    return when {
        key == wanted -> 0
        key.isNotBlank() && key.substringBefore('-') == wanted.substringBefore('-') -> 1
        key.substringBefore('-') == "en" -> 2
        key.isBlank() || key == "und" -> 4
        else -> 3
    }
}

/** The text to show: the preferred language, the same language without region, English, then the original. */
internal fun pickText(texts: List<AlertText>, preferred: String = "sk-SK"): AlertText? = texts.minByOrNull { textRank(it.language, preferred) }

/** Marks an AlertItem.awareness that already names the kind of the hazard, so every icon uses the unified result. */
internal const val HazardPrefix = "hazard:"
internal fun hazardOfAwareness(awareness: String): AlertHazard? =
    if (awareness.startsWith(HazardPrefix)) AlertHazard.entries.firstOrNull { it.name == awareness.removePrefix(HazardPrefix) } else null

/**
 * The warning in the form the screens, widgets and notifications use until they read UnifiedAlert themselves.
 * The text is the one of [preferred]; [origin] is the service that issued a warning passed on by an aggregator.
 */
internal fun UnifiedAlert.toAlertItem(issuer: String, origin: String = "", preferred: String = AppLocale.locale().toLanguageTag()): AlertItem {
    val text = pickText(texts, preferred)
    val event = text?.event?.takeIf { it.isNotBlank() } ?: text?.headline.orEmpty()
    return AlertItem(
        event = event, level = level.key, local = local, start = start, end = end,
        issuer = issuer, title = text?.headline.orEmpty(),
        identifier = key?.identifier ?: sources.firstOrNull()?.sourceId.orEmpty(), senderId = key?.sender.orEmpty(),
        language = text?.language.orEmpty(), eventCode = "",
        areaKey = if (areas.isEmpty()) "" else "geo:" + areas.sorted().joinToString(";"),
        originUri = web, via = sources.map { it.source }.toSet(),
        awareness = hazards.firstOrNull()?.let { HazardPrefix + it.name }.orEmpty(), origin = origin,
    )
}
