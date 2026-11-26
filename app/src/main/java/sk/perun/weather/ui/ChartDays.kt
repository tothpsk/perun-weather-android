package sk.perun.weather.ui

import sk.perun.weather.R

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** A day column of a chart: from [x0] to [x1]; [whole] when the chart shows all of the day. */
internal class ChartDay(val date: LocalDate, val x0: Float, val x1: Float, val whole: Boolean)

/**
 * The labels of the days of a chart, all in one form, as in the chart of the Alerts screen: the name and the date
 * ("Dnes · 26. 11.") when that fits every whole day, otherwise the date only. A day that does not fit its label,
 * usually a part day at an edge, gets none. [width] measures a label; [gap] is the room left beside it.
 */
internal fun chartDayLabels(days: List<ChartDay>, today: LocalDate, gap: Float, width: (String) -> Float): List<String?> {
    val date = DateTimeFormatter.ofPattern(appPattern("d. M."), appLocale())
    fun name(d: LocalDate) = when (d) {
        today -> uiText(R.string.ui_dnes_ce1bed34)
        today.plusDays(1) -> uiText(R.string.ui_zajtra_c3e86eb2)
        else -> d.format(DateTimeFormatter.ofPattern("EEE", appLocale()))
    }
    fun full(d: LocalDate) = name(d) + " · " + d.format(date)
    val reference = days.filter { it.whole }.ifEmpty { days }
    val named = reference.isNotEmpty() && reference.all { width(full(it.date)) <= it.x1 - it.x0 - gap }
    return days.map { d ->
        val label = if (named) full(d.date) else d.date.format(date)
        label.takeIf { width(it) <= d.x1 - d.x0 - gap }
    }
}
