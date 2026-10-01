package com.stresssense.app.domain.algorithms

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.*

/**
 * Voice stress analyzer using AudioRecord API.
 *
 * Records 10 seconds of audio at 16kHz mono and extracts vocal biomarkers:
 * - Pitch (F0) via YIN algorithm — fundamental frequency of speech
 * - Jitter — cycle-to-cycle pitch variation (high = stress)
 * - Shimmer — amplitude variation (high = stress)
 * - Speech rate — syllable density approximation
 *
 * All processing on-device; no audio data is stored or transmitted.
 */
class VoiceAnalyzer @Inject constructor() {

    companion object {
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val RECORDING_DURATION_SEC = 10
        const val TOTAL_SAMPLES = SAMPLE_RATE * RECORDING_DURATION_SEC

        // YIN algorithm parameters
        const val YIN_THRESHOLD = 0.15
        const val MIN_FREQUENCY = 60.0   // Hz — lowest expected pitch
        const val MAX_FREQUENCY = 500.0  // Hz — highest expected pitch

        // Frame analysis parameters
        const val FRAME_SIZE = 2048
        const val HOP_SIZE = 512
    }

    /**
     * Record audio and analyze voice stress features.
     * Must be called from a coroutine context.
     *
     * @return VoiceResult with all extracted features and stress score
     */
    suspend fun analyzeVoice(): VoiceResult = withContext(Dispatchers.IO) {
        val bufferSize = maxOf(
            AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT),
            FRAME_SIZE * 2
        )

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )

        val audioData = ShortArray(TOTAL_SAMPLES)
        var totalRead = 0

        try {
            audioRecord.startRecording()

            while (totalRead < TOTAL_SAMPLES && isActive) {
                val toRead = minOf(bufferSize, TOTAL_SAMPLES - totalRead)
                val read = audioRecord.read(audioData, totalRead, toRead)
                if (read > 0) totalRead += read
            }
        } finally {
            audioRecord.stop()
            audioRecord.release()
        }

        // Convert to normalized float samples [-1, 1]
        val samples = DoubleArray(totalRead) { audioData[it].toDouble() / Short.MAX_VALUE }

        analyzeFromSamples(samples)
    }

    /**
     * Analyze pre-recorded audio samples.
     * Useful for testing without a microphone.
     */
    fun analyzeFromSamples(samples: DoubleArray): VoiceResult {
        if (samples.size < FRAME_SIZE) {
            return VoiceResult(0.0, 0.0, 0.0, 0.0, 50)
        }

        // Extract pitch contour using YIN algorithm
        val pitchValues = mutableListOf<Double>()
        val amplitudes = mutableListOf<Double>()

        var offset = 0
        while (offset + FRAME_SIZE <= samples.size) {
            val frame = samples.sliceArray(offset until offset + FRAME_SIZE)

            // Compute frame energy (RMS)
            val rms = sqrt(frame.map { it * it }.average())
            amplitudes.add(rms)

            // Only analyze frames with sufficient energy (voice activity)
            if (rms > 0.01) {
                val pitch = yinPitchDetection(frame, SAMPLE_RATE.toDouble())
                if (pitch > 0) {
                    pitchValues.add(pitch)
                }
            }

            offset += HOP_SIZE
        }

        // Calculate vocal biomarkers
        val meanPitch = if (pitchValues.isNotEmpty()) pitchValues.average() else 0.0
        val jitter = calculateJitter(pitchValues)
        val shimmer = calculateShimmer(amplitudes)
        val speechRate = estimateSpeechRate(amplitudes)

        // Compute voice stress score
        val stressScore = computeVoiceStressScore(meanPitch, jitter, shimmer, speechRate)

        return VoiceResult(
            meanPitch = meanPitch,
            jitter = jitter,
            shimmer = shimmer,
            speechRate = speechRate,
            stressScore = stressScore
        )
    }

    /**
     * YIN pitch detection algorithm.
     *
     * Estimates fundamental frequency by computing the cumulative mean
     * normalized difference function and finding the first dip below threshold.
     *
     * @param frame   Audio frame (typically 2048 samples)
     * @param sampleRate Sample rate in Hz
     * @return Estimated pitch in Hz, or -1 if unvoiced
     */
    private fun yinPitchDetection(frame: DoubleArray, sampleRate: Double): Double {
        val halfSize = frame.size / 2
        val minLag = (sampleRate / MAX_FREQUENCY).toInt()
        val maxLag = (sampleRate / MIN_FREQUENCY).toInt().coerceAtMost(halfSize)

        // Step 1: Difference function
        val diff = DoubleArray(halfSize)
        for (tau in 1 until halfSize) {
            var sum = 0.0
            for (j in 0 until halfSize) {
                val delta = frame[j] - frame[j + tau]
                sum += delta * delta
            }
            diff[tau] = sum
        }

        // Step 2: Cumulative mean normalized difference
        val cmndf = DoubleArray(halfSize)
        cmndf[0] = 1.0
        var runningSum = 0.0
        for (tau in 1 until halfSize) {
            runningSum += diff[tau]
            cmndf[tau] = if (runningSum > 0) diff[tau] * tau / runningSum else 1.0
        }

        // Step 3: Find first dip below threshold
        var bestTau = -1
        for (tau in minLag until maxLag) {
            if (cmndf[tau] < YIN_THRESHOLD) {
                // Find the local minimum in this dip
                while (tau + 1 < maxLag && cmndf[tau + 1] < cmndf[tau]) {
                    bestTau = tau + 1
                    break
                }
                if (bestTau == -1) bestTau = tau
                break
            }
        }

        return if (bestTau > 0) sampleRate / bestTau else -1.0
    }

    /**
     * Jitter — cycle-to-cycle variation in pitch period.
     * Expressed as a percentage. Higher jitter indicates vocal tension/stress.
     *
     * Normal: < 1.0%
     * Stressed: > 1.5%
     */
    private fun calculateJitter(pitchValues: List<Double>): Double {
        if (pitchValues.size < 2) return 0.0

        // Convert pitches to periods
        val periods = pitchValues.map { 1.0 / it }
        val meanPeriod = periods.average()

        // Mean absolute difference between consecutive periods
        val diffs = (1 until periods.size).map { abs(periods[it] - periods[it - 1]) }
        val meanDiff = diffs.average()

        return if (meanPeriod > 0) (meanDiff / meanPeriod) * 100.0 else 0.0
    }

    /**
     * Shimmer — cycle-to-cycle variation in amplitude.
     * Expressed as a percentage. Higher shimmer suggests vocal instability.
     *
     * Normal: < 3.0%
     * Stressed: > 5.0%
     */
    private fun calculateShimmer(amplitudes: List<Double>): Double {
        if (amplitudes.size < 2) return 0.0

        val voicedAmplitudes = amplitudes.filter { it > 0.01 }
        if (voicedAmplitudes.size < 2) return 0.0

        val meanAmp = voicedAmplitudes.average()
        val diffs = (1 until voicedAmplitudes.size).map {
            abs(voicedAmplitudes[it] - voicedAmplitudes[it - 1])
        }
        val meanDiff = diffs.average()

        return if (meanAmp > 0) (meanDiff / meanAmp) * 100.0 else 0.0
    }

    /**
     * Estimate speech rate from energy contour.
     * Counts syllable-like energy peaks per second.
     */
    private fun estimateSpeechRate(amplitudes: List<Double>): Double {
        if (amplitudes.size < 3) return 0.0

        val smoothed = amplitudes.mapIndexed { i, _ ->
            val start = maxOf(0, i - 2)
            val end = minOf(amplitudes.size, i + 3)
            amplitudes.subList(start, end).average()
        }

        val threshold = smoothed.average() * 0.5
        var peaks = 0
        for (i in 1 until smoothed.size - 1) {
            if (smoothed[i] > smoothed[i - 1] &&
                smoothed[i] > smoothed[i + 1] &&
                smoothed[i] > threshold
            ) {
                peaks++
            }
        }

        val durationSec = amplitudes.size * HOP_SIZE.toDouble() / SAMPLE_RATE
        return if (durationSec > 0) peaks / durationSec else 0.0
    }

    /**
     * Compute voice stress score (0–100) from vocal biomarkers.
     *
     * High pitch + high jitter + high shimmer + high speech rate = HIGH STRESS
     */
    private fun computeVoiceStressScore(
        meanPitch: Double,
        jitter: Double,
        shimmer: Double,
        speechRate: Double
    ): Int {
        // Pitch stress component (higher pitch under stress)
        val pitchScore = when {
            meanPitch > 250 -> 80  // Very high pitch
            meanPitch > 200 -> 60
            meanPitch > 150 -> 40
            meanPitch > 100 -> 25
            else -> 15
        }

        // Jitter stress component (>1.5% is stressed)
        val jitterScore = when {
            jitter > 3.0 -> 90
            jitter > 2.0 -> 70
            jitter > 1.5 -> 55
            jitter > 1.0 -> 35
            else -> 15
        }

        // Shimmer stress component (>5% is stressed)
        val shimmerScore = when {
            shimmer > 8.0 -> 85
            shimmer > 5.0 -> 65
            shimmer > 3.0 -> 45
            shimmer > 1.5 -> 30
            else -> 15
        }

        // Speech rate stress component (rapid speech = stress)
        val rateScore = when {
            speechRate > 6.0 -> 80
            speechRate > 4.5 -> 60
            speechRate > 3.0 -> 40
            speechRate > 1.5 -> 25
            else -> 15
        }

        // Weighted combination
        val combined = (pitchScore * 0.3 + jitterScore * 0.3 +
                shimmerScore * 0.2 + rateScore * 0.2)

        return combined.toInt().coerceIn(0, 100)
    }

    /**
     * Container for voice analysis results.
     */
    data class VoiceResult(
        val meanPitch: Double,    // Hz — fundamental frequency
        val jitter: Double,       // % — pitch variation
        val shimmer: Double,      // % — amplitude variation
        val speechRate: Double,   // syllables/sec
        val stressScore: Int      // 0–100
    )
}
