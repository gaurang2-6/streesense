package com.stresssense.app.presentation.ui.history

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.stresssense.app.data.models.StressScan
import com.stresssense.app.presentation.components.getStressColor
import com.stresssense.app.presentation.components.getStressLabel
import com.stresssense.app.presentation.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * History screen showing scan trends and past results.
 *
 * Features:
 * - Day/Week/Month toggle
 * - Line chart with stress trend
 * - Scrollable list of past scans with tap-to-expand details
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "History",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Time range toggle
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardBackground, RoundedCornerShape(16.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TimeRange.entries.forEach { range ->
                        val isSelected = uiState.selectedRange == range
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) SurfaceColor else Color.Transparent
                                )
                                .clickable { viewModel.setTimeRange(range) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = range.label,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                color = if (isSelected) TextPrimary else TextSecondary
                            )
                        }
                    }
                }
            }

            // Scan Type filter chips
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ScanTypeFilter.entries.forEach { filter ->
                        val isSelected = uiState.selectedScanTypeFilter == filter
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.setScanTypeFilter(filter) },
                            label = { Text(filter.label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentBlue.copy(alpha = 0.12f),
                                selectedLabelColor = AccentBlue,
                                containerColor = SurfaceColor,
                                labelColor = TextSecondary
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) AccentBlue else DividerColor
                            )
                        )
                    }
                }
            }

            // Trend chart
            if (uiState.scans.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    TrendChart(
                        scans = uiState.scans,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    )
                }

                // Stats summary
                item {
                    val avg = uiState.scans.map { it.finalScore }.average().toInt()
                    val min = uiState.scans.minOf { it.finalScore }
                    val max = uiState.scans.maxOf { it.finalScore }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatChip("Avg", "$avg", getStressColor(avg))
                        StatChip("Low", "$min", LowStress)
                        StatChip("High", "$max", HighStress)
                        StatChip("Scans", "${uiState.scans.size}", AccentBlue)
                    }
                }
            }

            // Scan list header
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Past Scans",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
            }

            // Empty state
            if (uiState.scans.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No scans yet",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Light,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Complete your first scan to see history",
                                fontSize = 14.sp,
                                color = TextTertiary
                            )
                        }
                    }
                }
            }

            // Scan items
            items(uiState.scans, key = { it.id }) { scan ->
                ScanHistoryItem(
                    scan = scan,
                    isExpanded = uiState.expandedScanIds.contains(scan.id),
                    onToggleExpand = { viewModel.toggleScanExpansion(scan.id) }
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun TrendChart(
    scans: List<StressScan>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        val scores = scans.map { it.finalScore }

        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            if (scores.isEmpty()) return@Canvas

            val maxScore = 100f
            val width = size.width
            val height = size.height
            val padding = 8f

            // Draw grid lines
            for (i in 0..4) {
                val y = padding + (height - 2 * padding) * i / 4
                drawLine(
                    color = DividerColor,
                    start = androidx.compose.ui.geometry.Offset(padding, y),
                    end = androidx.compose.ui.geometry.Offset(width - padding, y),
                    strokeWidth = 1f
                )
            }

            // Draw data path
            val path = androidx.compose.ui.graphics.Path()
            val fillPath = androidx.compose.ui.graphics.Path()

            scores.forEachIndexed { index, score ->
                val x = if (scores.size == 1) width / 2
                else padding + (width - 2 * padding) * index / (scores.size - 1).toFloat()
                val y = height - (score / maxScore) * (height - 2 * padding) - padding

                if (index == 0) {
                    path.moveTo(x, y)
                    fillPath.moveTo(x, height - padding)
                    fillPath.lineTo(x, y)
                } else {
                    path.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }

            // Fill gradient
            val lastX = if (scores.size == 1) width / 2
            else padding + (width - 2 * padding) * (scores.size - 1) / (scores.size - 1).toFloat()
            fillPath.lineTo(lastX, height - padding)
            fillPath.close()

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        AccentBlue.copy(alpha = 0.2f),
                        Color.Transparent
                    )
                )
            )

            // Draw line
            drawPath(
                path = path,
                color = AccentBlue,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 3f,
                    cap = StrokeCap.Round
                )
            )

            // Draw points
            scores.forEachIndexed { index, score ->
                val x = if (scores.size == 1) width / 2
                else padding + (width - 2 * padding) * index / (scores.size - 1).toFloat()
                val y = height - (score / maxScore) * (height - 2 * padding) - padding

                drawCircle(
                    color = SurfaceColor,
                    radius = 5f,
                    center = androidx.compose.ui.geometry.Offset(x, y)
                )
                drawCircle(
                    color = getStressColor(score),
                    radius = 4f,
                    center = androidx.compose.ui.geometry.Offset(x, y)
                )
            }
        }
    }
}

@Composable
private fun ScanHistoryItem(
    scan: StressScan,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleExpand() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Score dot
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            getStressColor(scan.finalScore).copy(alpha = 0.15f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${scan.finalScore}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = getStressColor(scan.finalScore)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    val label = if (scan.scanType == "QUESTIONNAIRE") {
                        val rawScore = scan.hrvScore ?: 0
                        when {
                            rawScore <= 13 -> "Low Stress"
                            rawScore <= 26 -> "Moderate Stress"
                            else -> "High Stress"
                        }
                    } else {
                        getStressLabel(scan.finalScore)
                    }
                    Text(
                        text = label,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                    Text(
                        text = "${dateFormat.format(Date(scan.timestamp))} · ${timeFormat.format(Date(scan.timestamp))}",
                        fontSize = 12.sp,
                        color = TextTertiary
                    )
                }

                Icon(
                    if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = "Toggle details",
                    tint = TextTertiary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Expanded details
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(color = DividerColor)
                    Spacer(modifier = Modifier.height(12.dp))

                    if (scan.scanType == "QUESTIONNAIRE") {
                        val rawScore = scan.hrvScore ?: 0
                        val pssLabel = when {
                            rawScore <= 13 -> "Low Stress"
                            rawScore <= 26 -> "Moderate Stress"
                            else -> "High Stress"
                        }
                        DetailRow("PSS Survey Score", "$rawScore / 40")
                        DetailRow("PSS Classification", pssLabel)
                        DetailRow("Equivalent Stress Score", "${scan.finalScore} / 100")
                    } else {
                        scan.hrvScore?.let { DetailRow("HRV Score", "$it") }
                        scan.voiceScore?.let { DetailRow("Voice Score", "$it") }
                        scan.facialScore?.let { DetailRow("Facial Score", "$it") }
                        scan.heartRate?.let { DetailRow("Heart Rate", "$it BPM") }
                        scan.rmssd?.let { DetailRow("RMSSD", "${String.format("%.1f", it)} ms") }
                        scan.sdnn?.let { DetailRow("SDNN", "${String.format("%.1f", it)} ms") }
                    }

                    DetailRow("Scan Type", if (scan.scanType == "QUESTIONNAIRE") "PSS Survey" else scan.scanType)
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 13.sp, color = TextSecondary)
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
    }
}

@Composable
private fun StatChip(label: String, value: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(color.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = color)
        Text(text = label, fontSize = 11.sp, color = TextTertiary)
    }
}
