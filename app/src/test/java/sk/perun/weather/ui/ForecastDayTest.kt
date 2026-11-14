package sk.perun.weather.ui

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class ForecastDayTest : ResourceStringsTestBase() {
    @Test fun missingPrecipitationIsNotZero() {
        val day=ForecastDay(LocalDate.of(2026,10,30),0,86400,listOf(ForecastHour(0,emptyMap())))
        assertTrue(day.sum("precipitation").isNaN())
    }
    @Test fun pressureTrendNeedsExactThreeHours() {
        val h=ForecastHour(10800,mapOf("pressure_msl" to 1010.0))
        assertTrue(forecastTrend(h,mapOf(3600L to ForecastHour(3600,mapOf("pressure_msl" to 1008.0)))).isNaN())
        assertEquals(2.0,forecastTrend(h,mapOf(0L to ForecastHour(0,mapOf("pressure_msl" to 1008.0)))),0.0)
    }
    @Test fun gustIsOnlyExtension() {
        val (a,b)=forecastWindParts(20.0,40.0,50.0)
        assertEquals(.4,a,1e-9);assertEquals(.4,b,1e-9)
    }
    @Test fun completeDstDayHasTwentyFiveHours() {
        val z=ZoneId.of("Europe/Bratislava");val date=LocalDate.of(2026,10,25)
        val a=date.atStartOfDay(z).toEpochSecond();val b=date.plusDays(1).atStartOfDay(z).toEpochSecond()
        val hours=(a until b step 3600L).map { ForecastHour(it,mapOf("temperature_2m" to 10.0)) }
        assertEquals(25,hours.size);assertFalse(ForecastDay(date,a,b,hours).partial)
    }
}
