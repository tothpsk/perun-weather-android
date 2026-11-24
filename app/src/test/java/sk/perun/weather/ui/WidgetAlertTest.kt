package sk.perun.weather.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class WidgetAlertTest {
    private val now=Instant.parse("2026-11-24T18:00:00Z")
    private fun a(level:String,startH:Long?,endH:Long?,local:Boolean=false,event:String=level)=
        AlertItem(event=event,level=level,local=local,start=startH?.let { now.plusSeconds(it*3600) },end=endH?.let { now.plusSeconds(it*3600) })

    @Test fun onlyWarningsThatOverlapThePeriod() {
        val tomorrow=a("yellow",14,30)
        assertNull(widgetAlert(listOf(tomorrow),now,now.plusSeconds(6*3600)))
        assertEquals(tomorrow,widgetAlert(listOf(tomorrow),now,now.plusSeconds(6*86400)))
        assertNull(widgetAlert(listOf(a("orange",-10,-1)),now,now.plusSeconds(3600)))
    }

    @Test fun strongestThenEarliest() {
        val later=a("orange",5,8,event="later");val sooner=a("orange",1,3,event="sooner")
        assertEquals("sooner",widgetAlert(listOf(a("yellow",0,2),later,sooner),now,now.plusSeconds(86400))?.event)
        assertEquals("red",widgetAlert(listOf(a("yellow",0,2),a("red",20,22)),now,now.plusSeconds(86400))?.level)
    }

    @Test fun localEstimatesAndOtherLevelsStayOut() {
        assertNull(widgetAlert(listOf(a("red",0,2,local=true),a("neutral",0,2)),now,now.plusSeconds(86400)))
    }

    @Test fun openValidity() {
        assertEquals("yellow",widgetAlert(listOf(a("yellow",null,null)),now,now.plusSeconds(3600))?.level)
    }
}
