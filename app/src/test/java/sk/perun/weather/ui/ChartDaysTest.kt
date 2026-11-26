package sk.perun.weather.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ChartDaysTest : ResourceStringsTestBase() {
    private val today = LocalDate.of(2026, 11, 26)
    // One unit of width for each character.
    private fun labels(widths: List<Pair<Float, Boolean>>): List<String?> {
        var x = 0f
        val days = widths.mapIndexed { i, (w, whole) -> ChartDay(today.plusDays(i.toLong()), x, x + w, whole).also { x += w } }
        return chartDayLabels(days, today, 0f) { it.length.toFloat() }
    }

    @Test fun allWholeDaysHaveTheSameForm() {
        val wide = labels(listOf(20f to true, 20f to true, 20f to true))
        assertEquals(listOf(true, true, true), wide.map { it!!.contains(" · ") })
        // One whole day too narrow for the name: all days show the date only.
        val narrow = labels(listOf(20f to true, 10f to true, 20f to true))
        assertEquals(listOf(false, false, false), narrow.map { it!!.contains(" · ") })
    }

    @Test fun aPartDayKeepsTheFormOrHasNoLabel() {
        val l = labels(listOf(20f to true, 20f to true, 4f to false))
        assertEquals(true, l[0]!!.contains(" · "))
        assertEquals(null, l[2])
    }
}
