package sk.perun.weather.ui

import sk.perun.weather.R

import java.util.Locale

// Input values remain in canonical API units. Convert only at presentation time.
internal enum class TemperatureUnit(val symbol: String) {
    Celsius("°C"), Fahrenheit("°F");
    fun fromCelsius(value: Double): Double = when {
        !value.isFinite() -> Double.NaN
        this == Fahrenheit -> value * 1.8 + 32.0
        else -> value
    }
}

internal enum class WindSpeedUnit(val symbol: String, private val divisor: Double) {
    KilometresPerHour("km/h", 1.0), MetresPerSecond("m/s", 3.6),
    MilesPerHour("mph", 1.609344), Knots("kn", 1.852);
    fun fromKmh(value: Double): Double = if (value.isFinite() && value >= 0.0) value / divisor else Double.NaN
}

internal enum class PressureUnit(val symbol: String, private val hpaPerUnit: Double) {
    Hectopascals("hPa", 1.0), MillimetresMercury("mmHg", 1.33322387415), InchesMercury("inHg", 33.8638866667);
    fun fromHpa(value: Double): Double = if (value.isFinite() && value > 0.0) value / hpaPerUnit else Double.NaN
}

internal enum class PrecipitationUnit(val liquidSymbol: String, val snowSymbol: String) {
    Metric("mm", "cm"), Imperial("in", "in");
    fun fromMillimetres(value: Double): Double = when {
        !value.isFinite() || value < 0.0 -> Double.NaN
        this == Imperial -> value / 25.4
        else -> value
    }
    fun fromSnowCentimetres(value: Double): Double = when {
        !value.isFinite() || value < 0.0 -> Double.NaN
        this == Imperial -> value / 2.54
        else -> value
    }
}

// AQI scales require separate source series, not a numeric unit conversion.
internal enum class AirQualityScale(val apiVariable: String, val label: String) {
    European("european_aqi", uiText(R.string.ui_eu_aqi_3234eb6d)), UnitedStates("us_aqi", uiText(R.string.ui_us_aqi_924055e5))
}

internal data class WeatherUnits(
    val temperature: TemperatureUnit = TemperatureUnit.Celsius,
    val wind: WindSpeedUnit = WindSpeedUnit.KilometresPerHour,
    val pressure: PressureUnit = PressureUnit.Hectopascals,
    val precipitation: PrecipitationUnit = PrecipitationUnit.Metric,
    val airQuality: AirQualityScale = AirQualityScale.European,
)

internal fun weatherUnitNumber(value: Double, decimals: Int, locale: Locale = appLocale()): String {
    require(decimals in 0..4)
    if (!value.isFinite()) return "—"
    val formatted = String.format(locale, "%.${decimals}f", value)
    return if (formatted.startsWith("-") && formatted.drop(1).all { it == '0' || it == '.' || it == ',' }) formatted.drop(1) else formatted
}
