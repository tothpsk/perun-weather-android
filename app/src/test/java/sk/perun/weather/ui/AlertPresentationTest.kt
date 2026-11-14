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
    @Test fun frenchAndEnglishWindGroupWithoutDeletingMembers() {
        val a=item(); val b=item("Vigilance jaune vent-violent","fr").copy(identifier="cap-2",via=setOf("LibreWXR"))
        val g=groupAlertPresentation(listOf(a,b)).single()
        assertEquals("Vietor",g.label); assertEquals(listOf(a,b),g.items)
        assertEquals(2,mergeAlertRecords(listOf(a,b)).size)
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
