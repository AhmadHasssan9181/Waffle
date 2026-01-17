package com.noobdev.waffle.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ============================================
// WAFFLE TYPOGRAPHY SYSTEM
// Clear hierarchy for disaster/emergency contexts
// ============================================

// Using system fonts for reliability, can be replaced with custom fonts
val WaffleFont = FontFamily.Default

val Typography =
        Typography(
                // === Display - Hero text, large headers ===
                displayLarge =
                        TextStyle(
                                fontFamily = WaffleFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 57.sp,
                                lineHeight = 64.sp,
                                letterSpacing = (-0.25).sp
                        ),
                displayMedium =
                        TextStyle(
                                fontFamily = WaffleFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 45.sp,
                                lineHeight = 52.sp,
                                letterSpacing = 0.sp
                        ),
                displaySmall =
                        TextStyle(
                                fontFamily = WaffleFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 36.sp,
                                lineHeight = 44.sp,
                                letterSpacing = 0.sp
                        ),

                // === Headlines - Section headers ===
                headlineLarge =
                        TextStyle(
                                fontFamily = WaffleFont,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 32.sp,
                                lineHeight = 40.sp,
                                letterSpacing = 0.sp
                        ),
                headlineMedium =
                        TextStyle(
                                fontFamily = WaffleFont,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 28.sp,
                                lineHeight = 36.sp,
                                letterSpacing = 0.sp
                        ),
                headlineSmall =
                        TextStyle(
                                fontFamily = WaffleFont,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 24.sp,
                                lineHeight = 32.sp,
                                letterSpacing = 0.sp
                        ),

                // === Titles - Card titles, dialog titles ===
                titleLarge =
                        TextStyle(
                                fontFamily = WaffleFont,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 22.sp,
                                lineHeight = 28.sp,
                                letterSpacing = 0.sp
                        ),
                titleMedium =
                        TextStyle(
                                fontFamily = WaffleFont,
                                fontWeight = FontWeight.Medium,
                                fontSize = 16.sp,
                                lineHeight = 24.sp,
                                letterSpacing = 0.15.sp
                        ),
                titleSmall =
                        TextStyle(
                                fontFamily = WaffleFont,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                letterSpacing = 0.1.sp
                        ),

                // === Body - Main content ===
                bodyLarge =
                        TextStyle(
                                fontFamily = WaffleFont,
                                fontWeight = FontWeight.Normal,
                                fontSize = 16.sp,
                                lineHeight = 24.sp,
                                letterSpacing = 0.5.sp
                        ),
                bodyMedium =
                        TextStyle(
                                fontFamily = WaffleFont,
                                fontWeight = FontWeight.Normal,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                letterSpacing = 0.25.sp
                        ),
                bodySmall =
                        TextStyle(
                                fontFamily = WaffleFont,
                                fontWeight = FontWeight.Normal,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                letterSpacing = 0.4.sp
                        ),

                // === Labels - Buttons, chips, captions ===
                labelLarge =
                        TextStyle(
                                fontFamily = WaffleFont,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                letterSpacing = 0.1.sp
                        ),
                labelMedium =
                        TextStyle(
                                fontFamily = WaffleFont,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                letterSpacing = 0.5.sp
                        ),
                labelSmall =
                        TextStyle(
                                fontFamily = WaffleFont,
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                letterSpacing = 0.5.sp
                        )
        )

// ============================================
// CUSTOM TEXT STYLES FOR WAFFLE
// ============================================

object WaffleTextStyles {
    // Emergency alert - Large, bold, attention-grabbing
    val emergencyAlert =
            TextStyle(
                    fontFamily = WaffleFont,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    lineHeight = 28.sp,
                    letterSpacing = 1.sp
            )

    // Status badge text
    val statusBadge =
            TextStyle(
                    fontFamily = WaffleFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    letterSpacing = 0.5.sp
            )

    // Metric value (large numbers)
    val metricValue =
            TextStyle(
                    fontFamily = WaffleFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 40.sp,
                    lineHeight = 48.sp,
                    letterSpacing = (-1).sp
            )

    // Metric label
    val metricLabel =
            TextStyle(
                    fontFamily = WaffleFont,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    letterSpacing = 0.5.sp
            )

    // Timestamp
    val timestamp =
            TextStyle(
                    fontFamily = WaffleFont,
                    fontWeight = FontWeight.Normal,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    letterSpacing = 0.25.sp
            )

    // Coordinates
    val coordinates =
            TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Normal,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    letterSpacing = 0.sp
            )
}
