package sk.perun.weather.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A day column of a chart, from [x0] to [x1], cut to the visible part. */
internal class ChartDay(val date: LocalDate, val x0: Float, val x1: Float)

/**
 * The labels of the days of a scrollable chart, all in one form: the short name of the day and the date
 * ("pi · 28. 11.") when the widest such label fits a whole day, otherwise the date only. The form depends only on
 * [dayWidth], the width of a whole day, so it stays the same wherever the chart is scrolled. A day whose visible
 * part does not fit its label, usually a part day at an edge, gets none. [width] measures a label; [gap] is the
 * room left beside it.
 */
internal fun chartDayLabels(days: List<ChartDay>, dayWidth: Float, gap: Float, locale: Locale = appLocale(), width: (String) -> Float): List<String?> {
    val date = DateTimeFormatter.ofPattern(appPattern("d. M."), locale)
    val name = DateTimeFormatter.ofPattern("EEE", locale)
    fun full(d: LocalDate) = d.format(name) + " · " + d.format(date)
    // The widest name of a day and the widest date, independent of the days shown.
    val monday = LocalDate.of(2026, 1, 5)
    val widestName = (0L..6L).map { monday.plusDays(it).format(name) }.maxBy(width)
    val widestDate = (1..12).map { LocalDate.of(2026, it, 28).format(date) }.maxBy(width)
    val named = width("$widestName · $widestDate") <= dayWidth - gap
    return days.map { d ->
        val label = if (named) full(d.date) else d.date.format(date)
        label.takeIf { width(it) <= d.x1 - d.x0 - gap }
    }
}
