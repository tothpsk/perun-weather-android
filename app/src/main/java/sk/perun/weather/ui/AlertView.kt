package sk.perun.weather.ui

import java.text.Normalizer
import java.time.Instant
import java.util.Locale

/** The warning is valid at [t]. */
internal fun UnifiedAlert.activeAt(t: Instant) = !t.isBefore(start) && t.isBefore(end)

/** Order of the warnings on the Alerts screen: valid now, higher level, earlier start, then the kind and the end. */
internal fun alertOrder(now: Instant): Comparator<UnifiedAlert> =
    compareByDescending<UnifiedAlert> { it.activeAt(now) }.thenByDescending { it.level.rank }.thenBy { it.start }
        .thenBy { it.hazards.firstOrNull()?.ordinal ?: Int.MAX_VALUE }.thenBy { it.end }

/** One row of the chart: the warnings with the same set of hazards, from the lowest level, so higher levels are drawn on top. */
internal class AlertRow(val hazards: List<AlertHazard>, val local: Boolean, val alerts: List<UnifiedAlert>) {
    val top: UnifiedAlert get() = alerts.maxWith(compareBy<UnifiedAlert> { it.level.rank }.thenByDescending { it.start })
    /** The warning shown at [t]: the highest level of those valid then. */
    fun at(t: Instant): UnifiedAlert? = alerts.filter { it.activeAt(t) }.maxByOrNull { it.level.rank }
    /** The other warnings of the row whose validity overlaps [a]. */
    fun overlapping(a: UnifiedAlert): List<UnifiedAlert> = alerts.filter { it != a && it.start.isBefore(a.end) && a.start.isBefore(it.end) }
}

/** Rows of the chart: official warnings first, local estimates never share a row with them. */
internal fun alertRows(alerts: List<UnifiedAlert>, now: Instant): List<AlertRow> {
    val order = alertOrder(now)
    return alerts.groupBy { it.local to it.hazards.toSet() }.values.map { group ->
        val first = group.minWith(order)
        AlertRow(first.hazards, first.local, group.sortedWith(compareBy<UnifiedAlert> { it.level.rank }.thenBy { it.start }))
    }.sortedWith(compareBy<AlertRow> { it.local }.then { a, b -> order.compare(a.alerts.minWith(order), b.alerts.minWith(order)) })
}

private fun fold(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
        .lowercase(Locale.ROOT).trim().trimEnd('.', '!').trim()

// Headlines some issuers send instead of a text; they say nothing and are not shown.
private val PlaceholderTexts = setOf(
    "ziadne dalsie informacie nie su dostupne", "no detailed description available", "no further information available",
    "no further information", "keine weiteren informationen verfugbar",
)

/** The text says something that is not a placeholder and not already one of [shown]. */
internal fun informative(text: String, vararg shown: String): Boolean {
    val t = fold(text)
    return t.isNotEmpty() && t !in PlaceholderTexts && shown.none { fold(it) == t }
}

/** The hazard was read from a name, not from a code of the issuer. */
internal fun UnifiedAlert.hazardEstimated(): Boolean = !local && hazardBasis !in setOf(Basis.AwarenessCode, Basis.EventCode)

/**
 * The language versions to choose from, one for each language, in the order of [preferred]: the app language,
 * English, then the others. Works for any app language; a language without a name in the CAP message keeps its tag.
 */
internal fun languageChoices(texts: List<AlertText>, preferred: String): List<AlertText> =
    texts.filter { informative(it.event) || informative(it.description) || informative(it.headline) }
        .distinctBy { Locale.forLanguageTag(it.language.trim().replace('_', '-')).language.ifEmpty { it.language.trim().lowercase(Locale.ROOT) } }
        .sortedBy { textRank(it.language, preferred) }

/** The name of the language of a text in the language of the app, or an empty string when the tag has none. */
internal fun languageName(tag: String, ui: Locale): String {
    val locale = Locale.forLanguageTag(tag.trim().replace('_', '-'))
    if (locale.language.isEmpty() || locale.language == "und") return ""
    return locale.getDisplayLanguage(ui).replaceFirstChar { it.titlecase(ui) }
}
