package sk.perun.weather.ui

import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class WeatherUnitsTest : ResourceStringsTestBase() {
    @Test fun celsiusAndFahrenheit() {
        assertEquals(0.0, TemperatureUnit.Celsius.fromCelsius(0.0), 0.0)
        assertEquals(32.0, TemperatureUnit.Fahrenheit.fromCelsius(0.0), 1e-9)
        assertEquals(212.0, TemperatureUnit.Fahrenheit.fromCelsius(100.0), 1e-9)
        assertEquals(-40.0, TemperatureUnit.Fahrenheit.fromCelsius(-40.0), 1e-9)
    }
    @Test fun speedConversions() {
        assertEquals(10.0, WindSpeedUnit.MetresPerSecond.fromKmh(36.0), 1e-9)
        assertEquals(1.0, WindSpeedUnit.MilesPerHour.fromKmh(1.609344), 1e-9)
        assertEquals(1.0, WindSpeedUnit.Knots.fromKmh(1.852), 1e-9)
        assertEquals(36.0, WindSpeedUnit.KilometresPerHour.fromKmh(36.0), 0.0)
    }
    @Test fun pressureConversions() {
        assertEquals(1013.25, PressureUnit.Hectopascals.fromHpa(1013.25), 0.0)
        assertEquals(760.0, PressureUnit.MillimetresMercury.fromHpa(1013.25), 0.001)
        assertEquals(29.9213, PressureUnit.InchesMercury.fromHpa(1013.25), 0.0001)
    }
    @Test fun liquidAndSnowAreDifferentUnits() {
        assertEquals(1.0, PrecipitationUnit.Imperial.fromMillimetres(25.4), 1e-9)
        assertEquals(1.0, PrecipitationUnit.Imperial.fromSnowCentimetres(2.54), 1e-9)
        assertEquals(2.54, PrecipitationUnit.Metric.fromSnowCentimetres(2.54), 0.0)
        assertEquals(25.4, PrecipitationUnit.Metric.fromMillimetres(25.4), 0.0)
    }
    @Test fun missingValuesNeverBecomeZero() {
        TemperatureUnit.entries.forEach { assertTrue(it.fromCelsius(Double.NaN).isNaN()) }
        WindSpeedUnit.entries.forEach { assertTrue(it.fromKmh(Double.NaN).isNaN()) }
        PressureUnit.entries.forEach { assertTrue(it.fromHpa(Double.POSITIVE_INFINITY).isNaN()) }
        PrecipitationUnit.entries.forEach { assertTrue(it.fromMillimetres(Double.NaN).isNaN()) }
        assertTrue(WindSpeedUnit.Knots.fromKmh(-1.0).isNaN())
        assertTrue(PressureUnit.Hectopascals.fromHpa(0.0).isNaN())
        assertEquals(0.0, PrecipitationUnit.Metric.fromMillimetres(0.0), 0.0)
    }
    @Test fun formattingIsLocaleAwareAndAvoidsNegativeZero() {
        assertEquals("1,5", weatherUnitNumber(1.5, 1))
        assertEquals("1.5", weatherUnitNumber(1.5, 1, Locale.US))
        assertEquals("0,0", weatherUnitNumber(-0.001, 1))
        assertEquals("—", weatherUnitNumber(Double.NaN, 1))
    }
    @Test fun aqiRequestsSeparateSourceVariables() {
        assertEquals("european_aqi", AirQualityScale.European.apiVariable)
        assertEquals("us_aqi", AirQualityScale.UnitedStates.apiVariable)
        assertNotEquals(AirQualityScale.European.apiVariable, AirQualityScale.UnitedStates.apiVariable)
    }
    @Test fun defaultsPreserveMetricDisplay() {
        val u = WeatherUnits()
        assertEquals(TemperatureUnit.Celsius, u.temperature)
        assertEquals(WindSpeedUnit.KilometresPerHour, u.wind)
        assertEquals(PressureUnit.Hectopascals, u.pressure)
        assertEquals(PrecipitationUnit.Metric, u.precipitation)
        assertEquals(AirQualityScale.European, u.airQuality)
    }
}
