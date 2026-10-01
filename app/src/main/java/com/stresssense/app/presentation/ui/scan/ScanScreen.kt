package com.stresssense.app.presentation.ui.scan

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.*
import com.stresssense.app.presentation.theme.*
import com.stresssense.app.presentation.ui.scan.components.*

/**
 * Main scan screen orchestrating the multi-step flow.
 *
 * Steps flow: PPG → Voice → Facial → Result
 * The whole flow is gated behind the CAMERA and RECORD_AUDIO runtime
 * permissions. If the user denies, an explanatory screen with a retry
 * button is shown instead of crashing.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ScanScreen(
    onNavigateBack: () -> Unit,
    viewModel: ScanViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // ---- Runtime permission gate -------------------------------------------
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)
    val micPermissionState = rememberPermissionState(android.Manifest.permission.RECORD_AUDIO)

    val allGranted =
        cameraPermissionState.status.isGranted && micPermissionState.status.isGranted

    // Track whether we've already shown the system prompt, so the UI never
    // gets stuck on an infinite spinner after a denial.
    var promptShown by rememberSaveable { mutableStateOf(false) }

    // Fire the system permission dialogs once when the scan opens.
    LaunchedEffect(Unit) {
        if (!allGranted && !promptShown) {
            promptShown = true
            if (!cameraPermissionState.status.isGranted) {
                cameraPermissionState.launchPermissionRequest()
            }
            if (!micPermissionState.status.isGranted) {
                micPermissionState.launchPermissionRequest()
            }
        }
    }

    // Compute and save when reaching result step
    LaunchedEffect(uiState.currentStep) {
        if (uiState.currentStep == ScanStep.RESULT && !uiState.isScanComplete) {
            viewModel.computeAndSaveResult()
        }
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (uiState.currentStep) {
                            ScanStep.PPG -> "Step 1 · Heart Rate"
                            ScanStep.VOICE -> "Step 2 · Voice"
                            ScanStep.FACIAL -> "Step 3 · Facial"
                            ScanStep.RESULT -> "Results"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.resetScan()
                        onNavigateBack()
                    }) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Cancel scan",
                            tint = TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Background
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                // ---- Permissions granted: run the real scan -------------------
                allGranted -> GrantedContent(
                    uiState = uiState,
                    viewModel = viewModel
                )

                // ---- Not granted and we've already prompted: show a usable
                // rationale screen with a Retry button (never an infinite
                // spinner). This covers both "denied once" and
                // "permanently denied (Don't ask again)".
                promptShown -> PermissionRationale(
                    onRetry = {
                        if (!cameraPermissionState.status.isGranted) {
                            cameraPermissionState.launchPermissionRequest()
                        }
                        if (!micPermissionState.status.isGranted) {
                            micPermissionState.launchPermissionRequest()
                        }
                    }
                )

                // ---- Default: still showing the system prompt for the first time
                else -> PermissionWaiting()
            }
        }
    }
}

@Composable
private fun GrantedContent(
    uiState: ScanUiState,
    viewModel: ScanViewModel
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Step progress indicator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val steps = listOf(ScanStep.PPG, ScanStep.VOICE, ScanStep.FACIAL)
            steps.forEachIndexed { _, step ->
                val isCompleted = uiState.currentStep.ordinal > step.ordinal
                val isCurrent = uiState.currentStep == step

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .background(
                            color = when {
                                isCompleted -> AccentBlue
                                isCurrent -> AccentBlue.copy(alpha = 0.4f)
                                else -> DividerColor
                            },
                            shape = RoundedCornerShape(2.dp)
                        )
                )
            }
        }

        // Animated step content
        AnimatedContent(
            targetState = uiState.currentStep,
            transitionSpec = {
                fadeIn(animationSpec = tween(400)) togetherWith
                        fadeOut(animationSpec = tween(300))
            },
            label = "scanStep",
            modifier = Modifier.weight(1f)
        ) { step ->
            when (step) {
                ScanStep.PPG -> PPGScanView(
                    progress = uiState.ppgProgress,
                    isFingerDetected = uiState.isFingerDetected,
                    liveHeartRate = uiState.liveHeartRate,
                    onFrame = viewModel::processPPGFrame,
                    onComplete = { viewModel.completePPGScan() }
                )

                ScanStep.VOICE -> VoiceScanView(
                    progress = uiState.voiceProgress,
                    isRecording = uiState.isVoiceRecording,
                    onStartRecording = { viewModel.startVoiceScan() }
                )

                ScanStep.FACIAL -> FacialScanView(
                    progress = uiState.facialProgress,
                    frameCount = uiState.facialFrameCount,
                    onFaceDetected = viewModel::processFaceFrame,
                    onSkip = { viewModel.skipStep() },
                    onComplete = { viewModel.completeFacialScan() }
                )

                ScanStep.RESULT -> {
                    if (uiState.isScanComplete && uiState.finalScore != null) {
                        ScanResultView(
                            finalScore = uiState.finalScore!!,
                            hrvScore = uiState.hrvScore,
                            voiceScore = uiState.voiceScore,
                            facialScore = uiState.facialScore,
                            heartRate = uiState.hrvResult?.heartRate?.toInt(),
                            onDone = {
                                viewModel.resetScan()
                            }
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = AccentBlue,
                                strokeWidth = 3.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionWaiting() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = AccentBlue, strokeWidth = 3.dp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Waiting for permission…",
                color = TextSecondary,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun PermissionRationale(onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Permissions required",
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "StressSense needs the camera to measure your heart rate " +
                        "from your fingertip and analyse facial cues, and the " +
                        "microphone to analyse your voice. These measurements are " +
                        "processed entirely on your device.",
                fontSize = 14.sp,
                color = TextSecondary,
                lineHeight = 22.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Text("Grant permission", color = SurfaceColor)
            }
        }
    }
}
