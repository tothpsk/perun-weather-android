package sk.perun.weather.ui

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class AlertPresentationTest : ResourceStringsTestBase() {
    private val start=Instant.parse("2026-10-25T05:00:00Z")
    private val end=start.plusSeconds(3600)
    private fun alert(hazards: List<AlertHazard> =listOf(AlertHazard.Wind),level: AlertLevel=AlertLevel.Orange,local: Boolean=false,event: String="Moderate wind warning",id: String="cap-1")=
        UnifiedAlert(CapKey("s",id),emptySet(),"Alert",hazards,Basis.AwarenessCode,level,Basis.AwarenessCode,start,end,end,start,emptySet(),"Météo-France","",
            listOf(AlertText("en",event,"","","","")),listOf(AlertSourceRef("KDE",id,"")),local)
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
    @Test fun sameKindAndLevelGroupWithoutDeletingMembers() {
        val a=alert(); val b=alert(event="Vigilance jaune vent-violent",id="cap-2")
        val g=groupAlertPresentation(listOf(a,b)).single()
        assertEquals("Vietor",g.label); assertEquals(listOf(a,b),g.items); assertEquals(AlertHazard.Wind,g.hazard)
    }
    @Test fun levelsKindsAndLocalEstimatesStaySeparate() {
        assertEquals(4,groupAlertPresentation(listOf(alert(),alert(level=AlertLevel.Red),alert(local=true,event="Silné nárazy vetra"),alert(hazards=listOf(AlertHazard.Rain)))).size)
        assertEquals("Silné nárazy vetra",groupAlertPresentation(listOf(alert(local=true,event="Silné nárazy vetra"))).single().label)
    }
    @Test fun unknownHazardsKeepTheirNames() {
        val g=groupAlertPresentation(listOf(alert(hazards=listOf(AlertHazard.Generic),event="Unclassified A")))
        assertEquals("Unclassified A",g.single().label)
    }
}
