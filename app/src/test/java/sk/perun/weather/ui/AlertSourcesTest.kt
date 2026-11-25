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
    private fun item(name: String, source: String, preferred: String = "sk-SK") =
        message(name).toUnified(AlertSourceRef(source, name, "")).single().toAlertItem(issuer = source, preferred = preferred)

    @Test fun adapterKeepsTheUnifiedKind() {
        val a = item("meteoalarm_sk_icing.xml", "MeteoAlarm")
        assertEquals("Výstraha pred poľadovicou", a.event)
        assertEquals("yellow", a.level)
        assertEquals("sk", a.language)
        assertEquals(HazardPrefix + "Ice", a.awareness)
        assertEquals(AlertHazard.Ice, alertHazard(a.event, a.awareness, a.title))
        assertEquals("geo:EMMA_ID=SK100", a.areaKey)
        assertEquals("2.49.0.0.703.0.SK.261124163600.6b05bcfd_BA", a.identifier)
        assertEquals("http://www.shmu.sk", a.senderId)
        assertEquals("Moderate icing warning", item("meteoalarm_sk_icing.xml", "MeteoAlarm", "en-GB").event)
    }

    @Test fun iconsOfBothHungarianHazardsStartWithIce() {
        val a = item("meteoalarm_hu_snow_ice.xml", "MeteoAlarm", "en-GB")
        assertEquals(AlertHazard.Ice, alertHazard(a.event, a.awareness))
        assertEquals("Poľadovica", groupAlertPresentation(listOf(a)).single().label)
    }

    @Test fun meteoAlarmAndKdeNowMatchByRecord() {
        val meteo = item("meteoalarm_sk_icing.xml", "MeteoAlarm")
        val kde = item("kde_sk_icing.xml", "KDE")
        assertEquals(meteo.areaKey, kde.areaKey)
        val merged = mergeAlertRecords(listOf(meteo, kde)).single()
        assertEquals(setOf("MeteoAlarm", "KDE"), merged.via)
    }

    @Test fun translatedAreasAreFoundByTheirCodes() {
        val m = message("meteoalarm_hu_snow_ice.xml")
        val one = m.toUnified(AlertSourceRef("x", "x", ""), m.areasLike { it.description == "Közép-Dunántúl" }).single()
        assertEquals(2, one.texts.size)
    }

    @Test fun libreReadsItsCapMessage() {
        val m = message("meteoalarm_sk_icing.xml")
        val a = libreFromCap(m, "https://example.org/cap", setOf("Bratislava")).single()
        assertEquals("LibreWXR", a.issuer)
        assertEquals("Slovenský Hydrometeorologický ústav", a.origin)
        assertEquals(setOf("LibreWXR"), a.via)
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
        val cap = bytes("meteoalarm_sk_icing.xml")
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
