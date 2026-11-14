package sk.perun.weather.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val PerunBg = Color(0xFF1E2129)
val PerunPanel = Color(0xFF262B36)
val PerunDeep = Color(0xFF1A1D24)
val PerunBorder = Color(0xFF414958)
val PerunText = Color(0xFFE8EAF0)
val PerunMuted = Color(0xFFBBC4D2)
val PerunAccent = Color(0xFF4FC3F7)

private val PerunColors = darkColorScheme(
    primary = PerunAccent,
    background = PerunBg,
    surface = PerunPanel,
    surfaceVariant = PerunDeep,
    outline = PerunBorder,
    onBackground = PerunText,
    onSurface = PerunText,
    onSurfaceVariant = PerunMuted,
    secondaryContainer = Color(0xFF254459),
    onSecondaryContainer = Color(0xFFBCECFF),
)

@Composable
fun PerunTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PerunColors, content = content)
}
