package com.stresssense.app.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stresssense.app.presentation.theme.*

/**
 * Animated circular progress indicator for scan steps.
 *
 * Displays:
 * - Animated arc that fills as the scan progresses
 * - Percentage or timer text in center
 * - Soft pulsing glow effect
 */
@OptIn(ExperimentalTextApi::class)
@Composable
fun ScanProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
    strokeWidth: Dp = 10.dp,
    progressColor: Color = AccentBlue,
    trackColor: Color = DividerColor,
    centerText: String = "",
    subText: String = ""
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(300, easing = LinearEasing),
        label = "scanProgress"
    )

    // Subtle glow pulsing
    val infiniteTransition = rememberInfiniteTransition(label = "scanGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanGlowAlpha"
    )

    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier.size(size)) {
        val canvasSize = this.size
        val center = Offset(canvasSize.width / 2, canvasSize.height / 2)
        val radius = (canvasSize.minDimension / 2) - strokeWidth.toPx()

        // Background track (full circle)
        drawArc(
            color = trackColor,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        )

        // Glow effect behind progress
        if (animatedProgress > 0f) {
            drawArc(
                color = progressColor.copy(alpha = glowAlpha * 0.3f),
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(
                    width = strokeWidth.toPx() + 10.dp.toPx(),
                    cap = StrokeCap.Round
                )
            )
        }

        // Progress arc
        drawArc(
            color = progressColor,
            startAngle = -90f,
            sweepAngle = 360f * animatedProgress,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        )

        // Center text
        if (centerText.isNotEmpty()) {
            val mainStyle = TextStyle(
                fontSize = (canvasSize.minDimension * 0.16f).sp,
                color = TextPrimary,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Light
            )
            val mainLayout = textMeasurer.measure(centerText, mainStyle)
            drawText(
                textLayoutResult = mainLayout,
                topLeft = Offset(
                    center.x - mainLayout.size.width / 2,
                    center.y - mainLayout.size.height / 2 -
                            if (subText.isNotEmpty()) 8.dp.toPx() else 0f
                )
            )
        }

        // Sub text
        if (subText.isNotEmpty()) {
            val subStyle = TextStyle(
                fontSize = (canvasSize.minDimension * 0.07f).sp,
                color = TextSecondary
            )
            val subLayout = textMeasurer.measure(subText, subStyle)
            drawText(
                textLayoutResult = subLayout,
                topLeft = Offset(
                    center.x - subLayout.size.width / 2,
                    center.y + 8.dp.toPx()
                )
            )
        }
    }
}
