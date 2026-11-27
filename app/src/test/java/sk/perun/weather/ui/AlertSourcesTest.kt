package sk.perun.weather.ui

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Instant

class AlertSourcesTest : ResourceStringsTestBase() {
    private fun bytes(name: String) = javaClass.getResourceAsStream("/cap/$name")!!.use { it.readBytes() }
    private fun message(name: String) = parseCap(bytes(name))!!
    private fun unified(name: String, source: String) = message(name).toUnified(AlertSourceRef(source, name, "")).single()

    @Test fun textAndKindOfTheUnifiedWarning() {
        val a = unified("meteoalarm_sk_icing.xml", "MeteoAlarm")
        assertEquals("Výstraha pred poľadovicou", pickText(a.texts, "sk-SK")!!.event)
        assertEquals("Moderate icing warning", pickText(a.texts, "en-GB")!!.event)
        assertEquals(AlertLevel.Yellow, a.level)
        assertEquals(listOf(AlertHazard.Ice), a.hazards)
        assertEquals("Poľadovica", alertName(a))
        assertEquals(setOf("http://www.shmu.sk|2.49.0.0.703.0.SK.261124163600.6b05bcfd_BA"), alertRefs(a))
    }

    @Test fun iconsOfBothHungarianHazardsStartWithIce() {
        val a = unified("meteoalarm_hu_snow_ice.xml", "MeteoAlarm")
        assertEquals(AlertHazard.Ice, alertIconKind(a))
        val g = groupAlertPresentation(listOf(a)).single()
        assertEquals("Poľadovica · Sneženie", g.label)
        assertEquals(AlertHazard.Ice, g.hazard)
    }

    @Test fun meteoAlarmAndKdeAreOneWarning() {
        val meteo = message("meteoalarm_sk_icing.xml").toUnified(AlertSourceRef("MeteoAlarm", "m", ""))
        val kde = message("kde_sk_icing.xml").toUnified(AlertSourceRef("KDE", "k", ""))
        val one = mergeUnified(meteo + kde).single()
        assertEquals(setOf("MeteoAlarm", "KDE"), one.sources.map { it.source }.toSet())
        assertEquals("Slovenský Hydrometeorologický ústav", one.issuer)
    }

    @Test fun translatedAreasAreFoundByTheirCodes() {
        val m = message("meteoalarm_hu_snow_ice.xml")
        val one = m.toUnified(AlertSourceRef("x", "x", ""), m.areasLike { it.description == "Közép-Dunántúl" }).single()
        assertEquals(2, one.texts.size)
    }

    @Test fun libreReadsItsCapMessage() {
        val m = message("meteoalarm_sk_icing.xml")
        val a = libreFromCap(m, "https://example.org/cap", setOf("Bratislava")).single()
        assertEquals(listOf("LibreWXR"), a.sources.map { it.source })
        assertEquals("Slovenský Hydrometeorologický ústav", a.issuer)
        assertEquals(listOf(AlertHazard.Ice), a.hazards)
        assertEquals(1, libreFromCap(m, "https://example.org/cap", setOf("Elsewhere")).size)
    }

    private fun store(): CapStore = CapStore(Files.createTempDirectory("cap").toFile())

    @Test fun storeKeepsMessagesUntilTheyExpire() {
        val s = store(); val now = Instant.parse("2026-11-25T12:00:00Z")
        s.write("https://a", byteArrayOf(1, 2), now.plusSeconds(600), now)
        assertArrayEquals(byteArrayOf(1, 2), s.read("https://a", now))
        assertArrayEquals(byteArrayOf(1, 2), s.read("https://a", now.plusSeconds(600 + 3000)))
        assertNull(s.read("https://a", now.plusSeconds(600 + 3600)))
        s.write("https://b", byteArrayOf(3), now.minusSeconds(7200), now)
        assertNull(s.read("https://b", now))
    }

    @Test fun budgetCountsOnlyDownloads() {
        val s = store(); val budget = CapBudget(1); var downloads = 0
        // The real message with its expiry moved to the future, so the test does not depend on today.
        val cap = String(bytes("meteoalarm_sk_icing.xml")).replace(Regex("<expires>[^<]*</expires>"), "<expires>2099-01-01T00:00:00+00:00</expires>").toByteArray()
        val load: (String) -> ByteArray = { downloads++; cap }
        assertTrue(fetchCap("https://a", budget, load, s) is CapFetch.Read)
        assertTrue(fetchCap("https://a", budget, load, s) is CapFetch.Read)
        assertEquals(1, downloads)
        assertEquals(CapFetch.OverBudget, fetchCap("https://b", budget, load, s))
        assertTrue(budget.exhausted)
        assertEquals(CapFetch.Failed, fetchCap("https://c", CapBudget(), { error("offline") }, s))
        assertEquals(CapFetch.Failed, fetchCap("https://d", CapBudget(), { "<feed/>".toByteArray() }, s))
    }

    @Test fun parallelKeepsTheOrder() {
        assertEquals((1..20).map { it * 2 }, inParallel((1..20).toList()) { it * 2 })
    }

    @Test fun newInstallationsGetAllSources() {
        assertEquals(AllAlertSources, initialAlertSources(100L, 100L, false))
        assertEquals(LegacyAlertSources, initialAlertSources(100L, 200L, false))
        assertEquals(LegacyAlertSources, initialAlertSources(100L, 100L, true))
        assertEquals(setOf("local", "meteo", "libre", "kde"), alertSourceSet(AllAlertSources))
        assertEquals(alertSourceSet("kde"), alertSourceSet(LegacyAlertSources))
    }

    @Test fun hazardMarkOnlyForKnownKinds() {
        assertEquals(AlertHazard.Fog, hazardOfAwareness(HazardPrefix + "Fog"))
        assertNull(hazardOfAwareness("4; Fog"))
        assertNull(hazardOfAwareness(HazardPrefix + "Nothing"))
        assertEquals(AlertHazard.Fog, alertHazard("x", "4; Fog"))
    }

    @Test fun tempFilesAreNotRead() {
        val dir = Files.createTempDirectory("cap").toFile(); val s = CapStore(dir)
        File(dir, "junk").writeText("x")
        s.purge()
        assertTrue(dir.listFiles()!!.isEmpty())
    }
}
