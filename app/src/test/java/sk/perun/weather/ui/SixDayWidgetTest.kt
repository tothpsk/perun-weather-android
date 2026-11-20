package sk.perun.weather.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class SixDayWidgetTest {
    private val zone=ZoneOffset.UTC
    private val first=LocalDate.of(2026,11,17)
    private val times=(0 until 72).map { first.atStartOfDay(zone).toEpochSecond()+it*3600L }
    private val temps=times.indices.map { (it%24).toDouble() }
    private val codes=times.indices.map { if(it%24==12) 61.0 else 3.0 }

    @Test fun daysStartToday() {
        val days=daySummaries(times,temps,codes,zone,first.plusDays(1))
        assertEquals(listOf(first.plusDays(1),first.plusDays(2)),days.map { it.date })
    }

    @Test fun dayHasExtremesAndNoonCode() {
        val day=daySummaries(times,temps,codes,zone,first).first()
        assertEquals(23.0,day.max,0.0)
        assertEquals(0.0,day.min,0.0)
        assertEquals(61,day.code)
    }

    @Test fun dayWithoutTemperatureIsLeftOut() {
        val gaps=temps.mapIndexed { i,v -> if(i<24) Double.NaN else v }
        assertEquals(first.plusDays(1),daySummaries(times,gaps,codes,zone,first).first().date)
        assertEquals(1,daySummaries(times,temps,codes,zone,first,1).size)
    }
}
