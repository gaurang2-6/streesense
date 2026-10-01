package com.stresssense.app.presentation.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Reusable gradient brush definitions for StressSense.
 * Provides calming, diagonal gradient backgrounds and accent gradients.
 */
object StressSenseGradients {

    /** Main calming background gradient — lavender to teal to mint, diagonal */
    val backgroundGradient = Brush.linearGradient(
        colors = listOf(GradientStart, GradientMid, GradientEnd),
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    /** Subtle background gradient for cards and surfaces */
    val surfaceGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFFF8F9FF),
            Color(0xFFF0F5FF),
            Color(0xFFF5FBF7)
        ),
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    /** Primary button gradient */
    val buttonGradient = Brush.linearGradient(
        colors = listOf(AccentBlue, AccentPurple),
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, 0f)
    )

    /** Scan button gradient — more vibrant */
    val scanButtonGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFF7B9FFF),
            Color(0xFF9B8FFF),
            Color(0xFFA78BFA)
        ),
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    /** Stress level gradient — green to amber to coral */
    val stressGradient = Brush.linearGradient(
        colors = listOf(LowStress, MidStress, HighStress)
    )

    /** Low stress gauge gradient */
    val lowStressGradient = Brush.sweepGradient(
        colors = listOf(
            Color(0xFF6EC6A0),
            Color(0xFF7FD1AE),
            Color(0xFF6EC6A0)
        )
    )

    /** Medium stress gauge gradient */
    val midStressGradient = Brush.sweepGradient(
        colors = listOf(
            Color(0xFFFFD89B),
            Color(0xFFFFCC70),
            Color(0xFFFFD89B)
        )
    )

    /** High stress gauge gradient */
    val highStressGradient = Brush.sweepGradient(
        colors = listOf(
            Color(0xFFFF9AA2),
            Color(0xFFE87F8A),
            Color(0xFFFF9AA2)
        )
    )

    /** Splash screen gradient */
    val splashGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFB8C6FF),
            Color(0xFFC4E0E5),
            Color(0xFFD4F1DD),
            Color(0xFFE8F8ED)
        )
    )

    /** Onboarding page gradient */
    val onboardingGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFF0F3FF),
            Color(0xFFF5FAFB),
            Color(0xFFFAFBFF)
        )
    )

    /** Returns stress color based on score (0-100) */
    fun stressColor(score: Int): Color = when {
        score <= 25 -> LowStress
        score <= 40 -> Color(0xFFA8DFC4) // Blend
        score <= 55 -> MidStress
        score <= 75 -> Color(0xFFFFB88C) // Blend
        else -> HighStress
    }

    /** Returns stress label based on score (0-100) */
    fun stressLabel(score: Int): String = when {
        score <= 25 -> "Low Stress"
        score <= 40 -> "Mild Stress"
        score <= 55 -> "Moderate Stress"
        score <= 75 -> "High Stress"
        else -> "Very High Stress"
    }
}
