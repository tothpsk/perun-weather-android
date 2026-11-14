package sk.perun.weather.ui

import sk.perun.weather.R

import java.time.Instant
import java.util.Locale
import kotlin.math.abs

private fun languageKey(s: String)=s.trim().replace('_','-').lowercase(Locale.ROOT)
private fun languageRank(s: String,preferred: String="sk-SK"): Int {
    val key=languageKey(s); val wanted=languageKey(preferred)
    return when {
        key==wanted -> 0
        key.isNotBlank() && key.substringBefore('-')==wanted.substringBefore('-') -> 1
        key.substringBefore('-')=="en" -> 2
        key.isBlank() || key=="und" -> 4
        else -> 3
    }
}
private data class LanguageBucket(val id: String,val sender: String,val level: String,val start: Instant,val end: Instant,val area: String,val code: String)
internal fun selectAlertLanguages(items: List<AlertItem>,preferred: String="sk-SK"): List<AlertItem> {
    val groups=linkedMapOf<LanguageBucket,List<AlertItem>>(); val unknown=mutableListOf<AlertItem>()
    for (a in items) {
        val start=a.start; val end=a.end
        if (a.local || a.identifier.isBlank() || a.senderId.isBlank() || a.areaKey.isBlank() || start==null || end==null || !end.isAfter(start)) { unknown+=a; continue }
        val key=LanguageBucket(a.identifier,a.senderId,a.level,start,end,a.areaKey,a.eventCode)
        groups[key]=groups[key].orEmpty()+a
    }
    return unknown+groups.values.flatMap { g ->
        val language=languageKey(g.minBy { languageRank(it.language,preferred) }.language)
        g.filter { languageKey(it.language)==language }
    }
}
private fun textKey(s: String)=s.trim().lowercase(Locale.ROOT).replace(Regex("\\s+")," ")
private fun sameRecord(a: AlertItem,b: AlertItem): Boolean {
    if (a.local || b.local) return false
    val start=a.start ?: return false; val end=a.end ?: return false
    if (!end.isAfter(start) || start!=b.start || end!=b.end || a.level!=b.level) return false
    if (a.identifier.isBlank() || a.identifier!=b.identifier || a.senderId.isBlank() || a.senderId!=b.senderId) return false
    if (a.areaKey.isBlank() || a.areaKey!=b.areaKey) return false
    return if (a.eventCode.isNotBlank() || b.eventCode.isNotBlank()) a.eventCode.isNotBlank() && a.eventCode==b.eventCode else textKey(a.event)==textKey(b.event)
}
private fun prefer(a: AlertItem,b: AlertItem): AlertItem {
    val ar=languageRank(a.language); val br=languageRank(b.language)
    if (ar!=br) return if (ar<br) a else b
    return if (a.title.isBlank() && b.title.isNotBlank()) b else a
}
// The same warning republished by another aggregator (MeteoAlarm vs. FOSS Public Alert Server vs. LibreWXR):
// identifiers and area keys differ, but level, wording and validity match.
private fun wording(a: AlertItem)=listOf(a.event,a.title).map { textKey(it) }.filter { it.length>=4 }
private fun looseSame(a: AlertItem,b: AlertItem): Boolean {
    if (a.local || b.local || a.level!=b.level) return false
    // Only across aggregators: two records from one source with different areas stay separate.
    if (a.via.isEmpty() || b.via.isEmpty() || a.via.intersect(b.via).isNotEmpty()) return false
    val s1=a.start ?: return false; val e1=a.end ?: return false; val s2=b.start ?: return false; val e2=b.end ?: return false
    if (abs(s1.epochSecond-s2.epochSecond)>3600 || abs(e1.epochSecond-e2.epochSecond)>3600) return false
    return wording(a).any { x -> wording(b).any { y -> x==y || x in y || y in x } }
}
internal fun mergeAlertRecords(items: List<AlertItem>): List<AlertItem> {
    val out=mutableListOf<AlertItem>()
    for (a in items) {
        val i=out.indexOfFirst { sameRecord(it,a) || looseSame(it,a) }
        if (i<0) out+=a else { val old=out[i]; out[i]=prefer(old,a).copy(via=old.via+a.via) }
    }
    return out.map { a ->if (a.via.size<2) a else a.copy(issuer=a.issuer.substringBefore(uiText(R.string.ui_zdroje_072dfba9))+uiText(R.string.ui_zdroje_2f1b3da1)+a.via.sorted().joinToString(", ")) }
}
