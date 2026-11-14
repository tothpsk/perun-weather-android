package sk.perun.weather.ui

import sk.perun.weather.R

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Locale

// Independent CAP/REST adapter; no Breezy Weather source code is incorporated.
internal fun parseAlertTimestamp(raw: String?): Instant? {
    val s=raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    runCatching { Instant.parse(s) }.getOrNull()?.let { return it }
    runCatching { OffsetDateTime.parse(s).toInstant() }.getOrNull()?.let { return it }
    val n=s.toBigDecimalOrNull() ?: return null
    return runCatching {
        val ms=if (n.abs()<BigDecimal("100000000000")) n.movePointRight(3) else n
        Instant.ofEpochMilli(ms.setScale(0,RoundingMode.DOWN).longValueExact())
    }.getOrNull()
}
internal data class AlertDisplayGroup(val label: String,val items: List<AlertItem>)

// These labels are presentation categories, NEVER proof that two CAP messages are identical.
private fun presentationKind(a: AlertItem): String? {
    val words=a.event.lowercase(Locale.ROOT).split(Regex("[^a-z]+")).toSet()
    val kinds=listOf(
        uiText(R.string.ui_vietor_8a3ebf26) to setOf("wind","winds","gust","gusts","vent","vents","rafales","sturm","sturmb","orkan"),
        uiText(R.string.ui_dazd_8eda180e) to setOf("rain","rainfall","pluie","pluies","regen","dauerregen"),
        uiText(R.string.ui_burky_a079c974) to setOf("thunderstorm","thunderstorms","orage","orages","gewitter"),
        uiText(R.string.ui_sneh_27400f08) to setOf("snow","snowfall","neige","schnee"),
        uiText(R.string.ui_poladovica_459cceb0) to setOf("ice","icing","verglas","glatteis"),
        uiText(R.string.ui_horucava_d2c7a7d1) to setOf("heat","heatwave","canicule","hitze"),
        uiText(R.string.ui_chlad_7284d347) to setOf("cold","frost","gel","froid"),
    ).filter { (_,terms)->words.any { it in terms } }.map { it.first }
    return kinds.singleOrNull()
}
internal fun groupAlertPresentation(items: List<AlertItem>): List<AlertDisplayGroup> {
    val groups=linkedMapOf<String,AlertDisplayGroup>()
    items.forEachIndexed { index,a ->
        val kind=if (a.local) null else presentationKind(a)
        val key=if (kind==null) "separate:$index" else "$kind|${a.level}"
        val old=groups[key]
        groups[key]=AlertDisplayGroup(kind ?: a.event.ifBlank { uiText(R.string.ui_vystraha_a0ff184b) },old?.items.orEmpty()+a)
    }
    return groups.values.toList()
}
