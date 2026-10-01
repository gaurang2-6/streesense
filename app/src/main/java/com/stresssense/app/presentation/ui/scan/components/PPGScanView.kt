package com.stresssense.app.presentation.ui.scan.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.stresssense.app.presentation.components.ScanProgressIndicator
import com.stresssense.app.presentation.theme.*

/**
 * PPG Scan UI — Step 1 of the scan flow.
 *
 * Binds the **rear** camera with torch (flash) enabled so the user can place
 * a fingertip over the lens. Each analysed frame is forwarded to the
 * ViewModel as a Bitmap; the ViewModel extracts the red-channel mean for the
 * PPG waveform.
 */
@SuppressLint("UnsafeOptInUsageError")
@Composable
fun PPGScanView(
    progress: Float,
    isFingerDetected: Boolean,
    liveHeartRate: Int?,
    onFrame: (Bitmap) -> Unit,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val analysisExecutor = remember { ContextCompat.getMainExecutor(context) }

    // Heartbeat animation
    val infiniteTransition = rememberInfiniteTransition(label = "heartbeat")
    val heartScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (liveHeartRate != null) (60000 / liveHeartRate.coerceAtLeast(40)) else 800,
                easing = EaseOut
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heartScale"
    )

    val secondsRemaining = ((1f - progress) * 20).toInt().coerceAtLeast(0)

    LaunchedEffect(progress) {
        if (progress >= 1f) {
            onComplete()
        }
    }

    // ---- Bind the rear camera with torch + frame analysis -----------------
    // IMPORTANT: ProcessCameraProvider.getInstance() returns a ListenableFuture.
    // Calling .get() on it from the main thread deadlocks (its completion runs
    // on the main thread), so we MUST bind the camera inside addListener.
    DisposableEffect(Unit) {
        var boundCamera: androidx.camera.core.Camera? = null

        cameraProviderFuture.addListener({
            val cameraProvider = try {
                cameraProviderFuture.get()
            } catch (_: Exception) {
                return@addListener
            }

            val analyzer = ImageAnalysis.Builder()
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { ia ->
                    ia.setAnalyzer(analysisExecutor) { imageProxy ->
                        try {
                            val bitmap = imageProxy.toBitmap()
                            onFrame(bitmap)
                        } finally {
                            imageProxy.close()
                        }
                    }
                }

            val selector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider.unbindAll()
                boundCamera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    analyzer
                )
                // Turn the torch on — finger PPG requires the flash.
                boundCamera?.let { c ->
                    if (c.cameraInfo.hasFlashUnit()) {
                        c.cameraControl.enableTorch(true)
                    }
                }
            } catch (_: Exception) {
                // Device may not have a back camera, or binding failed.
            }
        }, analysisExecutor)

        onDispose {
            try {
                boundCamera?.cameraControl?.enableTorch(false)
                cameraProviderFuture.get().unbindAll()
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
            text = if (!isFingerDetected) "Cover the rear camera\nwith your fingertip"
            else "Hold still...",
            fontSize = 20.sp,
            fontWeight = FontWeight.Light,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            lineHeight = 28.sp
        )

        Spacer(modifier = Modifier.height(40.dp))

        Box(contentAlignment = Alignment.Center) {
            ScanProgressIndicator(
                progress = progress,
                size = 200.dp,
                strokeWidth = 10.dp,
                progressColor = if (isFingerDetected) AccentBlue else HighStress.copy(alpha = 0.5f),
                centerText = "${secondsRemaining}s",
                subText = if (isFingerDetected) "Recording" else "Waiting..."
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .background(
                    color = if (isFingerDetected) LowStress.copy(alpha = 0.1f)
                    else HighStress.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Icon(
                Icons.Rounded.Fingerprint,
                contentDescription = null,
                tint = if (isFingerDetected) LowStress else HighStress,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isFingerDetected) "Finger detected" else "No finger detected",
                fontSize = 14.sp,
                color = if (isFingerDetected) LowStress else HighStress
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (liveHeartRate != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(
                        color = HighStress.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 24.dp, vertical = 14.dp)
            ) {
                Icon(
                    Icons.Rounded.Favorite,
                    contentDescription = "Heart rate",
                    tint = HighStress,
                    modifier = Modifier
                        .size(24.dp)
                        .scale(heartScale)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "$liveHeartRate",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Light,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "BPM",
                    fontSize = 14.sp,
                    color = TextSecondary
                )
            }
        }
    }
}
