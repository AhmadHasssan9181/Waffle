package com.noobdev.waffle.design.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.noobdev.waffle.R

// ============================================
// TYPOGRAPHY
// Manrope (SIL Open Font License). Static instances are generated from the variable font so
// every weight renders identically on API 24+ instead of relying on variable-font support.
// ============================================

val WaffleFont =
        FontFamily(
                Font(R.font.manrope_regular, FontWeight.Normal),
                Font(R.font.manrope_medium, FontWeight.Medium),
                Font(R.font.manrope_semibold, FontWeight.SemiBold),
                Font(R.font.manrope_bold, FontWeight.Bold),
                Font(R.font.manrope_extrabold, FontWeight.ExtraBold)
        )

val Typography =
        Typography(
                displayLarge =
                        TextStyle(
                                fontFamily = WaffleFont,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 48.sp,
                                lineHeight = 54.sp,
                                letterSpacing = (-1.2).sp
                        ),
                displayMedium =
                        TextStyle(
                                fontFamily = WaffleFont,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 40.sp,
                                lineHeight = 46.sp,
                                letterSpacing = (-1).sp
                        ),
                displaySmall =
                        TextStyle(
                                fontFamily = WaffleFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 32.sp,
                                lineHeight = 38.sp,
                                letterSpacing = (-0.8).sp
                        ),
                headlineLarge =
                        TextStyle(
                                fontFamily = WaffleFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 28.sp,
                                lineHeight = 34.sp,
                                letterSpacing = (-0.6).sp
                        ),
                headlineMedium =
                        TextStyle(
                                fontFamily = WaffleFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp,
                                lineHeight = 30.sp,
                                letterSpacing = (-0.4).sp
                        ),
                headlineSmall =
                        TextStyle(
                                fontFamily = WaffleFont,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 20.sp,
                                lineHeight = 26.sp,
                                letterSpacing = (-0.3).sp
                        ),
                titleLarge =
                        TextStyle(
                                fontFamily = WaffleFont,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp,
                                lineHeight = 24.sp,
                                letterSpacing = (-0.2).sp
                        ),
                titleMedium =
                        TextStyle(
                                fontFamily = WaffleFont,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                lineHeight = 21.sp,
                                letterSpacing = (-0.1).sp
                        ),
                titleSmall =
                        TextStyle(
                                fontFamily = WaffleFont,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                letterSpacing = 0.sp
                        ),
                bodyLarge =
                        TextStyle(
                                fontFamily = WaffleFont,
                                fontWeight = FontWeight.Normal,
                                fontSize = 16.sp,
                                lineHeight = 24.sp,
                                letterSpacing = 0.sp
                        ),
                bodyMedium =
                        TextStyle(
                                fontFamily = WaffleFont,
                                fontWeight = FontWeight.Normal,
                                fontSize = 14.sp,
                                lineHeight = 21.sp,
                                letterSpacing = 0.sp
                        ),
                bodySmall =
                        TextStyle(
                                fontFamily = WaffleFont,
                                fontWeight = FontWeight.Normal,
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp,
                                letterSpacing = 0.1.sp
                        ),
                labelLarge =
                        TextStyle(
                                fontFamily = WaffleFont,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                lineHeight = 18.sp,
                                letterSpacing = 0.1.sp
                        ),
                labelMedium =
                        TextStyle(
                                fontFamily = WaffleFont,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                letterSpacing = 0.3.sp
                        ),
                labelSmall =
                        TextStyle(
                                fontFamily = WaffleFont,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                letterSpacing = 0.4.sp
                        )
        )

// ============================================
// CUSTOM TEXT STYLES
// ============================================

object WaffleTextStyles {
    val emergencyAlert =
            TextStyle(
                    fontFamily = WaffleFont,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    lineHeight = 26.sp,
                    letterSpacing = 0.2.sp
            )

    val statusBadge =
            TextStyle(
                    fontFamily = WaffleFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    letterSpacing = 0.8.sp
            )

    val metricValue =
            TextStyle(
                    fontFamily = WaffleFont,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 32.sp,
                    lineHeight = 36.sp,
                    letterSpacing = (-1).sp
            )

    val metricLabel =
            TextStyle(
                    fontFamily = WaffleFont,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    letterSpacing = 0.2.sp
            )

    val timestamp =
            TextStyle(
                    fontFamily = WaffleFont,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    letterSpacing = 0.2.sp
            )

    val coordinates =
            TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Normal,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    letterSpacing = 0.sp
            )

    /** Small uppercase eyebrow above a section, e.g. "NEAR YOU". */
    val eyebrow =
            TextStyle(
                    fontFamily = WaffleFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    letterSpacing = 1.4.sp
            )
}
