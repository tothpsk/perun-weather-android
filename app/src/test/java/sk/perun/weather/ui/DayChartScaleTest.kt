package sk.perun.weather.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class DayChartScaleTest {
    @Test fun valuesAreRoundedAsTheirLabels() {
        for (x in listOf(-0.6, -0.5, -0.4, 0.4, 0.5, 1.49, 2.5)) {
            val label = weatherUnitNumber(x, 0, Locale.ROOT)
            assertEquals(label.toDouble(), dayRowValue(x, 0), 1e-9)
        }
        assertEquals(1013.0, dayRowValue(1012.7, 0), 1e-9)
    }

    @Test fun smallDifferencesStayInTheMiddleOfTheMinimumSpan() {
        val values = listOf(-0.4, 0.2, 0.6).map { dayRowValue(it, 0) }
        assertEquals(-1.5 to 2.5, dayRowScale(values, 4.0))
        assertEquals(-2.0 to 2.0, dayRowScale(listOf(0.0, 0.0), 4.0))
    }

    @Test fun largerDifferencesUseTheirRange() {
        assertEquals(-3.0 to 5.0, dayRowScale(listOf(-3.0, 1.0, 5.0), 4.0))
        assertEquals(0.0 to 4.0, dayRowScale(listOf(0.0, 4.0), 4.0))
    }
}
