package sk.perun.weather.ui

import org.junit.Assert.*
import org.junit.Test

class LongForecastMathTest : ResourceStringsTestBase() {
    @Test fun defaultBandUsesInterpolatedPercentiles() {
        val band = ensembleBand((0..10).map { it.toDouble() })!!
        assertEquals(1.0, band.lower, 1e-9)
        assertEquals(9.0, band.upper, 1e-9)
        assertEquals(0.0, band.minimum, 1e-9)
        assertEquals(10.0, band.maximum, 1e-9)
        assertEquals(11, band.memberCount)
    }

    @Test fun ignoresMissingAndInfiniteMembers() {
        val band = ensembleBand(listOf(Double.NaN, 0.0, Double.POSITIVE_INFINITY, 10.0))!!
        assertEquals(1.0, band.lower, 1e-9)
        assertEquals(9.0, band.upper, 1e-9)
        assertEquals(2, band.memberCount)
    }

    @Test fun fewerThanTwoMembersHasNoUncertaintyBand() {
        assertNull(ensembleBand(emptyList()))
        assertNull(ensembleBand(listOf(5.0, Double.NaN)))
    }

    @Test fun customPercentilesCanIncludeFullRange() {
        val band = ensembleBand(listOf(3.0, 1.0, 2.0), 0, 100)!!
        assertEquals(band.minimum, band.lower, 1e-9)
        assertEquals(band.maximum, band.upper, 1e-9)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invertedPercentilesAreRejected() { ensembleBand(listOf(1.0, 2.0), 90, 10) }

    @Test(expected = IllegalArgumentException::class)
    fun equalPercentilesAreRejected() { ensembleBand(listOf(1.0, 2.0), 50, 50) }

    @Test fun shorterMembersDoNotExtendUncertaintyHorizon() {
        val bands = ensembleBandsAtTimes(listOf(listOf(0.0, 2.0), listOf(10.0)), 3)
        assertNotNull(bands[0])
        assertNull(bands[1])
        assertNull(bands[2])
    }
}
