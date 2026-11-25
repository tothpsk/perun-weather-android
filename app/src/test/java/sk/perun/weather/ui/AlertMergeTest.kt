package sk.perun.weather.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AlertMergeTest {
    private fun bytes(name: String) = javaClass.getResourceAsStream("/cap/$name")!!.use { it.readBytes() }
    private fun cap(name: String, source: String) = parseCap(bytes(name))!!.toUnified(AlertSourceRef(source, "$source:$name", ""))
    private val t0 = Instant.parse("2026-11-26T00:00:00Z")
    private fun alert(
        key: CapKey? = CapKey("s", "id-1"), source: String = "MeteoAlarm", hazard: AlertHazard = AlertHazard.Snow,
        level: AlertLevel = AlertLevel.Yellow, start: Instant = t0, end: Instant = t0.plusSeconds(6 * 3600),
        areas: Set<String> = setOf("EMMA_ID=SK100"), msgType: String = "Alert", supersedes: Set<CapKey> = emptySet(),
        language: String = "sk", basis: Basis = Basis.AwarenessCode, sourceId: String = "x",
    ) = UnifiedAlert(key, supersedes, msgType, listOf(hazard), basis, level, Basis.AwarenessCode, start, end, end, start, areas, "Issuer", "",
        listOf(AlertText(language, "event $language", "", "", "", "")), listOf(AlertSourceRef(source, sourceId, "")))

    @Test fun threeSourcesOfOneMessageAreOne() {
        val all = cap("meteoalarm_sk_icing.xml", "MeteoAlarm") + cap("kde_sk_icing.xml", "KDE") +
            alert(key = capKey("http://www.shmu.sk", "2.49.0.0.703.0.SK.261124163600.6b05bcfd_BA"), source = "LibreWXR", hazard = AlertHazard.Ice)
        val one = mergeUnified(all).single()
        assertEquals(setOf("MeteoAlarm", "KDE", "LibreWXR"), one.sources.map { it.source }.toSet())
        assertEquals(listOf("sk", "en-GB"), one.texts.map { it.language })
        assertFalse(one.looseMatch)
    }

    @Test fun dwdInThreeFilesAndTwoCodeKindsIsOne() {
        // LibreWXR gives DEU and ENG with WARNCELLID, KDE the MUL file with WARNCELLID.
        val all = cap("dwd_deu.xml", "LibreWXR") + cap("dwd_eng.xml", "LibreWXR") + cap("kde_dwd_mul.xml", "KDE")
        val one = mergeUnified(all).single()
        assertEquals(8, one.texts.size)
        assertTrue(areasCompatible(setOf("EMMA_ID=DE300"), setOf("WARNCELLID=711000101")))
        assertFalse(areasCompatible(setOf("EMMA_ID=DE300"), setOf("EMMA_ID=DE301")))
    }

    @Test fun anUpdateReplacesTheOlderVersion() {
        val old = alert(key = CapKey("s", "id-U3"), source = "MeteoAlarm")
        val new = alert(key = CapKey("s", "id-U4"), source = "KDE", level = AlertLevel.Orange, supersedes = setOf(CapKey("s", "id-U3")))
        assertEquals(listOf(CapKey("s", "id-U4")), mergeUnified(listOf(old, new)).map { it.key })
        val real = cap("meteoalarm_sk_snow_orange_update.xml", "MeteoAlarm").single()
        val u3 = alert(key = capKey("http://www.shmu.sk", "2.49.0.0.703.0.SK.261124090100.6b055258_BA_U3"))
        assertEquals(listOf(real.key), mergeUnified(listOf(u3, real)).map { it.key })
    }

    @Test fun aCancelRemovesItsWarningAndItself() {
        val warning = alert(key = CapKey("s", "id-1"))
        val cancel = alert(key = CapKey("s", "id-2"), msgType = "Cancel", supersedes = setOf(CapKey("s", "id-1")))
        assertTrue(mergeUnified(listOf(warning, cancel)).isEmpty())
    }

    @Test fun differentMessagesStaySeparateEvenWhenTheyOverlap() {
        val a = alert(key = CapKey("s", "id-1")); val b = alert(key = CapKey("s", "id-2"), start = t0.plusSeconds(1800))
        assertEquals(2, mergeUnified(listOf(a, b)).size)
        assertEquals(2, mergeUnified(listOf(a, alert(key = CapKey("s", "id-3"), end = t0.plusSeconds(9 * 3600)))).size)
    }

    @Test fun oneIssuerInTwoMessagesForTwoAggregators() {
        val meteo = alert(key = CapKey("opendata@dwd.de", "A"), source = "MeteoAlarm", areas = setOf("EMMA_ID=DE300"))
        val kde = alert(key = CapKey("opendata@dwd.de", "B"), source = "KDE", areas = setOf("WARNCELLID=711000101"))
        assertEquals(1, mergeUnified(listOf(meteo, kde)).size)
        assertEquals(2, mergeUnified(listOf(meteo, kde.copy(end = kde.end.plusSeconds(3600)))).size)
        assertEquals(2, mergeUnified(listOf(meteo, kde.copy(key = CapKey("other@x", "B")))).size)
        // Italy: one message for each zone of the place, the same warning in both.
        assertEquals(1, mergeUnified(listOf(meteo, kde.copy(sources = meteo.sources, areas = setOf("EMMA_ID=DE301")))).size)
        assertEquals(2, mergeUnified(listOf(meteo, kde.copy(level = AlertLevel.Orange))).size)
    }

    @Test fun laterMessagesFindEveryJoinedKey() {
        val meteo = alert(key = CapKey("opendata@dwd.de", "A"), source = "MeteoAlarm")
        val kde = alert(key = CapKey("opendata@dwd.de", "B"), source = "KDE")
        val libreDeu = alert(key = CapKey("opendata@dwd.de", "B"), source = "LibreWXR", language = "de-DE", start = t0.plusSeconds(60))
        val one = mergeUnified(listOf(meteo, kde, libreDeu)).single()
        assertEquals(setOf("MeteoAlarm", "KDE", "LibreWXR"), one.sources.map { it.source }.toSet())
    }

    @Test fun aWarningWithoutItsMessageJoinsByHazardLevelAndTime() {
        val keyed = alert(source = "KDE")
        val feed = alert(key = null, source = "MeteoAlarm", areas = emptySet(), start = t0.plusSeconds(1200), language = "en", basis = Basis.EnglishEvent)
        val one = mergeUnified(listOf(feed, keyed)).single()
        assertEquals(keyed.key, one.key)
        assertTrue(one.looseMatch)
        assertEquals(listOf("sk", "en"), one.texts.map { it.language })
        assertEquals(2, mergeUnified(listOf(keyed, feed.copy(level = AlertLevel.Orange))).size)
        assertEquals(2, mergeUnified(listOf(keyed, feed.copy(start = t0.plusSeconds(7200)))).size)
        assertEquals(2, mergeUnified(listOf(keyed, feed.copy(hazards = listOf(AlertHazard.Ice)))).size)
    }

    @Test fun oneSourceNeverJoinsItselfLoosely() {
        val a = alert(key = null, source = "LibreWXR", areas = emptySet(), sourceId = "a")
        val b = alert(key = null, source = "LibreWXR", areas = emptySet(), sourceId = "b")
        assertEquals(2, mergeUnified(listOf(a, b)).size)
        assertEquals(1, mergeUnified(listOf(a, a.copy())).size)
    }

    @Test fun theMoreCertainKindWins() {
        val feed = alert(key = null, source = "MeteoAlarm", areas = emptySet(), hazard = AlertHazard.Snow, basis = Basis.EnglishEvent)
        val keyed = alert(source = "KDE", hazard = AlertHazard.Snow, basis = Basis.EventCode)
        assertEquals(Basis.EventCode, mergeUnified(listOf(feed, keyed)).single().hazardBasis)
    }

    @Test fun issuerLineListsTheSources() {
        val a = alert(source = "KDE"); val b = alert(source = "MeteoAlarm"); val c = alert(source = "LibreWXR")
        assertEquals("MeteoAlarm, LibreWXR, FOSS", officialItems(listOf(a, b, c), "sk-SK").single().issuer)
    }
}
