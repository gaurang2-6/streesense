package com.stresssense.app.presentation.ui.scan.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stresssense.app.presentation.components.AnimatedWaveform
import com.stresssense.app.presentation.components.ScanProgressIndicator
import com.stresssense.app.presentation.theme.*
import kotlinx.coroutines.delay

/**
 * Voice Scan UI — Step 2 of the scan flow.
 *
 * Shows:
 * - Instruction with the sentence to read aloud
 * - Animated waveform visualization
 * - Recording progress indicator
 *
 * The audio capture runs for a fixed 10 seconds inside the ViewModel and
 * blocks until complete, so it cannot update `progress` itself. We drive a
 * local 10s countdown here for a smooth, always-completing timer.
 */
@Composable
fun VoiceScanView(
    progress: Float,
    isRecording: Boolean,
    onStartRecording: () -> Unit
) {
    // Local 10-second countdown — guaranteed to reach 1.0.
    var elapsed by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        onStartRecording()
        while (elapsed < 1f) {
            delay(100)
            elapsed = (elapsed + 0.01f).coerceAtMost(1f)
        }
    }
    val displayProgress = if (progress > 0f) progress else elapsed

    val infiniteTransition = rememberInfiniteTransition(label = "micPulse")
    val micScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "micScale"
    )

    val secondsRemaining = ((1f - displayProgress) * 10).toInt().coerceAtLeast(0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Instruction
        Text(
            text = "Read the following aloud:",
            fontSize = 16.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Sentence to read
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground)
        ) {
            Text(
                text = "\"The quick brown fox jumps over the lazy dog\"",
                fontSize = 18.sp,
                fontWeight = FontWeight.Light,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                lineHeight = 26.sp,
                modifier = Modifier.padding(24.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Waveform visualization
        AnimatedWaveform(
            isActive = isRecording,
            height = 100.dp,
            waveColor = AccentPurple,
            secondaryColor = AccentBlue
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Progress
        ScanProgressIndicator(
            progress = displayProgress,
            size = 140.dp,
            strokeWidth = 8.dp,
            progressColor = AccentPurple,
            centerText = "${secondsRemaining}s",
            subText = if (isRecording) "Listening..." else "Starting..."
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Mic indicator
        if (isRecording) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Mic,
                    contentDescription = "Recording",
                    tint = HighStress,
                    modifier = Modifier
                        .size(20.dp)
                        .scale(micScale)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Recording",
                    fontSize = 14.sp,
                    color = HighStress
                )
            }
        }
    }
}
