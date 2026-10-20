package com.noobdev.waffle.design.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/**
 * The app ships exactly two looks: a warm dark mode for night driving and a warm light mode for
 * daylight. "System" follows the phone.
 */
enum class AppTheme {
    SYSTEM,
    LIGHT,
    DARK
}

val WaffleShapes =
        Shapes(
                extraSmall = RoundedCornerShape(10.dp),
                small = RoundedCornerShape(14.dp),
                medium = RoundedCornerShape(18.dp),
                large = RoundedCornerShape(24.dp),
                extraLarge = RoundedCornerShape(30.dp)
        )

private val WaffleDarkScheme =
        darkColorScheme(
                primary = Honey,
                onPrimary = TextOnHoney,
                primaryContainer = Color(0xFF3A2A14),
                onPrimaryContainer = Color(0xFFFFDCA8),
                secondary = Sky,
                onSecondary = Color(0xFF04222D),
                secondaryContainer = Color(0xFF14323D),
                onSecondaryContainer = Color(0xFFBEE9F7),
                tertiary = SafeGreen,
                onTertiary = Color(0xFF04231A),
                background = Ink,
                onBackground = TextOnDark,
                surface = InkSurface,
                onSurface = TextOnDark,
                surfaceVariant = InkElevated,
                onSurfaceVariant = TextOnDarkMuted,
                surfaceContainer = InkElevated,
                surfaceContainerHigh = Color(0xFF222C3C),
                outline = InkOutline,
                outlineVariant = Color(0xFF212A38),
                error = DangerRed,
                onError = Color.White,
                errorContainer = Color(0xFF3B1518),
                onErrorContainer = Color(0xFFFFD9DA),
                scrim = Color(0xCC05080D)
        )

private val WaffleLightScheme =
        lightColorScheme(
                primary = HoneyDeep,
                onPrimary = Color.White,
                primaryContainer = Color(0xFFFFE9C9),
                onPrimaryContainer = Color(0xFF4A2C06),
                secondary = SkyDeep,
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFD5EEF8),
                onSecondaryContainer = Color(0xFF062C3A),
                tertiary = SafeDeep,
                onTertiary = Color.White,
                background = Cream,
                onBackground = TextOnLight,
                surface = CreamSurface,
                onSurface = TextOnLight,
                surfaceVariant = Sand,
                onSurfaceVariant = TextOnLightMuted,
                surfaceContainer = CreamElevated,
                surfaceContainerHigh = Color(0xFFF6F0E5),
                outline = CreamOutline,
                outlineVariant = Color(0xFFEFE6D8),
                error = DangerDeep,
                onError = Color.White,
                errorContainer = Color(0xFFFBE0E0),
                onErrorContainer = Color(0xFF5A1517),
                scrim = Color(0x66101620)
        )

@Composable
fun WaffleTheme(
        appTheme: AppTheme = AppTheme.SYSTEM,
        content: @Composable () -> Unit
) {
    val darkTheme =
            when (appTheme) {
                AppTheme.SYSTEM -> isSystemInDarkTheme()
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
            }
    val colorScheme = if (darkTheme) WaffleDarkScheme else WaffleLightScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = WaffleShapes,
            content = content
    )
}

/**
 * Semantic colours. Screens should reach for these by meaning ("this is dangerous") rather than
 * by hue, so the palette can change without touching every screen.
 */
object WaffleColors {
    val danger
        @Composable get() = DangerRed
    val dangerDark
        @Composable get() = DangerDeep
    val warning
        @Composable get() = WarningOrange
    val warningDark
        @Composable get() = WarningOrangeDark
    val safe
        @Composable get() = SafeGreen
    val safeDark
        @Composable get() = SafeDeep
    val info
        @Composable get() = Sky
    val infoDark
        @Composable get() = SkyDeep

    val floodLow
        @Composable get() = FloodLow
    val floodMedium
        @Composable get() = FloodMedium
    val floodHigh
        @Composable get() = FloodHigh
    val floodSevere
        @Composable get() = FloodSevere
    val floodCritical
        @Composable get() = FloodCritical

    val earthquake
        @Composable get() = EarthquakeColor
    val flood
        @Composable get() = FloodColor
    val wildfire
        @Composable get() = WildfireColor
    val storm
        @Composable get() = StormColor

    val statusActive
        @Composable get() = StatusActive
    val statusWarning
        @Composable get() = StatusWarning
    val statusMonitoring
        @Composable get() = StatusMonitoring
    val statusResolved
        @Composable get() = StatusResolved
}
