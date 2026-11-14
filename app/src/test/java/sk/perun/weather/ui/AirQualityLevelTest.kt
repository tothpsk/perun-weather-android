package sk.perun.weather.ui

import org.junit.Assert.*
import org.junit.Test

class AirQualityLevelTest : ResourceStringsTestBase() {
    @Test fun europeanBands() {
        listOf(0.0 to 0,20.0 to 0,20.1 to 1,40.0 to 1,40.1 to 2,60.1 to 3,80.1 to 4,100.1 to 5).forEach { (v,b) -> assertEquals(b,airQualityLevel(v,AirQualityScale.European)?.band) }
    }
    @Test fun americanBands() {
        listOf(50.0 to 0,50.1 to 1,100.1 to 2,150.1 to 3,200.1 to 4,300.1 to 5,501.0 to 5).forEach { (v,b) -> assertEquals(b,airQualityLevel(v,AirQualityScale.UnitedStates)?.band) }
    }
    @Test fun missingIsNotGood() { listOf(Double.NaN,Double.POSITIVE_INFINITY,-1.0).forEach { v -> AirQualityScale.entries.forEach { assertNull(airQualityLevel(v,it)) } } }
    @Test fun scalesAreNotInterchangeable() { assertEquals(4,airQualityLevel(90.0,AirQualityScale.European)?.band);assertEquals(1,airQualityLevel(90.0,AirQualityScale.UnitedStates)?.band) }
    @Test fun carbonMonoxideHasNoEuropeanComponent() { assertFalse(AirPollutants.single { it.key=="carbon_monoxide" }.european) }
}
