package com.stresssense.app.presentation.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stresssense.app.data.models.StressScan
import com.stresssense.app.domain.algorithms.*
import com.stresssense.app.domain.usecases.SaveScanUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel managing the multi-step scan flow.
 *
 * Steps:
 * 1. PPG Scan (20 sec) — finger on rear camera
 * 2. Voice Scan (10 sec) — read a sentence
 * 3. Facial Scan (10 sec) — front camera
 * 4. Result display
 */
@HiltViewModel
class ScanViewModel @Inject constructor(
    private val saveScanUseCase: SaveScanUseCase,
    private val signalProcessor: SignalProcessor,
    private val hrvCalculator: HRVCalculator,
    private val stressScoreCalculator: StressScoreCalculator,
    private val voiceAnalyzer: VoiceAnalyzer,
    private val faceAnalyzer: FaceAnalyzer
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    // PPG data accumulation
    private val ppgDataPoints = mutableListOf<PPGProcessor.PPGDataPoint>()
    private val ppgProcessor = PPGProcessor()

    /**
     * Advance to the next scan step.
     */
    fun nextStep() {
        _uiState.update {
            val nextStep = when (it.currentStep) {
                ScanStep.PPG -> ScanStep.VOICE
                ScanStep.VOICE -> ScanStep.FACIAL
                ScanStep.FACIAL -> ScanStep.RESULT
                ScanStep.RESULT -> ScanStep.RESULT
            }
            it.copy(currentStep = nextStep)
        }
    }

    /**
     * Skip a step (e.g., facial scan is optional).
     */
    fun skipStep() {
        nextStep()
    }

    // ─── PPG Scan ──────────────────────────────────────────────────

    /**
     * Process a camera frame during PPG capture.
     *
     * Channel extraction is cheap, but the live heart-rate estimate runs the
     * full Butterworth + peak-detection pipeline, so that work is dispatched
     * to a background thread to avoid freezing the UI (ANR) at 30fps.
     */
    fun processPPGFrame(bitmap: android.graphics.Bitmap) {
        viewModelScope.launch(Dispatchers.Default) {
            val dataPoint = ppgProcessor.extractChannelMeans(bitmap)
            val contact = ppgProcessor.checkFingerContact(dataPoint)

            ppgDataPoints.add(dataPoint)

            val progress = ppgDataPoints.size.toFloat() / PPGProcessor.EXPECTED_FRAMES
            // Only run the (expensive) live HR estimate every ~10 frames to
            // keep CPU usage sane, and never before enough data exists.
            val heartRate = if (ppgDataPoints.size > 60 && ppgDataPoints.size % 10 == 0) {
                estimateLiveHeartRate()
            } else {
                _uiState.value.liveHeartRate
            }

            _uiState.update {
                it.copy(
                    ppgProgress = progress.coerceIn(0f, 1f),
                    isFingerDetected = contact.isFingerDetected,
                    liveHeartRate = heartRate,
                    ppgFrameCount = ppgDataPoints.size
                )
            }
        }
    }

    /**
     * Complete PPG scan and process the signal.
     */
    fun completePPGScan() {
        viewModelScope.launch {
            val rawSignal = ppgDataPoints.map { it.redMean }
            val timestamps = ppgDataPoints.map { it.timestamp }

            // Calculate actual sampling rate
            val duration = if (timestamps.size >= 2) {
                (timestamps.last() - timestamps.first()) / 1000.0
            } else 20.0
            val samplingRate = if (duration > 0) timestamps.size / duration else 30.0

            // Process signal
            val processed = signalProcessor.process(rawSignal, timestamps, samplingRate)
            val rrIntervals = processed.calculateRRIntervals()

            // Calculate HRV
            val hrvResult = hrvCalculator.calculate(rrIntervals)

            if (hrvResult != null) {
                val hrvScore = stressScoreCalculator.calculateHRVStressScore(hrvResult.rmssd)
                _uiState.update {
                    it.copy(
                        hrvResult = hrvResult,
                        hrvScore = hrvScore
                    )
                }
            } else {
                // Fallback: use a moderate score if HRV computation fails
                _uiState.update {
                    it.copy(hrvScore = 50)
                }
            }

            nextStep()
        }
    }

    // ─── Voice Scan ────────────────────────────────────────────────

    /**
     * Start voice recording and analysis.
     */
    fun startVoiceScan() {
        viewModelScope.launch {
            _uiState.update { it.copy(isVoiceRecording = true) }

            try {
                val voiceResult = voiceAnalyzer.analyzeVoice()
                _uiState.update {
                    it.copy(
                        voiceResult = voiceResult,
                        voiceScore = voiceResult.stressScore,
                        isVoiceRecording = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isVoiceRecording = false) }
            }

            nextStep()
        }
    }

    /**
     * Update voice recording progress.
     */
    fun updateVoiceProgress(progress: Float) {
        _uiState.update { it.copy(voiceProgress = progress) }
    }

    // ─── Facial Scan ───────────────────────────────────────────────

    /**
     * Process a face detection result during facial scan.
     */
    fun processFaceFrame(face: com.google.mlkit.vision.face.Face) {
        faceAnalyzer.processFace(face)
        _uiState.update {
            it.copy(
                facialProgress = faceAnalyzer.getProgress(),
                facialFrameCount = faceAnalyzer.getFrameCount()
            )
        }
    }

    /**
     * Complete facial scan and compute result.
     */
    fun completeFacialScan() {
        val result = faceAnalyzer.computeResult()
        _uiState.update {
            it.copy(
                faceResult = result,
                facialScore = result?.stressScore
            )
        }
        faceAnalyzer.reset()
        nextStep()
    }

    /**
     * Start facial analysis capture.
     */
    fun startFacialCapture() {
        faceAnalyzer.reset()
    }

    // ─── Result ────────────────────────────────────────────────────

    /**
     * Compute and save the final composite score.
     */
    fun computeAndSaveResult() {
        viewModelScope.launch {
            val state = _uiState.value
            val hrvScore = state.hrvScore ?: 50

            val finalScore = stressScoreCalculator.computeFinalScore(
                hrvScore = hrvScore,
                voiceScore = state.voiceScore,
                facialScore = state.facialScore
            )

            val scan = StressScan(
                timestamp = System.currentTimeMillis(),
                finalScore = finalScore,
                hrvScore = hrvScore,
                voiceScore = state.voiceScore,
                facialScore = state.facialScore,
                heartRate = state.hrvResult?.heartRate?.toInt(),
                rmssd = state.hrvResult?.rmssd,
                sdnn = state.hrvResult?.sdnn,
                pnn50 = state.hrvResult?.pnn50,
                scanType = "ACTIVE"
            )

            saveScanUseCase(scan)

            _uiState.update {
                it.copy(
                    finalScore = finalScore,
                    isScanComplete = true
                )
            }
        }
    }

    /**
     * Live heart rate estimate from the last few seconds of PPG data.
     */
    private fun estimateLiveHeartRate(): Int? {
        val recentPoints = ppgDataPoints.takeLast(90) // ~3 sec at 30fps
        if (recentPoints.size < 30) return null

        val rawSignal = recentPoints.map { it.redMean }
        val timestamps = recentPoints.map { it.timestamp }
        val duration = (timestamps.last() - timestamps.first()) / 1000.0
        val sampleRate = if (duration > 0) timestamps.size / duration else 30.0

        val processed = signalProcessor.process(rawSignal, timestamps, sampleRate)
        val rrIntervals = processed.calculateRRIntervals()

        if (rrIntervals.isEmpty()) return null

        val meanRR = rrIntervals.average()
        return if (meanRR > 0) (60000.0 / meanRR).toInt() else null
    }

    /**
     * Reset the scan state for a new scan.
     */
    fun resetScan() {
        ppgDataPoints.clear()
        faceAnalyzer.reset()
        _uiState.value = ScanUiState()
    }
}

enum class ScanStep {
    PPG, VOICE, FACIAL, RESULT
}

data class ScanUiState(
    val currentStep: ScanStep = ScanStep.PPG,

    // PPG
    val ppgProgress: Float = 0f,
    val isFingerDetected: Boolean = false,
    val liveHeartRate: Int? = null,
    val ppgFrameCount: Int = 0,
    val hrvResult: HRVCalculator.HRVResult? = null,
    val hrvScore: Int? = null,

    // Voice
    val voiceProgress: Float = 0f,
    val isVoiceRecording: Boolean = false,
    val voiceResult: VoiceAnalyzer.VoiceResult? = null,
    val voiceScore: Int? = null,

    // Facial
    val facialProgress: Float = 0f,
    val facialFrameCount: Int = 0,
    val faceResult: FaceAnalyzer.FaceResult? = null,
    val facialScore: Int? = null,

    // Result
    val finalScore: Int? = null,
    val isScanComplete: Boolean = false
)
