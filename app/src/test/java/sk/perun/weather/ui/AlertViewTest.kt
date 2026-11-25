package sk.perun.weather.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.Locale

class AlertViewTest {
    private val now = Instant.parse("2026-11-25T12:00:00Z")
    private fun h(n: Long) = now.plusSeconds(n * 3600)
    private fun alert(
        hazards: List<AlertHazard> = listOf(AlertHazard.Snow), level: AlertLevel = AlertLevel.Yellow,
        start: Instant = h(1), end: Instant = h(7), local: Boolean = false, basis: Basis = Basis.AwarenessCode,
        id: String = "id", texts: List<AlertText> = listOf(AlertText("sk", "event", "", "", "", "")),
    ) = UnifiedAlert(CapKey("s", id), emptySet(), "Alert", hazards, basis, level, Basis.AwarenessCode, start, end, end, now,
        emptySet(), "SHMU", "", texts, emptyList(), local)

    @Test fun validNowComesFirstThenHigherLevel() {
        val future = alert(level = AlertLevel.Red, start = h(5), id = "a")
        val running = alert(level = AlertLevel.Yellow, start = h(-1), id = "b")
        val orange = alert(level = AlertLevel.Orange, start = h(5), id = "c")
        assertEquals(listOf("b", "a", "c"), listOf(future, orange, running).sortedWith(alertOrder(now)).map { it.key!!.identifier })
    }

    @Test fun oneRowForEachSetOfHazardsOfficialBeforeLocal() {
        val snowYellow = alert(id = "1", start = h(1), end = h(6))
        val snowOrange = alert(id = "2", level = AlertLevel.Orange, start = h(4), end = h(10))
        val iceSnow = alert(id = "3", hazards = listOf(AlertHazard.Ice, AlertHazard.Snow))
        val snowIce = alert(id = "4", hazards = listOf(AlertHazard.Snow, AlertHazard.Ice), start = h(20), end = h(30))
        val local = alert(id = "5", local = true, level = AlertLevel.Red, start = h(-1))
        val rows = alertRows(listOf(local, snowYellow, iceSnow, snowOrange, snowIce), now)
        assertEquals(3, rows.size)
        assertFalse(rows[0].local); assertTrue(rows[2].local)
        val snow = rows.first { it.hazards == listOf(AlertHazard.Snow) && !it.local }
        // Higher levels are drawn last, so they lie on top.
        assertEquals(listOf("1", "2"), snow.alerts.map { it.key!!.identifier })
        assertEquals("2", snow.at(h(5))!!.key!!.identifier)
        assertEquals(listOf("2"), snow.overlapping(snowYellow).map { it.key!!.identifier })
        assertNull(snow.at(h(12)))
        assertEquals(2, rows.first { it.hazards.size == 2 }.alerts.size)
    }

    @Test fun placeholdersAndRepeatsAreHidden() {
        assertFalse(informative("Žiadne ďalšie informácie nie sú dostupné"))
        assertFalse(informative("No detailed description available."))
        assertFalse(informative("  "))
        assertFalse(informative("Sneženie", "sneženie"))
        assertTrue(informative("Očakávame tvorbu poľadovice", "Poľadovica"))
    }

    @Test fun hazardFromANameIsMarkedAsEstimated() {
        assertFalse(alert(basis = Basis.AwarenessCode).hazardEstimated())
        assertFalse(alert(basis = Basis.EventCode).hazardEstimated())
        assertTrue(alert(basis = Basis.EnglishEvent).hazardEstimated())
        assertFalse(alert(basis = Basis.Fallback, local = true).hazardEstimated())
    }

    @Test fun languagesFollowTheAppLanguageForAnyLanguage() {
        val texts = listOf(
            AlertText("de-DE", "Glatteis", "", "", "", ""), AlertText("en-GB", "Ice", "", "", "", ""),
            AlertText("sk", "Poľadovica", "", "", "", ""), AlertText("en-US", "Ice", "", "", "", ""),
            AlertText("hu-HU", "", "Nincs", "", "", ""),
        )
        assertEquals(listOf("sk", "en-GB", "de-DE", "hu-HU"), languageChoices(texts, "sk-SK").map { it.language })
        // An app in German would put German first and English second.
        assertEquals(listOf("de-DE", "en-GB"), languageChoices(texts, "de-AT").take(2).map { it.language })
        assertEquals("Deutsch", languageName("de-DE", Locale.GERMAN))
        assertEquals("German", languageName("de-DE", Locale.UK))
        assertEquals("", languageName("und", Locale.UK))
    }
}
