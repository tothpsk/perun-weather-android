package sk.perun.weather.ui

import kotlin.math.ceil
import kotlin.math.floor

internal data class EnsembleBand(
    val lower: Double,
    val upper: Double,
    val minimum: Double,
    val maximum: Double,
    val memberCount: Int,
)

internal fun ensembleBand(values: List<Double>, lowerPercentile: Int = 10, upperPercentile: Int = 90): EnsembleBand? {
    require(lowerPercentile in 0..100 && upperPercentile in 0..100 && lowerPercentile < upperPercentile)
    val sorted = values.filter { it.isFinite() }.sorted()
    if (sorted.size < 2) return null
    fun percentile(p: Int): Double {
        val index = (sorted.size - 1) * (p / 100.0)
        val lo = floor(index).toInt()
        val hi = ceil(index).toInt()
        return sorted[lo] + (sorted[hi] - sorted[lo]) * (index - lo)
    }
    return EnsembleBand(percentile(lowerPercentile), percentile(upperPercentile), sorted.first(), sorted.last(), sorted.size)
}

internal fun ensembleBandsAtTimes(
    members: List<List<Double>>,
    timeCount: Int,
    lowerPercentile: Int = 10,
    upperPercentile: Int = 90,
): List<EnsembleBand?> {
    require(timeCount >= 0)
    require(lowerPercentile in 0..100 && upperPercentile in 0..100 && lowerPercentile < upperPercentile)
    return List(timeCount) { index ->
        ensembleBand(members.mapNotNull { it.getOrNull(index) }, lowerPercentile, upperPercentile)
    }
}
