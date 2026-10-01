package com.stresssense.app.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stresssense.app.presentation.theme.AccentBlue
import com.stresssense.app.presentation.theme.AccentPurple
import com.stresssense.app.presentation.theme.GradientStart
import kotlin.math.sin

/**
 * Animated audio waveform visualization for the voice recording step.
 *
 * Displays a smooth sine-wave that responds to audio amplitude levels.
 * Multiple overlapping waves create a rich visual effect.
 */
@Composable
fun AnimatedWaveform(
    modifier: Modifier = Modifier,
    amplitudes: List<Float> = emptyList(),
    isActive: Boolean = true,
    height: Dp = 120.dp,
    waveColor: Color = AccentBlue,
    secondaryColor: Color = GradientStart
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")

    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val breathe by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    // Smooth amplitude for idle animation
    val targetAmplitude = if (amplitudes.isNotEmpty()) {
        amplitudes.takeLast(5).average().toFloat().coerceIn(0f, 1f)
    } else if (isActive) {
        breathe * 0.6f
    } else {
        0.1f
    }

    val smoothAmplitude by animateFloatAsState(
        targetValue = targetAmplitude,
        animationSpec = tween(150),
        label = "smoothAmp"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val centerY = size.height / 2
        val width = size.width

        // Draw 3 overlapping waves for richness
        val waves = listOf(
            Triple(waveColor.copy(alpha = 0.6f), 1.0f, 0f),
            Triple(secondaryColor.copy(alpha = 0.4f), 0.7f, 0.8f),
            Triple(AccentPurple.copy(alpha = 0.3f), 0.5f, 1.6f)
        )

        for ((color, amplitudeScale, phaseOffset) in waves) {
            val path = Path()
            val amplitude = smoothAmplitude * amplitudeScale * (size.height * 0.35f)

            for (x in 0..width.toInt() step 2) {
                val xRatio = x / width
                // Envelope: stronger in the center
                val envelope = sin(xRatio * Math.PI).toFloat()
                val y = centerY + sin(
                    xRatio * 4 * Math.PI + phase + phaseOffset
                ).toFloat() * amplitude * envelope

                if (x == 0) {
                    path.moveTo(x.toFloat(), y)
                } else {
                    path.lineTo(x.toFloat(), y)
                }
            }

            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 2.5f)
            )
        }
    }
}

/**
 * Static waveform visualization for displaying recorded audio.
 */
@Composable
fun StaticWaveform(
    amplitudes: List<Float>,
    modifier: Modifier = Modifier,
    height: Dp = 80.dp,
    barColor: Color = AccentBlue,
    barWidth: Float = 3f,
    barSpacing: Float = 2f
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val centerY = size.height / 2
        val totalBars = ((size.width) / (barWidth + barSpacing)).toInt()
        val displayAmplitudes = if (amplitudes.size >= totalBars) {
            // Downsample
            amplitudes.chunked(amplitudes.size / totalBars) { chunk ->
                chunk.average().toFloat()
            }
        } else {
            amplitudes
        }

        displayAmplitudes.forEachIndexed { index, amplitude ->
            val x = index * (barWidth + barSpacing)
            val barHeight = amplitude.coerceIn(0.05f, 1f) * size.height * 0.8f

            drawLine(
                color = barColor.copy(alpha = 0.4f + amplitude * 0.6f),
                start = Offset(x, centerY - barHeight / 2),
                end = Offset(x, centerY + barHeight / 2),
                strokeWidth = barWidth
            )
        }
    }
}
