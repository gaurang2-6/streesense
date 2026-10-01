package com.stresssense.app.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stresssense.app.presentation.theme.*

/**
 * Animated circular gauge displaying stress score with a color-coded ring.
 *
 * Features:
 * - Smooth fill animation on appearance
 * - Color transitions based on stress level (green → amber → coral)
 * - Score number displayed in center
 * - Optional label text below score
 */
@OptIn(ExperimentalTextApi::class)
@Composable
fun StressGauge(
    score: Int,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    strokeWidth: Dp = 14.dp,
    animationDuration: Int = 1200,
    showLabel: Boolean = true
) {
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(score) {
        animatedProgress.snapTo(0f)
        animatedProgress.animateTo(
            targetValue = score / 100f,
            animationSpec = tween(
                durationMillis = animationDuration,
                easing = FastOutSlowInEasing
            )
        )
    }

    // Pulsing glow effect for the score
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    val stressColor = getStressColor(score)
    val stressGradient = getStressGradient(score)
    val label = getStressLabel(score)

    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier.size(size)) {
        val canvasSize = this.size
        val center = Offset(canvasSize.width / 2, canvasSize.height / 2)
        val radius = (canvasSize.minDimension / 2) - strokeWidth.toPx()

        // Background track
        drawArc(
            color = DividerColor,
            startAngle = 135f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        )

        // Glow behind the progress arc
        drawArc(
            brush = stressGradient,
            startAngle = 135f,
            sweepAngle = 270f * animatedProgress.value,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(
                width = strokeWidth.toPx() + 8.dp.toPx(),
                cap = StrokeCap.Round
            ),
            alpha = glowAlpha * 0.4f
        )

        // Progress arc with gradient
        drawArc(
            brush = stressGradient,
            startAngle = 135f,
            sweepAngle = 270f * animatedProgress.value,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        )

        // Score text in center
        val scoreText = "${(animatedProgress.value * 100).toInt()}"
        val scoreStyle = TextStyle(
            fontSize = (canvasSize.minDimension * 0.22f).sp,
            color = TextPrimary,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Light
        )
        val scoreLayout = textMeasurer.measure(scoreText, scoreStyle)
        drawText(
            textLayoutResult = scoreLayout,
            topLeft = Offset(
                center.x - scoreLayout.size.width / 2,
                center.y - scoreLayout.size.height / 2 - 10.dp.toPx()
            )
        )

        // Label text below score
        if (showLabel) {
            val labelStyle = TextStyle(
                fontSize = (canvasSize.minDimension * 0.08f).sp,
                color = TextSecondary
            )
            val labelLayout = textMeasurer.measure(label, labelStyle)
            drawText(
                textLayoutResult = labelLayout,
                topLeft = Offset(
                    center.x - labelLayout.size.width / 2,
                    center.y + scoreLayout.size.height / 2 - 4.dp.toPx()
                )
            )
        }
    }
}

/**
 * Get the appropriate stress color based on score.
 */
fun getStressColor(score: Int): Color {
    return when {
        score <= 30 -> LowStress
        score <= 60 -> MidStress
        else -> HighStress
    }
}

/**
 * Get a gradient brush for the stress gauge arc.
 */
fun getStressGradient(score: Int): Brush {
    return when {
        score <= 30 -> Brush.sweepGradient(
            colors = listOf(VeryLowStress, LowStress, LowStress)
        )
        score <= 60 -> Brush.sweepGradient(
            colors = listOf(LowStress, MidStress, MidStress)
        )
        else -> Brush.sweepGradient(
            colors = listOf(MidStress, HighStress, VeryHighStress)
        )
    }
}

/**
 * Get the human-readable stress label for a score.
 */
fun getStressLabel(score: Int): String {
    return when {
        score <= 25 -> "Very Low Stress"
        score <= 40 -> "Low Stress"
        score <= 55 -> "Moderate Stress"
        score <= 75 -> "High Stress"
        else -> "Very High Stress"
    }
}
