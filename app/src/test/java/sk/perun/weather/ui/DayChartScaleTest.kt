package sk.perun.weather.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DayChartScaleTest {
    @Test fun smallDifferencesGetTheMinimumSpanAroundTheMiddle() {
        val (lo, hi) = dayRowScale(listOf(-0.4, 0.1, 0.6), 2.0)
        assertEquals(-0.9, lo, 1e-9)
        assertEquals(1.1, hi, 1e-9)
    }

    @Test fun largerDifferencesKeepTheirRange() {
        assertEquals(-3.0 to 5.0, dayRowScale(listOf(-3.0, 1.0, 5.0), 2.0))
    }

    @Test fun aFlatDayStaysInTheMiddle() {
        assertEquals(-1.0 to 1.0, dayRowScale(listOf(0.0, 0.0), 2.0))
    }
}
