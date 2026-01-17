package com.noobdev.waffle.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================
// WAFFLE APP COLOR SYSTEM
// Dark mode focused with emergency/disaster semantics
// ============================================

// === Core Brand Colors ===
val WafflePrimary = Color(0xFF3B82F6) // Electric Blue - main actions
val WafflePrimaryVariant = Color(0xFF2563EB) // Darker blue for emphasis
val WaffleSecondary = Color(0xFF10B981) // Emerald - secondary actions
val WaffleSecondaryVariant = Color(0xFF059669)

// === Semantic Colors - Disaster States ===
val DangerRed = Color(0xFFEF4444) // Critical alerts, active disasters
val DangerRedDark = Color(0xFFDC2626)
val WarningOrange = Color(0xFFF59E0B) // Warnings, caution areas
val WarningOrangeDark = Color(0xFFD97706)
val SafeGreen = Color(0xFF22C55E) // Safe zones, all clear
val SafeGreenDark = Color(0xFF16A34A)
val InfoBlue = Color(0xFF0EA5E9) // Information, updates
val InfoBlueDark = Color(0xFF0284C7)

// === Flood Depth Colors (for heatmaps) ===
val FloodLow = Color(0xFF93C5FD) // < 0.5m - Light blue
val FloodMedium = Color(0xFF3B82F6) // 0.5-1m - Medium blue
val FloodHigh = Color(0xFF1D4ED8) // 1-2m - Dark blue
val FloodSevere = Color(0xFF7C3AED) // 2-3m - Purple
val FloodCritical = Color(0xFFDC2626) // > 3m - Red

// === Dark Theme Base Colors ===
val DarkBackground = Color(0xFF0F172A) // Slate 900 - main background
val DarkSurface = Color(0xFF1E293B) // Slate 800 - cards/panels
val DarkSurfaceVariant = Color(0xFF334155) // Slate 700 - elevated surfaces
val DarkBorder = Color(0xFF475569) // Slate 600 - borders

// === Text Colors (Dark Theme) ===
val TextPrimary = Color(0xFFF8FAFC) // Slate 50 - main text
val TextSecondary = Color(0xFF94A3B8) // Slate 400 - secondary text
val TextMuted = Color(0xFF64748B) // Slate 500 - muted/disabled
val TextOnPrimary = Color(0xFFFFFFFF) // White on primary buttons

// === Light Theme Base Colors ===
val LightBackground = Color(0xFFF8FAFC) // Slate 50
val LightSurface = Color(0xFFFFFFFF) // Pure white
val LightSurfaceVariant = Color(0xFFF1F5F9) // Slate 100
val LightBorder = Color(0xFFE2E8F0) // Slate 200

// === Text Colors (Light Theme) ===
val LightTextPrimary = Color(0xFF0F172A) // Slate 900
val LightTextSecondary = Color(0xFF475569) // Slate 600
val LightTextMuted = Color(0xFF94A3B8) // Slate 400

// === Disaster Type Accent Colors ===
val EarthquakeColor = Color(0xFFD97706) // Amber
val FloodColor = Color(0xFF0284C7) // Sky blue
val WildfireColor = Color(0xFFDC2626) // Red
val StormColor = Color(0xFF7C3AED) // Purple

// === Status Badge Colors ===
val StatusActive = Color(0xFFEF4444) // Active emergency
val StatusWarning = Color(0xFFF59E0B) // Warning
val StatusMonitoring = Color(0xFF3B82F6) // Under monitoring
val StatusResolved = Color(0xFF22C55E) // Resolved

// ============================================
// THEME COLOR SCHEMES
// ============================================

// Ocean Theme
val OceanPrimary = Color(0xFF0891B2)
val OceanSecondary = Color(0xFF06B6D4)
val OceanBackground = Color(0xFF0C1929)
val OceanSurface = Color(0xFF132338)

// Forest Theme
val ForestPrimary = Color(0xFF059669)
val ForestSecondary = Color(0xFF10B981)
val ForestBackground = Color(0xFF0A1F0F)
val ForestSurface = Color(0xFF14321A)

// Sunset Theme
val SunsetPrimary = Color(0xFFF97316)
val SunsetSecondary = Color(0xFFFB923C)
val SunsetBackground = Color(0xFF1C1410)
val SunsetSurface = Color(0xFF2E2118)

// Midnight Theme
val MidnightPrimary = Color(0xFF8B5CF6)
val MidnightSecondary = Color(0xFFA78BFA)
val MidnightBackground = Color(0xFF0A0A14)
val MidnightSurface = Color(0xFF14141F)

// Emergency Theme (High Contrast)
val EmergencyPrimary = Color(0xFFFF0000)
val EmergencySecondary = Color(0xFFFFFFFF)
val EmergencyBackground = Color(0xFF1A0000)
val EmergencySurface = Color(0xFF2D0000)
