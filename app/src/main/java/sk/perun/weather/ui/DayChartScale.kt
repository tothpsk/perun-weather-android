package sk.perun.weather.ui

import java.math.BigDecimal
import java.math.RoundingMode

/** A value of a row of the expanded day rounded as its label is ([weatherUnitNumber] rounds half up). */
internal fun dayRowValue(x: Double, decimals: Int): Double =
    if (x.isFinite()) BigDecimal(x).setScale(decimals, RoundingMode.HALF_UP).toDouble() else x

/**
 * The scale of a row of the expanded day: from the lowest to the highest value, at least [minSpan] wide. When the
 * values differ less, the scale is widened evenly up and down, so the curve stays in the middle of the row and
 * small changes look small.
 */
internal fun dayRowScale(values: List<Double>, minSpan: Double = 1.0): Pair<Double, Double> {
    val lo = values.minOrNull() ?: 0.0
    val hi = values.maxOrNull() ?: lo
    if (hi - lo >= minSpan) return lo to hi
    val middle = (lo + hi) / 2
    return middle - minSpan / 2 to middle + minSpan / 2
}
