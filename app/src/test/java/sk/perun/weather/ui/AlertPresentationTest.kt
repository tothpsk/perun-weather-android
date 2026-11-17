package sk.perun.weather.ui

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class AlertPresentationTest : ResourceStringsTestBase() {
    private val start=Instant.parse("2026-10-25T05:00:00Z")
    private val end=start.plusSeconds(3600)
    private fun item(text: String="Moderate wind warning",language: String="en",level: String="orange",local: Boolean=false)=AlertItem(text,level,local,start,end,"Météo-France",text,"cap-1","issuer",language,"wind","area-1","",setOf("KDE"))
    @Test fun unixSecondsAndMillisecondsAgree() {
        val expected=Instant.ofEpochSecond(1778680800)
        assertEquals(expected,parseAlertTimestamp("1778680800"))
        assertEquals(expected,parseAlertTimestamp("1778680800000"))
        assertEquals(expected.plusMillis(500),parseAlertTimestamp("1778680800.5"))
    }
    @Test fun isoAndBadTimes() {
        assertEquals(start,parseAlertTimestamp("2026-10-25T06:00:00+01:00"))
        assertNull(parseAlertTimestamp(null)); assertNull(parseAlertTimestamp("")); assertNull(parseAlertTimestamp("not a time"))
    }
    @Test fun exactLocaleThenEnglish() {
        val preferred=item(language="sk-SK")
        assertEquals(listOf(preferred),selectAlertLanguages(listOf(item(language="de-DE"),item(language="sk-CZ"),preferred)))
        assertEquals("en",selectAlertLanguages(listOf(item(language="fr"),item())).single().language)
    }
    @Test fun phoneLanguagesDecideInTheirOrder() {
        val de=item(language="de-DE"); val en=item(language="en-GB"); val sk=item(language="sk-SK")
        assertEquals(listOf(en),selectAlertLanguages(listOf(sk,de,en),listOf("en-US")))
        assertEquals(listOf(de),selectAlertLanguages(listOf(sk,de,en),listOf("de-AT","en-US")))
        assertEquals(listOf(en),selectAlertLanguages(listOf(sk,en),listOf("fr-FR")))
    }
    @Test fun frenchAndEnglishWindGroupWithoutDeletingMembers() {
        val a=item(); val b=item("Vigilance jaune vent-violent","fr").copy(identifier="cap-2",via=setOf("LibreWXR"))
        val g=groupAlertPresentation(listOf(a,b)).single()
        assertEquals("Vietor",g.label); assertEquals(listOf(a,b),g.items)
        assertEquals(2,mergeAlertRecords(listOf(a,b)).size)
    }
    @Test fun fogInThreeWordingsIsOneGroupWithTheFogIcon() {
        val texts=listOf("Moderate fog warning","Očakávame hmlu so zníženou dohľadnosťou do 50 metrov","Výstraha pred hmlou")
        texts.forEach { assertEquals(it,AlertKind.Fog,alertKind(it)) }
        val items=texts.mapIndexed { i,t ->item(t,level="yellow").copy(identifier="cap-$i",via=setOf("source-$i")) }
        val g=groupAlertPresentation(items).single()
        assertEquals("Hmla",g.label); assertEquals(items,g.items)
    }
    @Test fun stemsCountOnlyAtTheStartOfAWord() {
        assertEquals(AlertKind.Other,alertKind("Znížená dohľadnosť"))
        assertEquals(AlertKind.Other,alertKind("Coastal event"))
        assertEquals(AlertKind.Ice,alertKind("Moderate icing warning"))
        assertEquals(AlertKind.Heat,alertKind("Yellow High-temperature Warning"))
        assertEquals(AlertKind.Storm,alertKind("Thunderstorm risk"))
        assertEquals(AlertKind.Wind,alertKind("Strong wind gusts"))
        assertEquals(AlertKind.Rain,alertKind("Heavy precipitation"))
        assertEquals(AlertKind.Cold,alertKind("Extreme cold"))
        assertEquals(listOf(AlertKind.Snow,AlertKind.Ice),alertKinds("Yellow Snow-ice Warning"))
    }
    @Test fun severitiesAndLocalEstimateStaySeparate() {
        assertEquals(3,groupAlertPresentation(listOf(item(),item(level="red"),item(local=true))).size)
    }
    @Test fun unknownHazardsStaySeparate() {
        assertEquals(2,groupAlertPresentation(listOf(item("Unclassified A"),item("Unclassified B"))).size)
    }
    @Test fun noMergeForMissingTimeOrDifferentArea() {
        assertEquals(2,mergeAlertRecords(listOf(item(),item().copy(start=null))).size)
        assertEquals(2,mergeAlertRecords(listOf(item(),item().copy(areaKey="area-2"))).size)
    }
    @Test fun confirmedIdentityCombinesProvenance() {
        val a=item(); val b=item().copy(via=setOf("LibreWXR"))
        assertEquals(setOf("KDE","LibreWXR"),mergeAlertRecords(listOf(a,b)).single().via)
    }
}
