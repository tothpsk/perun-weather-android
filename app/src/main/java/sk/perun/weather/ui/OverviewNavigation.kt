package sk.perun.weather.ui

import androidx.compose.runtime.staticCompositionLocalOf

internal data class OverviewNavigation(
    val openForecast: (String) -> Unit,
    val openAlerts: () -> Unit,
    val openSunMoon: () -> Unit = {},
    val openMarine: (String) -> Unit = {},
    val openAir: (String) -> Unit = {},
)
internal val LocalOverviewNavigation = staticCompositionLocalOf { OverviewNavigation({}, {}) }
