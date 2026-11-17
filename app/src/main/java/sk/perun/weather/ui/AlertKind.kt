package sk.perun.weather.ui

import java.util.Locale

internal enum class AlertKind { Wind, Storm, Rain, Flood, Snow, Ice, Cold, Heat, Fog, Other }

// One list of words for the icon of an alert and for the grouping of alerts.
// A stem counts only at the start of a word: as a bare substring, "ľad" (ice) matched "dohľadnosť" (visibility)
// and "vent" matched "event". "(?= )" closes a word that must match whole.
private fun stems(vararg s: String) = Regex(" (?:" + s.joinToString("|") + ")")
private val KindStems = listOf(
    AlertKind.Storm to stems("thunder", "búrk", "búrok", "orage", "gewitter"),
    AlertKind.Wind to stems("wind", "gust", "gale(?= )", "gales(?= )", "vietor", "vetr", "víchr", "vent(?= )", "vents(?= )", "rafale", "sturm", "orkan", "orkán"),
    AlertKind.Rain to stems("rain", "precip", "dážď", "dažď", "zrážk", "zrážok", "lejak", "pluie", "regen(?= )", "starkregen", "dauerregen"),
    AlertKind.Flood to stems("flood", "povod", "záplav", "inondation", "crue(?= )", "crues(?= )", "hochwasser"),
    AlertKind.Snow to stems("snow", "sneh", "snež", "závej", "neige", "schnee"),
    AlertKind.Ice to stems("ice(?= )", "icing", "icy(?= )", "poľadov", "ľad(?= )", "námraz", "verglas", "glatteis", "glätte"),
    AlertKind.Cold to stems("cold", "frost", "low temp", "nízk[^ ]* teplot", "chlad", "mráz", "mraz", "gel(?= )", "froid", "kälte"),
    AlertKind.Heat to stems("heat", "high temp", "vysok[^ ]* teplot", "horúč", "hitze", "canicule", "chaleur"),
    AlertKind.Fog to stems("fog", "mist(?= )", "hml", "brouillard", "nebel"),
)
private val StormWord = Regex(" storm")
// Lower case, letters only, one space between words and one at each end.
private fun words(text: String) = " " + text.lowercase(Locale.ROOT).map { if (it.isLetter()) it else ' ' }.joinToString("").split(' ').filter { it.isNotEmpty() }.joinToString(" ") + " "

// Every kind named in the text, in the order of the list above.
internal fun alertKinds(text: String): List<AlertKind> { val t = words(text); return KindStems.filter { it.second.containsMatchIn(t) }.map { it.first } }
// Kind for the icon: the first one named. "Storm" alone, when nothing else is named, stays a storm.
internal fun alertKind(text: String): AlertKind = alertKinds(text).firstOrNull() ?: if (StormWord.containsMatchIn(words(text))) AlertKind.Storm else AlertKind.Other
