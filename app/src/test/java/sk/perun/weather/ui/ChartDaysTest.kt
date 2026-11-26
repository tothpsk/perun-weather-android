package sk.perun.weather.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class ChartDaysTest : ResourceStringsTestBase() {
    private val first = LocalDate.of(2026, 11, 26)
    // One unit of width for each character; days of [day] units, the view starts [shift] units into the first day.
    private fun labels(day: Float, shift: Float, count: Int = 4): List<String?> {
        val view = day * (count - 1)
        val days = (0 until count).map { i ->
            val a = i * day - shift; val b = a + day
            ChartDay(first.plusDays(i.toLong()), a.coerceIn(0f, view), b.coerceIn(0f, view))
        }
        return chartDayLabels(days, day, 0f, Locale.ENGLISH) { it.length.toFloat() }
    }

    @Test fun theFormDoesNotDependOnScrolling() {
        for (day in listOf(9f, 20f)) {
            val forms = listOf(0f, day / 3, day * 0.9f).map { shift ->
                labels(day, shift).filterNotNull().map { it.contains(" · ") }.distinct()
            }
            // Every view has one form, and all views have the same one.
            assertEquals(1, forms.flatten().distinct().size)
        }
    }

    @Test fun nameWhenItFitsOtherwiseDate() {
        assertEquals(true, labels(20f, 0f)[0]!!.startsWith("Thu · "))
        assertEquals(false, labels(9f, 0f)[0]!!.contains(" · "))
    }

    @Test fun aPartDayThatDoesNotFitHasNoLabel() {
        assertNull(labels(20f, 15f)[0])
    }
}
