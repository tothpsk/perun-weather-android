package sk.perun.weather.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlertModelTest {
    @Test fun dwdKeysLoseOnlyTheirLanguage() {
        val core = "2.49.0.0.276.0.DWD.PVW.1795604760000.261331c1-0d6e-4437-be9c-c1548af9056a"
        assertEquals(CapKey("opendata@dwd.de", core), capKey("opendata@dwd.de", "$core.DEU"))
        assertEquals(CapKey("opendata@dwd.de", core), capKey("OPENDATA@dwd.de", "$core.MUL"))
        assertEquals("2.49.0.0.40.0.AT.20261125131415.ABC", capKey("cap@zamg.ac.at", "2.49.0.0.40.0.AT.20261125131415.ABC")!!.identifier)
        assertNull(capKey("", "x"))
        assertNull(capKey("a", " "))
    }

    @Test fun levelFromAwarenessFirst() {
        assertEquals(AlertLevel.Orange to Basis.AwarenessCode, classifyLevel("3; orange; Severe", "Moderate", "x", emptyList()))
        assertEquals(AlertLevel.Green to Basis.AwarenessCode, classifyLevel("1; green; Minor", null, "x", emptyList()))
        assertEquals(AlertLevel.Red to Basis.EnglishEvent, classifyLevel(null, "Moderate", "x", listOf("Red Wind Warning")))
    }

    @Test fun severityDependsOnTheIssuer() {
        assertEquals(AlertLevel.Yellow, classifyLevel(null, "Moderate", "vigilance@meteo.fr", emptyList()).first)
        assertEquals(AlertLevel.Orange, classifyLevel(null, "Severe", "vigilance@meteo.fr", emptyList()).first)
        assertEquals(AlertLevel.Yellow, classifyLevel(null, "Minor", "opendata@dwd.de", emptyList()).first)
        assertEquals(AlertLevel.Orange, classifyLevel(null, "Moderate", "opendata@dwd.de", emptyList()).first)
        assertEquals(AlertLevel.Red, classifyLevel(null, "Severe", "opendata@dwd.de", emptyList()).first)
        assertEquals(AlertLevel.Neutral to Basis.Fallback, classifyLevel(null, "Unknown", "x", emptyList()))
    }

    @Test fun namesWithMoreHazards() {
        assertEquals(listOf(AlertHazard.Ice, AlertHazard.Snow), nameHazards("Road ice and snow"))
        assertEquals(listOf(AlertHazard.Ice, AlertHazard.Snow), nameHazards("sárga ónos eső, hófúvás figyelmeztetés"))
        assertEquals(listOf(AlertHazard.RainFlood), nameHazards("Moderate rain-flood warning"))
        assertEquals(emptyList<AlertHazard>(), nameHazards("Yellow Snow-ice Warning"))
        assertEquals(emptyList<AlertHazard>(), nameHazards("Snow-Icewarning"))
    }

    @Test fun hazardOrder() {
        fun h(type: String?, codes: List<Pair<String, String>> = emptyList(), en: String = "", orig: String = "", head: String = "") =
            classifyHazards(HazardInput(type, codes, en, orig, head))
        assertEquals(listOf(AlertHazard.Wind) to Basis.AwarenessCode, h("1; Wind", en = "Snow"))
        assertEquals(listOf(AlertHazard.Ice) to Basis.EventCode, h("2; snow-ice", listOf("GROUP" to "SLIPPERINESS"), en = "Snow"))
        assertEquals(listOf(AlertHazard.Snow) to Basis.AwarenessCode, h("2; Snow-ice", en = "Yellow Snow-ice Warning"))
        assertEquals(listOf(AlertHazard.Cold) to Basis.EventCode, h(null, listOf("GROUP" to "FROST"), en = "frost"))
        assertEquals(listOf(AlertHazard.Storm) to Basis.EnglishEvent, h(null, en = "Thunderstorm warning"))
        assertEquals(listOf(AlertHazard.Fog) to Basis.OriginalText, h(null, orig = "Nebel"))
        assertEquals(listOf(AlertHazard.Generic) to Basis.Fallback, h(null))
    }

    @Test fun textInThePreferredLanguage() {
        fun t(lang: String) = AlertText(lang, lang, "", "", "", "")
        assertEquals("sk", pickText(listOf(t("hu-HU"), t("en-GB"), t("sk")))!!.language)
        assertEquals("en-GB", pickText(listOf(t("hu-HU"), t("en-GB")))!!.language)
        assertEquals("hu-HU", pickText(listOf(t("hu-HU")))!!.language)
        assertEquals("en", pickText(listOf(t("de-DE"), t("en"), t("fr")), "en-GB")!!.language)
        assertNull(pickText(emptyList()))
    }
}
