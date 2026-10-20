package com.noobdev.waffle.design.theme

import androidx.compose.ui.graphics.Color

// ============================================
// WAFFLE PALETTE
// Two hues and a lot of warm neutral. Deliberately no rainbow: amber is the brand, sky is the
// only "live/technical" accent, and red/green exist solely to mean danger/safe.
// ============================================

// === Brand ===
val Honey = Color(0xFFF2A33C) // primary action, the waffle
val HoneyDeep = Color(0xFFC9761B) // primary on light backgrounds (AA contrast)
val HoneySoft = Color(0x33F2A33C) // 20% wash for icon tiles
val Sky = Color(0xFF4FB3D9) // GPS / live / offline-ready accent, used sparingly
val SkyDeep = Color(0xFF1B7A9C)
val SkySoft = Color(0x264FB3D9)

// === Neutrals, dark ===
val Ink = Color(0xFF0B0F16) // app background
val InkSurface = Color(0xFF141A24) // cards
val InkElevated = Color(0xFF1B2330) // sheets, dialogs
val InkOutline = Color(0xFF2A3444)
val InkMuted = Color(0xFF6C7787)

// === Neutrals, light ===
val Cream = Color(0xFFFBF7F0) // app background
val CreamSurface = Color(0xFFFFFFFF) // cards
val CreamElevated = Color(0xFFFFFDF9)
val CreamOutline = Color(0xFFE7DCC9)
val Sand = Color(0xFFF3EBDD)

// === Text ===
val TextOnDark = Color(0xFFF4F1EA)
val TextOnDarkMuted = Color(0xFF9AA4B2)
val TextOnLight = Color(0xFF14181F)
val TextOnLightMuted = Color(0xFF6A6F78)
val TextOnHoney = Color(0xFF231607)

// === Status (semantic only — never decorative) ===
val DangerRed = Color(0xFFE5484D)
val DangerDeep = Color(0xFFB9353A)
val WarningOrange = Color(0xFFF2A33C)
val SafeGreen = Color(0xFF3FA37A)
val SafeDeep = Color(0xFF2C7C5B)
val InfoBlue = Color(0xFF4FB3D9)

// ============================================
// BACKWARDS-COMPATIBLE ALIASES
// Existing screens reference these names. They now resolve to the brand palette, so the old
// rainbow look disappears without touching every call site at once.
// ============================================

val WafflePrimary = Honey
val WafflePrimaryVariant = HoneyDeep
val WaffleSecondary = Sky
val WaffleSecondaryVariant = SkyDeep

val DangerRedDark = DangerDeep
val WarningOrangeDark = Color(0xFFC9761B)
val SafeGreenDark = SafeDeep
val InfoBlueDark = SkyDeep

val DarkBackground = Ink
val DarkSurface = InkSurface
val DarkSurfaceVariant = InkElevated
val DarkBorder = InkOutline

val TextPrimary = TextOnDark
val TextSecondary = TextOnDarkMuted
val TextMuted = InkMuted
val TextOnPrimary = TextOnHoney

val LightBackground = Cream
val LightSurface = CreamSurface
val LightSurfaceVariant = Sand
val LightBorder = CreamOutline

val LightTextPrimary = TextOnLight
val LightTextSecondary = TextOnLightMuted
val LightTextMuted = Color(0xFF9A9EA6)

// Flood depth ramp. One hue, four steps, red only at the top.
val FloodLow = Color(0xFFBFE3F0)
val FloodMedium = Color(0xFF7CC6DF)
val FloodHigh = Color(0xFF3D9DBF)
val FloodSevere = Color(0xFF1B6E8C)
val FloodCritical = DangerRed

// Disaster accents collapse to three hues plus neutral.
val EarthquakeColor = Honey
val FloodColor = Sky
val WildfireColor = DangerRed
val StormColor = Color(0xFF8C97A8)

val StatusActive = DangerRed
val StatusWarning = WarningOrange
val StatusMonitoring = Sky
val StatusResolved = SafeGreen

// Legacy theme names. Kept only so old preference values still compile; the theme picker no
// longer offers them and all of them resolve to the brand palette.
val OceanPrimary = SkyDeep
val OceanSecondary = Sky
val OceanBackground = Ink
val OceanSurface = InkSurface
val ForestPrimary = SafeDeep
val ForestSecondary = SafeGreen
val ForestBackground = Ink
val ForestSurface = InkSurface
val SunsetPrimary = HoneyDeep
val SunsetSecondary = Honey
val SunsetBackground = Ink
val SunsetSurface = InkSurface
val MidnightPrimary = Honey
val MidnightSecondary = Honey
val MidnightBackground = Ink
val MidnightSurface = InkSurface
val EmergencyPrimary = DangerRed
val EmergencySecondary = Color.White
val EmergencyBackground = Ink
val EmergencySurface = InkSurface
