package sk.perun.weather.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DayChartScaleTest {
    @Test fun smallDifferencesStayInTheMiddleOfTheMinimumSpan() {
        val (lo, hi) = dayRowScale(listOf(-0.4, 0.2, 0.6), 4.0)
        assertEquals(-1.9, lo, 1e-9)
        assertEquals(2.1, hi, 1e-9)
        assertEquals(-2.0 to 2.0, dayRowScale(listOf(0.0, 0.0), 4.0))
    }

    @Test fun largerDifferencesUseTheirRange() {
        assertEquals(-3.0 to 5.0, dayRowScale(listOf(-3.0, 1.0, 5.0), 4.0))
        assertEquals(0.0 to 4.0, dayRowScale(listOf(0.0, 4.0), 4.0))
    }
}
