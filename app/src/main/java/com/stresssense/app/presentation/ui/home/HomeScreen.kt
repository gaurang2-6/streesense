package com.stresssense.app.presentation.ui.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.stresssense.app.presentation.components.GradientButton
import com.stresssense.app.presentation.components.StressGauge
import com.stresssense.app.presentation.components.getStressLabel
import com.stresssense.app.presentation.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Main home dashboard screen.
 *
 * Layout:
 * - Greeting with current date
 * - Big stress score card with circular gauge
 * - Quick Scan button (primary CTA)
 * - Today's trend mini chart
 * - Bottom navigation: Home | History | Settings
 */
@Composable
fun HomeScreen(
    onNavigateToScan: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToQuestionnaire: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val dateFormat = remember { SimpleDateFormat("EEEE, MMM d", Locale.getDefault()) }
    val currentDate = remember { dateFormat.format(Date()) }

    Scaffold(
        containerColor = Background,
        bottomBar = {
            NavigationBar(
                containerColor = SurfaceColor,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        ambientColor = TextPrimary.copy(alpha = 0.08f)
                    )
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            ) {
                NavigationBarItem(
                    selected = true,
                    onClick = { },
                    icon = {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = "Home",
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = { Text("Home", fontSize = 12.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentBlue,
                        selectedTextColor = AccentBlue,
                        indicatorColor = AccentBlue.copy(alpha = 0.1f),
                        unselectedIconColor = TextTertiary,
                        unselectedTextColor = TextTertiary
                    )
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToHistory,
                    icon = {
                        Icon(
                            Icons.Rounded.History,
                            contentDescription = "History",
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = { Text("History", fontSize = 12.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentBlue,
                        selectedTextColor = AccentBlue,
                        indicatorColor = AccentBlue.copy(alpha = 0.1f),
                        unselectedIconColor = TextTertiary,
                        unselectedTextColor = TextTertiary
                    )
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToSettings,
                    icon = {
                        Icon(
                            Icons.Rounded.Settings,
                            contentDescription = "Settings",
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = { Text("Settings", fontSize = 12.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentBlue,
                        selectedTextColor = AccentBlue,
                        indicatorColor = AccentBlue.copy(alpha = 0.1f),
                        unselectedIconColor = TextTertiary,
                        unselectedTextColor = TextTertiary
                    )
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // Greeting
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Hey there 👋",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Light,
                        color = TextPrimary,
                        letterSpacing = (-0.5).sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currentDate,
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Big Stress Score Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    CardBackground,
                                    SurfaceColor
                                )
                            )
                        )
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (uiState.latestScan != null) {
                            StressGauge(
                                score = uiState.latestScan!!.finalScore,
                                size = 200.dp,
                                strokeWidth = 14.dp
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Last scan: ${getTimeAgo(uiState.latestScan!!.timestamp)}",
                                fontSize = 12.sp,
                                color = TextTertiary
                            )
                        } else {
                            // Empty state
                            StressGauge(
                                score = 0,
                                size = 200.dp,
                                strokeWidth = 14.dp,
                                showLabel = false
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "No scans yet",
                                fontSize = 15.sp,
                                color = TextSecondary
                            )
                            Text(
                                text = "Take your first scan below",
                                fontSize = 13.sp,
                                color = TextTertiary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Quick Scan Button
            GradientButton(
                text = "⚡ Quick Scan",
                onClick = onNavigateToScan,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Stress Survey Card
            OutlinedCard(
                onClick = onNavigateToQuestionnaire,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, DividerColor),
                colors = CardDefaults.outlinedCardColors(containerColor = SurfaceColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(AccentBlue.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "📝",
                            fontSize = 20.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Perceived Stress Survey",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )
                        Text(
                            text = "Take a 10-question PSS check-in",
                            fontSize = 12.sp,
                            color = TextTertiary
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Today's Trend Card
            if (uiState.todayScans.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = "Today's Trend",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Simple mini chart using scan scores
                        MiniTrendChart(
                            scans = uiState.todayScans,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${uiState.todayScans.size} scans today",
                                fontSize = 12.sp,
                                color = TextTertiary
                            )
                            val avg = uiState.todayScans.map { it.finalScore }.average().toInt()
                            Text(
                                text = "Avg: $avg",
                                fontSize = 12.sp,
                                color = TextTertiary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MiniTrendChart(
    scans: List<com.stresssense.app.data.models.StressScan>,
    modifier: Modifier = Modifier
) {
    val scores = scans.map { it.finalScore }

    androidx.compose.foundation.Canvas(modifier = modifier) {
        if (scores.isEmpty()) return@Canvas

        val maxScore = 100f
        val width = size.width
        val height = size.height
        val padding = 4.dp.toPx()

        val path = androidx.compose.ui.graphics.Path()
        val fillPath = androidx.compose.ui.graphics.Path()

        scores.forEachIndexed { index, score ->
            val x = if (scores.size == 1) width / 2
            else padding + (width - 2 * padding) * index / (scores.size - 1).toFloat()
            val y = height - (score / maxScore) * (height - 2 * padding) - padding

            if (index == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, height)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }

        // Fill area under curve
        fillPath.lineTo(
            padding + (width - 2 * padding) * (scores.size - 1).coerceAtLeast(0) /
                    (scores.size - 1).coerceAtLeast(1).toFloat(),
            height
        )
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    AccentBlue.copy(alpha = 0.15f),
                    Color.Transparent
                )
            )
        )

        // Draw line
        drawPath(
            path = path,
            color = AccentBlue,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 2.5f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        )

        // Draw dots
        scores.forEachIndexed { index, score ->
            val x = if (scores.size == 1) width / 2
            else padding + (width - 2 * padding) * index / (scores.size - 1).toFloat()
            val y = height - (score / maxScore) * (height - 2 * padding) - padding

            drawCircle(
                color = AccentBlue,
                radius = 3.5f,
                center = androidx.compose.ui.geometry.Offset(x, y)
            )
        }
    }
}

/**
 * Format a timestamp as a relative time string (e.g., "5 min ago", "2 hours ago").
 */
private fun getTimeAgo(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val minutes = diff / (1000 * 60)
    val hours = diff / (1000 * 60 * 60)
    val days = diff / (1000 * 60 * 60 * 24)

    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
    }
}
