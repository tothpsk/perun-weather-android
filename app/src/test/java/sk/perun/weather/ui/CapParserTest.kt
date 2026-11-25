package sk.perun.weather.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CapParserTest {
    private fun bytes(name: String) = javaClass.getResourceAsStream("/cap/$name")!!.use { it.readBytes() }
    private fun message(name: String) = parseCap(bytes(name))!!
    private fun alerts(name: String, source: String = "MeteoAlarm") = message(name).toUnified(AlertSourceRef(source, name, ""))
    private fun single(name: String, source: String = "MeteoAlarm"): UnifiedAlert = alerts(name, source).single()

    @Test fun headerAndReferences() {
        val m = message("meteoalarm_sk_snow_orange_update.xml")
        assertEquals("2.49.0.0.703.0.SK.261124090100.6b055258_BA_U4", m.identifier)
        assertEquals("http://www.shmu.sk", m.sender)
        assertEquals("Update", m.msgType)
        assertEquals(listOf(CapKey("http://www.shmu.sk", "2.49.0.0.703.0.SK.261124090100.6b055258_BA_U3")), m.references)
        assertEquals(Instant.parse("2026-11-25T10:48:24Z"), m.sent)
    }

    @Test fun languageVersionsFormOneWarning() {
        val a = single("meteoalarm_sk_snow_orange_update.xml")
        assertEquals(listOf("sk", "en-GB"), a.texts.map { it.language })
        assertEquals(AlertLevel.Orange, a.level)
        assertEquals(Basis.AwarenessCode, a.levelBasis)
        assertEquals(listOf(AlertHazard.Snow), a.hazards)
        assertEquals(Instant.parse("2026-11-25T23:00:00Z"), a.start)
        assertEquals(Instant.parse("2026-11-26T23:00:00Z"), a.end)
        assertEquals(setOf("EMMA_ID=SK100"), a.areas)
        assertEquals("Slovenský Hydrometeorologický ústav", a.issuer)
        assertEquals(setOf(CapKey("http://www.shmu.sk", "2.49.0.0.703.0.SK.261124090100.6b055258_BA_U3")), a.supersedes)
    }

    @Test fun translatedAreaNamesStillFormOneWarning() {
        val a = single("meteoalarm_hu_snow_ice.xml")
        assertEquals(2, a.texts.size)
        assertEquals(listOf("Közép-Dunántúl", "Central Transdanubia"), a.texts.map { it.area })
    }

    @Test fun snowAndIceOfTheSharedType() {
        assertEquals(listOf(AlertHazard.Ice), single("meteoalarm_sk_icing.xml").hazards)
        assertEquals(Basis.EnglishEvent, single("meteoalarm_sk_icing.xml").hazardBasis)
        assertEquals(listOf(AlertHazard.Snow), single("meteoalarm_sk_snow.xml").hazards)
        assertEquals(listOf(AlertHazard.Ice), single("meteoalarm_at_snow.xml").hazards)
        assertEquals(listOf(AlertHazard.Snow), single("meteoalarm_cz_snow.xml").hazards)
        assertEquals(listOf(AlertHazard.Snow), single("meteoalarm_it_snow_ice.xml").hazards)
        assertEquals(Basis.AwarenessCode, single("meteoalarm_it_snow_ice.xml").hazardBasis)
    }

    @Test fun eventCodesDecide() {
        val ice = single("meteoalarm_no_ice.xml")
        assertEquals(listOf(AlertHazard.Ice), ice.hazards)
        assertEquals(Basis.EventCode, ice.hazardBasis)
        val icy = single("meteoalarm_de_icy_surfaces.xml")
        assertEquals(listOf(AlertHazard.Ice), icy.hazards)
        assertEquals(Basis.EventCode, icy.hazardBasis)
    }

    @Test fun otherTypesComeFromTheCode() {
        assertEquals(listOf(AlertHazard.Wind), single("meteoalarm_no_gale.xml").hazards)
        assertEquals(listOf(AlertHazard.Fog), single("meteoalarm_de_fog.xml").hazards)
        assertEquals(listOf(AlertHazard.RainFlood), single("meteoalarm_fr_rain_flood.xml").hazards)
    }

    @Test fun eventEndingTimeEndsTheEvent() {
        val a = single("meteoalarm_cz_snow.xml")
        assertEquals(Instant.parse("2026-11-27T11:00:00Z"), a.end)
        assertEquals(Instant.parse("2026-11-27T10:59:59Z"), a.expires)
    }

    @Test fun oneHungarianNameWithTwoHazards() {
        val a = single("meteoalarm_hu_snow_ice.xml")
        assertEquals(listOf(AlertHazard.Ice, AlertHazard.Snow), a.hazards)
        assertEquals(Basis.OriginalText, a.hazardBasis)
    }

    @Test fun instructionDoesNotAddHazards() {
        // The instruction speaks of "snow and/or ice"; only the name counts.
        val a = single("meteoalarm_sk_snow_orange_update.xml")
        assertTrue("ľad" in a.texts.first().instruction)
        assertEquals(listOf(AlertHazard.Snow), a.hazards)
    }

    @Test fun dwdLanguageFilesShareOneKey() {
        val deu = single("dwd_deu.xml", "LibreWXR")
        val eng = single("dwd_eng.xml", "LibreWXR")
        val mul = single("kde_dwd_mul.xml", "KDE")
        val key = CapKey("opendata@dwd.de", "2.49.0.0.276.0.DWD.PVW.1795604760000.261331c1-0d6e-4437-be9c-c1548af9056a")
        assertEquals(key, deu.key); assertEquals(key, eng.key); assertEquals(key, mul.key)
        assertEquals(8, mul.texts.size)
        assertEquals(deu.areas, mul.areas)
    }

    @Test fun dwdWithoutAwarenessUsesItsSeverity() {
        val a = single("kde_dwd_mul.xml", "KDE")
        assertEquals(AlertLevel.Yellow, a.level)
        assertEquals(Basis.Severity, a.levelBasis)
        assertEquals(listOf(AlertHazard.Cold), a.hazards)
        assertEquals(Basis.EventCode, a.hazardBasis)
    }

    @Test fun meteoAlarmAndKdeShareOneKey() {
        val meteo = single("meteoalarm_sk_icing.xml")
        val kde = single("kde_sk_icing.xml", "KDE")
        assertEquals(meteo.key, kde.key)
        assertEquals(meteo.areas, kde.areas)
        assertEquals(meteo.level, kde.level)
        assertEquals(meteo.hazards, kde.hazards)
    }

    @Test fun areaFilterPicksThePlace() {
        val m = message("kde_dwd_mul.xml")
        val one = m.toUnified(AlertSourceRef("KDE", "x", "")) { area -> area.geocodes.any { it.second == "711000101" } }.single()
        assertEquals(setOf("WARNCELLID=711000101"), one.areas)
        assertTrue(m.toUnified(AlertSourceRef("KDE", "x", "")) { false }.isEmpty())
    }

    private fun synthetic(level: String, status: String = "Actual", category: String = "Met") = """
        <alert xmlns="urn:oasis:names:tc:emergency:cap:1.2">
          <identifier>T.1</identifier><sender>test@example.org</sender><sent>2026-11-25T10:00:00+00:00</sent>
          <status>$status</status><msgType>Alert</msgType><scope>Public</scope>
          <info><language>en-GB</language><category>$category</category><event>Wind warning</event><severity>Minor</severity>
            <onset>2026-11-25T12:00:00+00:00</onset><expires>2026-11-25T18:00:00+00:00</expires>
            <parameter><valueName>awareness_level</valueName><value>$level</value></parameter>
            <parameter><valueName>awareness_type</valueName><value>1; Wind</value></parameter>
            <area><areaDesc>Test</areaDesc><geocode><valueName>EMMA_ID</valueName><value>XX001</value></geocode></area>
          </info>
        </alert>
    """.trimIndent().toByteArray()

    @Test fun greenIsLeftOut() {
        val src = AlertSourceRef("test", "x", "")
        assertTrue(parseCap(synthetic("1; green; Minor"))!!.toUnified(src).isEmpty())
        assertEquals(AlertLevel.Yellow, parseCap(synthetic("2; yellow; Moderate"))!!.toUnified(src).single().level)
    }

    @Test fun onlyActualPublicMeteorologicalWarnings() {
        val src = AlertSourceRef("test", "x", "")
        assertTrue(parseCap(synthetic("2; yellow; Moderate", status = "Test"))!!.toUnified(src).isEmpty())
        assertTrue(parseCap(synthetic("2; yellow; Moderate", category = "Geo"))!!.toUnified(src).isEmpty())
    }

    @Test fun refusesOtherDocuments() {
        assertNull(parseCap("<feed xmlns=\"http://www.w3.org/2005/Atom\"/>".toByteArray()))
        assertNull(parseCap("not xml".toByteArray()))
        val doctype = "<?xml version=\"1.0\"?><!DOCTYPE alert [<!ENTITY x \"y\">]><alert xmlns=\"urn:oasis:names:tc:emergency:cap:1.2\"><identifier>&x;</identifier></alert>"
        assertNull(parseCap(doctype.toByteArray()))
    }
}
