package com.stresssense.app.presentation.ui.scan.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stresssense.app.presentation.components.StressGauge
import com.stresssense.app.presentation.components.getStressColor
import com.stresssense.app.presentation.components.getStressLabel
import com.stresssense.app.presentation.theme.*

/**
 * Scan result screen with animated score reveal.
 *
 * Features:
 * - Big animated number reveal (StressGauge)
 * - Soft pulse animation
 * - Score breakdown (HRV, Voice, Facial)
 * - Done button to return to Home
 */
@Composable
fun ScanResultView(
    finalScore: Int,
    hrvScore: Int?,
    voiceScore: Int?,
    facialScore: Int?,
    heartRate: Int?,
    onDone: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    val fadeIn by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(800, easing = EaseOut),
        label = "resultFade"
    )

    val scaleIn by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.6f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "resultScale"
    )

    // Soft pulsing glow behind the score
    val infiniteTransition = rememberInfiniteTransition(label = "resultGlow")
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .alpha(fadeIn),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Check icon
        Icon(
            Icons.Rounded.CheckCircle,
            contentDescription = "Complete",
            tint = LowStress,
            modifier = Modifier.size(40.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Scan Complete",
            fontSize = 16.sp,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Big animated gauge with glow
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.scale(scaleIn)
        ) {
            // Glow circle behind gauge
            Box(
                modifier = Modifier
                    .size(230.dp)
                    .scale(glowScale)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                getStressColor(finalScore).copy(alpha = 0.15f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )

            StressGauge(
                score = finalScore,
                size = 220.dp,
                strokeWidth = 16.dp,
                animationDuration = 1500
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Score breakdown card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "Breakdown",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(12.dp))

                // HRV Score
                hrvScore?.let {
                    ScoreRow("Heart Rate Variability", it)
                }

                // Voice Score
                voiceScore?.let {
                    ScoreRow("Voice Analysis", it)
                }

                // Facial Score
                facialScore?.let {
                    ScoreRow("Facial Analysis", it)
                }

                // Heart Rate
                heartRate?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = DividerColor)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Heart Rate",
                            fontSize = 14.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = "$it BPM",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Done button
        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            contentPadding = PaddingValues(0.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(GradientStart, GradientMid, GradientEnd)
                        ),
                        shape = RoundedCornerShape(28.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Done",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
            }
        }
    }
}

@Composable
private fun ScoreRow(label: String, score: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = TextSecondary
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Mini color dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(getStressColor(score), CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "$score",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
        }
    }
}
