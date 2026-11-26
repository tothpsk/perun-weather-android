package sk.perun.weather.ui

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.OffsetDateTime

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
/** Warnings shown together on the Now chip and the mark of a place: the same kinds and level; local estimates stay apart. */
internal data class AlertDisplayGroup(val label: String,val hazard: AlertHazard,val level: String,val local: Boolean,val items: List<UnifiedAlert>)

/** Groups in the order of [items]; the label is the short general name, a local estimate keeps the name of its rule. */
internal fun groupAlertPresentation(items: List<UnifiedAlert>): List<AlertDisplayGroup> =
    items.groupBy { Triple(it.local,it.hazards.toSet(),it.level) }.values.map { g ->
        val first=g.first()
        AlertDisplayGroup(if (first.local) alertText(first)?.event?.takeIf { it.isNotBlank() } ?: alertName(first) else alertName(first),alertIconKind(first),first.level.key,first.local,g)
    }
