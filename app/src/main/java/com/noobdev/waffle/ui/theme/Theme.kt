package com.noobdev.waffle.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ============================================
// APP THEME ENUM
// ============================================
enum class AppTheme {
    DEFAULT,
    OCEAN,
    FOREST,
    SUNSET,
    MIDNIGHT,
    EMERGENCY
}

// ============================================
// DARK COLOR SCHEMES
// ============================================

private val DefaultDarkColorScheme =
        darkColorScheme(
                primary = WafflePrimary,
                onPrimary = TextOnPrimary,
                primaryContainer = WafflePrimaryVariant,
                secondary = WaffleSecondary,
                onSecondary = TextOnPrimary,
                secondaryContainer = WaffleSecondaryVariant,
                tertiary = InfoBlue,
                background = DarkBackground,
                onBackground = TextPrimary,
                surface = DarkSurface,
                onSurface = TextPrimary,
                surfaceVariant = DarkSurfaceVariant,
                onSurfaceVariant = TextSecondary,
                error = DangerRed,
                onError = TextOnPrimary,
                outline = DarkBorder
        )

private val OceanDarkColorScheme =
        darkColorScheme(
                primary = OceanPrimary,
                onPrimary = TextOnPrimary,
                primaryContainer = Color(0xFF065266),
                secondary = OceanSecondary,
                onSecondary = TextOnPrimary,
                background = OceanBackground,
                onBackground = TextPrimary,
                surface = OceanSurface,
                onSurface = TextPrimary,
                error = DangerRed,
                onError = TextOnPrimary
        )

private val ForestDarkColorScheme =
        darkColorScheme(
                primary = ForestPrimary,
                onPrimary = TextOnPrimary,
                primaryContainer = Color(0xFF047857),
                secondary = ForestSecondary,
                onSecondary = TextOnPrimary,
                background = ForestBackground,
                onBackground = TextPrimary,
                surface = ForestSurface,
                onSurface = TextPrimary,
                error = DangerRed,
                onError = TextOnPrimary
        )

private val SunsetDarkColorScheme =
        darkColorScheme(
                primary = SunsetPrimary,
                onPrimary = TextOnPrimary,
                primaryContainer = Color(0xFFEA580C),
                secondary = SunsetSecondary,
                onSecondary = TextOnPrimary,
                background = SunsetBackground,
                onBackground = TextPrimary,
                surface = SunsetSurface,
                onSurface = TextPrimary,
                error = DangerRed,
                onError = TextOnPrimary
        )

private val MidnightDarkColorScheme =
        darkColorScheme(
                primary = MidnightPrimary,
                onPrimary = TextOnPrimary,
                primaryContainer = Color(0xFF7C3AED),
                secondary = MidnightSecondary,
                onSecondary = TextOnPrimary,
                background = MidnightBackground,
                onBackground = TextPrimary,
                surface = MidnightSurface,
                onSurface = TextPrimary,
                error = DangerRed,
                onError = TextOnPrimary
        )

private val EmergencyDarkColorScheme =
        darkColorScheme(
                primary = EmergencyPrimary,
                onPrimary = TextOnPrimary,
                primaryContainer = Color(0xFFCC0000),
                secondary = EmergencySecondary,
                onSecondary = Color.Black,
                background = EmergencyBackground,
                onBackground = TextPrimary,
                surface = EmergencySurface,
                onSurface = TextPrimary,
                error = Color.Yellow,
                onError = Color.Black
        )

// ============================================
// LIGHT COLOR SCHEMES
// ============================================

private val DefaultLightColorScheme =
        lightColorScheme(
                primary = WafflePrimary,
                onPrimary = TextOnPrimary,
                primaryContainer = Color(0xFFDBEAFE),
                secondary = WaffleSecondary,
                onSecondary = TextOnPrimary,
                secondaryContainer = Color(0xFFD1FAE5),
                background = LightBackground,
                onBackground = LightTextPrimary,
                surface = LightSurface,
                onSurface = LightTextPrimary,
                surfaceVariant = LightSurfaceVariant,
                onSurfaceVariant = LightTextSecondary,
                error = DangerRed,
                onError = TextOnPrimary,
                outline = LightBorder
        )

// ============================================
// MAIN THEME COMPOSABLE
// ============================================

@Composable
fun WaffleTheme(
        appTheme: AppTheme = AppTheme.DEFAULT,
        darkTheme: Boolean = true, // Default to dark mode
        dynamicColor: Boolean = false, // Disabled by default for consistent branding
        content: @Composable () -> Unit
) {
    val colorScheme =
            when {
                dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    val context = LocalContext.current
                    if (darkTheme) dynamicDarkColorScheme(context)
                    else dynamicLightColorScheme(context)
                }
                darkTheme ->
                        when (appTheme) {
                            AppTheme.DEFAULT -> DefaultDarkColorScheme
                            AppTheme.OCEAN -> OceanDarkColorScheme
                            AppTheme.FOREST -> ForestDarkColorScheme
                            AppTheme.SUNSET -> SunsetDarkColorScheme
                            AppTheme.MIDNIGHT -> MidnightDarkColorScheme
                            AppTheme.EMERGENCY -> EmergencyDarkColorScheme
                        }
                else -> DefaultLightColorScheme // Light themes can be expanded later
            }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

// ============================================
// SEMANTIC COLOR ACCESSORS
// ============================================

object WaffleColors {
    val danger
        @Composable get() = DangerRed
    val dangerDark
        @Composable get() = DangerRedDark
    val warning
        @Composable get() = WarningOrange
    val warningDark
        @Composable get() = WarningOrangeDark
    val safe
        @Composable get() = SafeGreen
    val safeDark
        @Composable get() = SafeGreenDark
    val info
        @Composable get() = InfoBlue
    val infoDark
        @Composable get() = InfoBlueDark

    // Flood depth colors
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

    // Disaster type colors
    val earthquake
        @Composable get() = EarthquakeColor
    val flood
        @Composable get() = FloodColor
    val wildfire
        @Composable get() = WildfireColor
    val storm
        @Composable get() = StormColor

    // Status colors
    val statusActive
        @Composable get() = StatusActive
    val statusWarning
        @Composable get() = StatusWarning
    val statusMonitoring
        @Composable get() = StatusMonitoring
    val statusResolved
        @Composable get() = StatusResolved
}
