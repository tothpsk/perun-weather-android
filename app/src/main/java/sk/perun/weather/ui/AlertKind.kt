package sk.perun.weather.ui

import java.text.Normalizer
import java.util.Locale

internal enum class AlertKind { Wind, Storm, Rain, Flood, Snow, Ice, Cold, Heat, Fog, Other }

// One list of words for the icon of an alert and for the grouping of alerts.
// The text is compared without diacritics, so the stems are plain ASCII (tools/check_localization.py allows no accented literal).
// A stem counts only at the start of a word: as a bare substring, "lad" (ice) matched "dohladnost" (visibility)
// and "vent" matched "event". "(?= )" closes a word that must match whole.
// Slovak flood words are limited to forms that do not collide with "povodne" (original) once the diacritics are gone.
private fun stems(vararg s: String) = Regex(" (?:" + s.joinToString("|") + ")")
private val KindStems = listOf(
    AlertKind.Storm to stems("thunder", "burk", "burok", "orage", "gewitter"),
    AlertKind.Wind to stems("wind", "gust", "gale(?= )", "gales(?= )", "vietor", "vetr", "vichr", "vent(?= )", "vents(?= )", "rafale", "sturm", "orkan"),
    AlertKind.Rain to stems("rain", "precip", "dazd", "zrazk", "zrazok", "lejak", "pluie", "regen(?= )", "starkregen", "dauerregen"),
    AlertKind.Flood to stems("flood", "povoden", "povodnov", "zaplav", "inondation", "crue(?= )", "crues(?= )", "hochwasser"),
    AlertKind.Snow to stems("snow", "sneh", "snez", "zavej", "neige", "schnee"),
    AlertKind.Ice to stems("ice(?= )", "icing", "icy(?= )", "poladov", "lad(?= )", "namraz", "verglas", "glatte"),
    AlertKind.Cold to stems("cold", "frost", "low temp", "nizk[^ ]* teplot", "chlad", "mraz", "gel(?= )", "froid", "kalte"),
    AlertKind.Heat to stems("heat", "high temp", "vysok[^ ]* teplot", "horuc", "hitze", "canicule", "chaleur"),
    AlertKind.Fog to stems("fog", "mist(?= )", "hml", "brouillard", "nebel"),
)
private val StormWord = Regex(" storm")
// Lower case, no diacritics, letters only, one space between words and one at each end.
private fun words(text: String): String {
    val plain = Normalizer.normalize(text, Normalizer.Form.NFD).filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }.lowercase(Locale.ROOT)
    return " " + plain.map { if (it.isLetter()) it else ' ' }.joinToString("").split(' ').filter { it.isNotEmpty() }.joinToString(" ") + " "
}

// Every kind named in the text, in the order of the list above.
internal fun alertKinds(text: String): List<AlertKind> { val t = words(text); return KindStems.filter { it.second.containsMatchIn(t) }.map { it.first } }
// Kind for the icon: the first one named. "Storm" alone, when nothing else is named, stays a storm.
internal fun alertKind(text: String): AlertKind = alertKinds(text).firstOrNull() ?: if (StormWord.containsMatchIn(words(text))) AlertKind.Storm else AlertKind.Other
