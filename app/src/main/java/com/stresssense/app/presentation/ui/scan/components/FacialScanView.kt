package com.stresssense.app.presentation.ui.scan.components

import android.annotation.SuppressLint
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.common.InputImage
import com.stresssense.app.presentation.components.ScanProgressIndicator
import com.stresssense.app.presentation.theme.*
import kotlinx.coroutines.delay

/**
 * Facial Scan UI — Step 3 (optional) of the scan flow.
 *
 * Binds the **front** camera, shows a live circular preview, and runs ML Kit
 * face detection on each frame. Detected faces are forwarded to the ViewModel
 * via [onFaceDetected], which accumulates blink / openness / smile data.
 */
@SuppressLint("UnsafeOptInUsageError")
@Composable
fun FacialScanView(
    progress: Float,
    frameCount: Int,
    onFaceDetected: (com.google.mlkit.vision.face.Face) -> Unit,
    onSkip: () -> Unit,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val analysisExecutor = remember { ContextCompat.getMainExecutor(context) }

    // Single shared PreviewView used both by the camera binding and the UI.
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    // ML Kit face detector — needs eye-openness + smile probabilities.
    val faceDetector = remember {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.25f)
            .build()
        FaceDetection.getClient(options)
    }

    // Time-based 10s timer so the scan always completes even if ML Kit
    // doesn't detect a face (e.g. user is too far, wrong angle, etc.)
    var elapsed by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (elapsed < 1f) {
            delay(100)
            elapsed = (elapsed + 0.01f).coerceAtMost(1f)
        }
        // Auto-complete when timer finishes
        onComplete()
    }

    // Use elapsed (always advances) for the displayed countdown.
    // progress from faceAnalyzer is used only to auto-complete early.
    val displayProgress = elapsed.coerceAtMost(progress.coerceAtLeast(elapsed))
    val secondsRemaining = ((1f - displayProgress) * 10).toInt().coerceAtLeast(0)

    LaunchedEffect(progress) {
        // Also complete if the face-analyzer progress reaches 1.0
        if (progress >= 1f) {
            onComplete()
        }
    }

    // ---- Bind the front camera: preview + ML Kit analysis -----------------
    // IMPORTANT: bind inside addListener — calling .get() on the main thread
    // deadlocks (the future's completion runs on the main thread).
    DisposableEffect(Unit) {
        cameraProviderFuture.addListener({
            val cameraProvider = try {
                cameraProviderFuture.get()
            } catch (_: Exception) {
                return@addListener
            }

            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(previewView.surfaceProvider)

            val analyzer = ImageAnalysis.Builder()
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { ia ->
                    ia.setAnalyzer(analysisExecutor) { imageProxy ->
                        try {
                            val mediaImage = imageProxy.image
                            if (mediaImage != null) {
                                val rotation = imageProxy.imageInfo.rotationDegrees
                                val inputImage = InputImage.fromMediaImage(mediaImage, rotation)
                                faceDetector.process(inputImage)
                                    .addOnSuccessListener { faces ->
                                        if (faces.isNotEmpty()) {
                                            onFaceDetected(faces.first())
                                        }
                                    }
                            }
                        } catch (_: Exception) {
                            // Face detection can fail on some frames — skip silently.
                        } finally {
                            imageProxy.close()
                        }
                    }
                }

            val selector = CameraSelector.DEFAULT_FRONT_CAMERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    preview,
                    analyzer
                )
            } catch (_: Exception) {
                // No front camera, or binding failed.
            }
        }, analysisExecutor)

        onDispose {
            try {
                cameraProviderFuture.get().unbindAll()
                faceDetector.close()
            } catch (_: Exception) {
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Look at the camera\nnaturally",
            fontSize = 20.sp,
            fontWeight = FontWeight.Light,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            lineHeight = 28.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Camera preview area with face oval
        Box(
            modifier = Modifier
                .size(220.dp)
                .clip(CircleShape)
                .background(CardBackground)
                .border(
                    width = 3.dp,
                    color = if (frameCount > 0) AccentBlue.copy(alpha = 0.6f) else DividerColor,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            // Live front-camera preview, cropped into the circle.
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize().clip(CircleShape)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        ScanProgressIndicator(
            progress = displayProgress,
            size = 120.dp,
            strokeWidth = 6.dp,
            progressColor = LowStress,
            centerText = "${secondsRemaining}s"
        )

        Spacer(modifier = Modifier.height(24.dp))

        TextButton(
            onClick = onSkip,
            modifier = Modifier.padding(8.dp)
        ) {
            Icon(
                Icons.Rounded.SkipNext,
                contentDescription = "Skip",
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Skip this step",
                fontSize = 14.sp,
                color = TextSecondary
            )
        }
    }
}
