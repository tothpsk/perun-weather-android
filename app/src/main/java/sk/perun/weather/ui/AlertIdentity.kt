package sk.perun.weather.ui

import kotlin.math.abs

/**
 * Areas of two warnings may be the same: an area code they share, or no common kind of code to compare
 * (MeteoAlarm gives DWD warnings as EMMA_ID, DWD itself as WARNCELLID). An unknown area is compatible with any.
 */
internal fun areasCompatible(a: Set<String>, b: Set<String>): Boolean {
    if (a.isEmpty() || b.isEmpty()) return true
    fun kinds(s: Set<String>) = s.map { it.substringBefore('=') }.toSet()
    val common = kinds(a) intersect kinds(b)
    if (common.isEmpty()) return true
    return a.any { it in b && it.substringBefore('=') in common }
}

private fun sourceNames(a: UnifiedAlert) = a.sources.map { it.source }.toSet()

// Fallback for a warning without its CAP message: the same main hazard, level and area, start and end within an hour,
// and only between different sources.
private fun looseSame(a: UnifiedAlert, b: UnifiedAlert): Boolean =
    a.hazards.firstOrNull() == b.hazards.firstOrNull() && a.level == b.level && areasCompatible(a.areas, b.areas) &&
        abs(a.start.epochSecond - b.start.epochSecond) <= 3600 && abs(a.end.epochSecond - b.end.epochSecond) <= 3600

// One issuer sends one warning as different messages: DWD a district for MeteoAlarm and parts of it in its own feed,
// Italy one message for each zone. All warnings here are those of one place, so the same issuer, hazard, level,
// start and end make one warning whatever the areas; warnings that differ in any of them stay apart.
private fun sameWarningOfOneIssuer(a: UnifiedAlert, b: UnifiedAlert, ka: CapKey, kb: CapKey): Boolean =
    ka.sender == kb.sender && a.start == b.start && a.end == b.end && a.level == b.level &&
        a.hazards.firstOrNull() == b.hazards.firstOrNull()

// [keys] are all the CAP keys already joined into [a].
private fun sameAlert(a: UnifiedAlert, keys: Set<CapKey>, b: UnifiedAlert): Boolean {
    val ka = a.key; val kb = b.key
    if (ka != null && kb != null) return (kb in keys && areasCompatible(a.areas, b.areas)) || sameWarningOfOneIssuer(a, b, ka, kb)
    // A feed repeats one entry: the same source, the same id and the same validity.
    if (ka == null && kb == null && sourceNames(a) == sourceNames(b))
        return a.sources.map { it.sourceId }.toSet() == b.sources.map { it.sourceId }.toSet() && a.start == b.start && a.end == b.end && a.level == b.level
    if ((sourceNames(a) intersect sourceNames(b)).isNotEmpty()) return false
    return looseSame(a, b)
}

private fun combine(a: UnifiedAlert, b: UnifiedAlert): UnifiedAlert {
    // The warning read from its CAP message leads; the other one adds its sources and languages.
    val (base, other) = if (a.key == null && b.key != null) b to a else a to b
    val hazards = if (other.hazardBasis < base.hazardBasis) other.hazards to other.hazardBasis else base.hazards to base.hazardBasis
    val level = if (other.levelBasis < base.levelBasis) other.level to other.levelBasis else base.level to base.levelBasis
    return base.copy(
        supersedes = base.supersedes + other.supersedes,
        hazards = hazards.first, hazardBasis = hazards.second, level = level.first, levelBasis = level.second,
        areas = base.areas + other.areas,
        issuer = base.issuer.ifBlank { other.issuer }, web = base.web.ifBlank { other.web },
        texts = base.texts + other.texts.filter { t -> base.texts.none { it.language.equals(t.language, ignoreCase = true) } },
        sources = (base.sources + other.sources).distinctBy { it.source to it.sourceId },
        looseMatch = base.looseMatch || other.looseMatch || (a.key == null) != (b.key == null),
    )
}

/**
 * One warning for every alert of every source:
 * 1. a message named in the references of another one (Update, Cancel) is left out, and so is every Cancel;
 * 2. messages with the same CAP key and compatible areas are one warning, and so are messages of one issuer with
 *    the same hazard, level, start and end;
 * 3. a warning without its CAP message joins a warning of another source by hazard, level, area and time.
 * Warnings that only overlap in time are never joined.
 */
internal fun mergeUnified(all: List<UnifiedAlert>): List<UnifiedAlert> {
    val replaced = all.flatMap { it.supersedes }.toSet()
    val cancelled = all.filter { it.msgType == "Cancel" }.mapNotNull { it.key }.toSet()
    val live = all.filter { a -> a.msgType != "Cancel" && a.key.let { it == null || (it !in replaced && it !in cancelled) } }
    val out = mutableListOf<UnifiedAlert>(); val keys = mutableListOf<MutableSet<CapKey>>()
    for (a in live.filter { it.key != null } + live.filter { it.key == null }) {
        val i = out.indices.firstOrNull { sameAlert(out[it], keys[it], a) }
        if (i == null) { out += a; keys += listOfNotNull(a.key).toMutableSet() }
        else { out[i] = combine(out[i], a); a.key?.let { keys[i] += it } }
    }
    return out.mapIndexed { i, a -> a.copy(joined = keys[i].toSet()) }
}
