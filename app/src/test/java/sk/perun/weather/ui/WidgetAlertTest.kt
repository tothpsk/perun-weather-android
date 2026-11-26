package sk.perun.weather.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class WidgetAlertTest : ResourceStringsTestBase() {
    private val now=Instant.parse("2026-11-24T18:00:00Z")
    private fun a(level:AlertLevel,startH:Long,endH:Long,local:Boolean=false,id:String=level.key)=
        UnifiedAlert(CapKey("s",id),emptySet(),"Alert",listOf(AlertHazard.Snow),Basis.AwarenessCode,level,Basis.AwarenessCode,
            now.plusSeconds(startH*3600),now.plusSeconds(endH*3600),now.plusSeconds(endH*3600),now,emptySet(),"","",
            listOf(AlertText("sk","Sneh","","","","")),emptyList(),local)

    @Test fun onlyWarningsThatOverlapThePeriod() {
        val tomorrow=a(AlertLevel.Yellow,14,30)
        assertNull(widgetAlert(listOf(tomorrow),now,now.plusSeconds(6*3600)))
        assertEquals(tomorrow,widgetAlert(listOf(tomorrow),now,now.plusSeconds(6*86400)))
        assertNull(widgetAlert(listOf(a(AlertLevel.Orange,-10,-1)),now,now.plusSeconds(3600)))
    }

    @Test fun strongestThenEarliest() {
        val later=a(AlertLevel.Orange,5,8,id="later");val sooner=a(AlertLevel.Orange,1,3,id="sooner")
        assertEquals("sooner",widgetAlert(listOf(a(AlertLevel.Yellow,0,2),later,sooner),now,now.plusSeconds(86400))?.key?.identifier)
        assertEquals(AlertLevel.Red,widgetAlert(listOf(a(AlertLevel.Yellow,0,2),a(AlertLevel.Red,20,22)),now,now.plusSeconds(86400))?.level)
    }

    @Test fun localEstimatesAndOtherLevelsStayOut() {
        assertNull(widgetAlert(listOf(a(AlertLevel.Red,0,2,local=true),a(AlertLevel.Neutral,0,2)),now,now.plusSeconds(86400)))
    }
}
