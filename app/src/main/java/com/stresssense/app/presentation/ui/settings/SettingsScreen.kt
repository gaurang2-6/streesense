package com.stresssense.app.presentation.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.stresssense.app.presentation.theme.*

/**
 * Settings screen with app configuration options.
 *
 * Sections:
 * - Monitoring: Passive monitoring toggle
 * - Notifications: Push notification toggle
 * - Calibration: Recalibrate baseline
 * - About: Version info, privacy policy
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPrivacyPolicy: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ─── Monitoring Section ──────────────────────────────
            SectionHeader("Monitoring")

            SettingsCard {
                SettingsToggleItem(
                    icon = Icons.Rounded.Sensors,
                    title = "Passive Monitoring",
                    description = "Track typing patterns and phone usage in the background",
                    isChecked = uiState.passiveMonitoringEnabled,
                    onToggle = { viewModel.togglePassiveMonitoring(it) }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ─── Notifications Section ───────────────────────────
            SectionHeader("Notifications")

            SettingsCard {
                SettingsToggleItem(
                    icon = Icons.Rounded.Notifications,
                    title = "Push Notifications",
                    description = "Get reminders to check your stress levels",
                    isChecked = uiState.notificationsEnabled,
                    onToggle = { viewModel.toggleNotifications(it) }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ─── Calibration Section ─────────────────────────────
            SectionHeader("Calibration")

            SettingsCard {
                SettingsActionItem(
                    icon = Icons.Rounded.Tune,
                    title = "Recalibrate Baseline",
                    description = if (uiState.hasBaseline) "Reset your personal baseline measurements"
                    else "No baseline set — take scans to build one",
                    onClick = { viewModel.recalibrateBaseline() },
                    isLoading = uiState.isRecalibrating
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ─── Model Performance Section ───────────────────────
            SectionHeader("On-Device ML Performance")

            SettingsCard {
                SettingsInfoItem(
                    icon = Icons.Rounded.Verified,
                    title = "Model Accuracy",
                    value = "91.2% (INT8 TFLite)"
                )

                HorizontalDivider(
                    color = DividerColor,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                SettingsInfoItem(
                    icon = Icons.Rounded.Memory,
                    title = "Model Size",
                    value = "5.77 KB"
                )

                HorizontalDivider(
                    color = DividerColor,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                SettingsInfoItem(
                    icon = Icons.Rounded.Speed,
                    title = "Inference Latency",
                    value = "< 1.8 ms"
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ─── About Section ───────────────────────────────────
            SectionHeader("About")

            SettingsCard {
                SettingsInfoItem(
                    icon = Icons.Rounded.Info,
                    title = "Version",
                    value = "1.0.0"
                )

                HorizontalDivider(
                    color = DividerColor,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                SettingsActionItem(
                    icon = Icons.Rounded.Shield,
                    title = "Privacy Policy",
                    description = "All data stays on your device",
                    onClick = onNavigateToPrivacyPolicy
                )

                HorizontalDivider(
                    color = DividerColor,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                SettingsActionItem(
                    icon = Icons.Rounded.Security,
                    title = "Permissions",
                    description = "Manage camera, microphone, and other permissions",
                    onClick = { /* Open app settings */ }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Footer
            Text(
                text = "StressSense — All processing on-device.\nYour data never leaves your phone.",
                fontSize = 12.sp,
                color = TextTertiary,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = TextSecondary,
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsCard(
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    description: String,
    isChecked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!isChecked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = AccentBlue,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
            Text(
                text = description,
                fontSize = 12.sp,
                color = TextTertiary,
                lineHeight = 16.sp
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = isChecked,
            onCheckedChange = null,  // driven by Row.clickable to avoid double-fire
            colors = SwitchDefaults.colors(
                checkedThumbColor = SurfaceColor,
                checkedTrackColor = AccentBlue,
                uncheckedThumbColor = SurfaceColor,
                uncheckedTrackColor = DividerColor
            )
        )
    }
}

@Composable
private fun SettingsActionItem(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    isLoading: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isLoading) { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = AccentBlue,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
            Text(
                text = description,
                fontSize = 12.sp,
                color = TextTertiary,
                lineHeight = 16.sp
            )
        }
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = AccentBlue
            )
        } else {
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SettingsInfoItem(
    icon: ImageVector,
    title: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = AccentBlue,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            fontSize = 14.sp,
            color = TextSecondary
        )
    }
}
