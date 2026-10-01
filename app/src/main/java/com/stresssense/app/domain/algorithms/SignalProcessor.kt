package com.stresssense.app.domain.algorithms

import javax.inject.Inject
import kotlin.math.*

/**
 * Digital signal processing pipeline for PPG waveforms.
 *
 * Takes raw red-channel time series from PPGProcessor and produces
 * clean, filtered signals with detected peaks (heartbeats).
 *
 * Pipeline:
 * 1. Detrending — remove DC offset via moving-average subtraction
 * 2. Bandpass filter — Butterworth 0.7–4 Hz (42–240 BPM)
 * 3. Normalization — min-max scale to [0, 1]
 * 4. Peak detection — local maxima with minimum distance constraint
 */
class SignalProcessor @Inject constructor() {

    companion object {
        /** Moving average window size for detrending (samples) */
        const val DETREND_WINDOW = 15

        /** Butterworth filter order */
        const val FILTER_ORDER = 2

        /** Lower cutoff frequency in Hz (42 BPM) */
        const val LOW_CUTOFF_HZ = 0.7

        /** Upper cutoff frequency in Hz (240 BPM) */
        const val HIGH_CUTOFF_HZ = 4.0

        /** Minimum distance between peaks in seconds */
        const val MIN_PEAK_DISTANCE_SEC = 0.4
    }

    /**
     * Full processing pipeline: detrend → filter → normalize → detect peaks.
     *
     * @param rawSignal     List of raw red-channel intensity values
     * @param timestamps    Corresponding timestamps in milliseconds
     * @param samplingRate  Actual sampling rate in Hz (typically ~30)
     * @return ProcessedSignal containing filtered data and detected peak indices
     */
    fun process(
        rawSignal: List<Double>,
        timestamps: List<Long>,
        samplingRate: Double = 30.0
    ): ProcessedSignal {
        if (rawSignal.size < 20) {
            return ProcessedSignal(emptyList(), emptyList(), emptyList())
        }

        // Step 1: Detrend
        val detrended = detrend(rawSignal)

        // Step 2: Bandpass filter
        val filtered = bandpassFilter(detrended, samplingRate)

        // Step 3: Normalize
        val normalized = normalize(filtered)

        // Step 4: Peak detection
        val minDistanceSamples = (MIN_PEAK_DISTANCE_SEC * samplingRate).toInt()
        val peakIndices = detectPeaks(normalized, minDistanceSamples)

        return ProcessedSignal(
            filteredSignal = normalized,
            peakIndices = peakIndices,
            timestamps = timestamps
        )
    }

    /**
     * Remove slow baseline drift by subtracting a moving average.
     * This eliminates DC offset and low-frequency motion artifacts.
     */
    fun detrend(signal: List<Double>): List<Double> {
        val halfWindow = DETREND_WINDOW / 2
        return signal.mapIndexed { index, value ->
            val start = maxOf(0, index - halfWindow)
            val end = minOf(signal.size - 1, index + halfWindow)
            val windowMean = signal.subList(start, end + 1).average()
            value - windowMean
        }
    }

    /**
     * Second-order Butterworth bandpass filter (0.7–4 Hz).
     *
     * Uses cascaded biquad sections implemented via Direct Form II.
     * Pre-warps cutoff frequencies for bilinear transform accuracy.
     */
    fun bandpassFilter(signal: List<Double>, samplingRate: Double): List<Double> {
        // Design Butterworth bandpass filter coefficients
        val nyquist = samplingRate / 2.0
        val lowNorm = LOW_CUTOFF_HZ / nyquist
        val highNorm = HIGH_CUTOFF_HZ / nyquist

        // Pre-warp frequencies for bilinear transform
        val wLow = tan(PI * lowNorm)
        val wHigh = tan(PI * highNorm)
        val bw = wHigh - wLow
        val w0 = sqrt(wLow * wHigh)

        // Second-order bandpass section coefficients
        val q = w0 / bw
        val alpha = w0 * bw
        val norm = 1.0 + alpha + w0 * w0

        val b0 = alpha / norm
        val b1 = 0.0
        val b2 = -alpha / norm
        val a1 = (2.0 * (w0 * w0 - 1.0)) / norm
        val a2 = (1.0 - alpha + w0 * w0) / norm

        // Apply filter forwards
        val forward = applyBiquad(signal, b0, b1, b2, a1, a2)

        // Apply filter backwards (zero-phase filtering)
        val reversed = forward.reversed()
        val backward = applyBiquad(reversed, b0, b1, b2, a1, a2)

        return backward.reversed()
    }

    /**
     * Apply a single biquad filter section using Direct Form II transposed.
     */
    private fun applyBiquad(
        signal: List<Double>,
        b0: Double, b1: Double, b2: Double,
        a1: Double, a2: Double
    ): List<Double> {
        val output = DoubleArray(signal.size)
        var z1 = 0.0
        var z2 = 0.0

        for (i in signal.indices) {
            val input = signal[i]
            val out = b0 * input + z1
            z1 = b1 * input - a1 * out + z2
            z2 = b2 * input - a2 * out
            output[i] = out
        }

        return output.toList()
    }

    /**
     * Min-max normalization to [0, 1] range.
     */
    fun normalize(signal: List<Double>): List<Double> {
        if (signal.isEmpty()) return emptyList()
        val min = signal.min()
        val max = signal.max()
        val range = max - min
        if (range < 1e-10) return signal.map { 0.5 }
        return signal.map { (it - min) / range }
    }

    /**
     * Detect local maxima (peaks) in the signal with a minimum distance constraint.
     *
     * A peak is a point higher than both its neighbors, filtered by:
     * 1. Minimum distance between consecutive peaks
     * 2. Minimum amplitude (0.3 of normalized range) to reject noise
     *
     * @param signal             Normalized signal values
     * @param minDistanceSamples Minimum number of samples between peaks
     * @return List of peak indices
     */
    fun detectPeaks(signal: List<Double>, minDistanceSamples: Int): List<Int> {
        if (signal.size < 3) return emptyList()

        // Find all local maxima above threshold
        val candidates = mutableListOf<Int>()
        val threshold = 0.3

        for (i in 1 until signal.size - 1) {
            if (signal[i] > signal[i - 1] &&
                signal[i] > signal[i + 1] &&
                signal[i] > threshold
            ) {
                candidates.add(i)
            }
        }

        // Enforce minimum distance: keep the tallest peak in each window
        val peaks = mutableListOf<Int>()
        for (candidate in candidates) {
            if (peaks.isEmpty()) {
                peaks.add(candidate)
            } else {
                val lastPeak = peaks.last()
                if (candidate - lastPeak >= minDistanceSamples) {
                    peaks.add(candidate)
                } else if (signal[candidate] > signal[lastPeak]) {
                    // Replace last peak if this one is taller
                    peaks[peaks.size - 1] = candidate
                }
            }
        }

        return peaks
    }

    /**
     * Container for the fully processed PPG signal.
     */
    data class ProcessedSignal(
        val filteredSignal: List<Double>,
        val peakIndices: List<Int>,
        val timestamps: List<Long>
    ) {
        /** Calculate R-R intervals in milliseconds from detected peaks */
        fun calculateRRIntervals(): List<Double> {
            if (peakIndices.size < 2 || timestamps.isEmpty()) return emptyList()

            return (1 until peakIndices.size).map { i ->
                val t1 = timestamps[peakIndices[i - 1]]
                val t2 = timestamps[peakIndices[i]]
                (t2 - t1).toDouble()
            }
        }
    }
}
