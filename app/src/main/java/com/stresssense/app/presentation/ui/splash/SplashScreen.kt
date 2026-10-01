package com.stresssense.app.presentation.ui.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stresssense.app.presentation.theme.*
import kotlinx.coroutines.delay

/**
 * Splash screen with calming gradient background and fade-in logo.
 * Auto-navigates to Onboarding (first launch) or Home after 1.5 seconds.
 */
@Composable
fun SplashScreen(
    onNavigateToOnboarding: () -> Unit,
    onNavigateToHome: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
        delay(1500)
        // For now, always navigate to onboarding on first launch
        // In production, check DataStore preference for onboarding completion
        onNavigateToOnboarding()
    }

    val fadeIn by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(800, easing = EaseOut),
        label = "splashFade"
    )

    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.8f,
        animationSpec = tween(800, easing = EaseOut),
        label = "splashScale"
    )

    // Gentle breathing animation for the circle
    val infiniteTransition = rememberInfiniteTransition(label = "breathe")
    val breatheScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breatheScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        GradientStart,
                        GradientMid,
                        GradientEnd
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .alpha(fadeIn)
                .scale(scale)
        ) {
            // Animated circle with logo-like icon
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(breatheScale)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                GlassWhite,
                                GlassBorder
                            )
                        ),
                        shape = androidx.compose.foundation.shape.CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Stylized "S" for StressSense
                Text(
                    text = "S",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Light,
                    color = TextPrimary.copy(alpha = 0.8f),
                    letterSpacing = (-2).sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "StressSense",
                fontSize = 32.sp,
                fontWeight = FontWeight.Light,
                color = TextPrimary,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Know your calm",
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = TextSecondary,
                letterSpacing = 1.sp
            )
        }
    }
}
