package sk.perun.weather.ui

import org.junit.Assert.*
import org.junit.Test

class MarineDataTest : ResourceStringsTestBase() {
    @Test fun distances() { assertEquals(0.0,marineDistance(0.0,0.0,0.0,0.0),1e-9);assertEquals(111.195,marineDistance(0.0,0.0,0.0,1.0),.001) }
    @Test fun headingIsTravelNotSource() { assertEquals(180.0,marineTravelBearing(0.0),0.0);assertEquals(270.0,marineTravelBearing(90.0),0.0);assertEquals(90.0,marineTravelBearing(270.0),0.0);assertTrue(marineTravelBearing(Double.NaN).isNaN()) }
    @Test fun negativeLevelIsValid() { assertEquals(-.25,marineValue("sea_level_height_msl",-.25),0.0);assertEquals(-1.0,marineValue("sea_surface_temperature",-1.0),0.0);assertTrue(marineValue("wave_height",-1.0).isNaN()) }
    @Test fun flatNoiseHasNoExtreme() { assertTrue(marineExtremes(listOf(.001,.002,.001,.002)).isEmpty()) }
    @Test fun plateauAndGaps() { assertEquals(listOf(MarineExtreme(1,true)),marineExtremes(listOf(0.0,.5,.5,0.0)));assertTrue(marineExtremes(listOf(0.0,Double.NaN,0.0)).isEmpty()) }
    @Test fun minimum() { assertEquals(listOf(MarineExtreme(1,false)),marineExtremes(listOf(1.0,0.0,1.0))) }
}
