package sk.perun.weather.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DayWidgetTest {
    private val first=LocalDate.of(2026,11,18)
    private val days=(0L..3L).map { first.plusDays(it) }

    @Test fun rowCountFollowsHeight() {
        assertEquals(1,dayWidgetRowCount(58,12))
        assertEquals(4,dayWidgetRowCount(148,12))
        assertEquals(12,dayWidgetRowCount(600,12))
        assertEquals(3,dayWidgetRowCount(600,3))
        assertEquals(1,dayWidgetRowCount(10,0))
    }

    @Test fun chosenDayFallsBackToFirst() {
        assertEquals(first,dayWidgetDate(null,days))
        assertEquals(first,dayWidgetDate(first.minusDays(2),days))
        assertEquals(first.plusDays(2),dayWidgetDate(first.plusDays(2),days))
        assertEquals(first.plusDays(3),dayWidgetDate(first.plusDays(9),days))
        assertEquals(null,dayWidgetDate(first,emptyList()))
    }

    @Test fun stepStaysInRange() {
        assertEquals(first,dayWidgetStep(first,days,-1))
        assertEquals(first.plusDays(1),dayWidgetStep(first,days,1))
        assertEquals(first.plusDays(3),dayWidgetStep(first.plusDays(3),days,1))
    }
}
