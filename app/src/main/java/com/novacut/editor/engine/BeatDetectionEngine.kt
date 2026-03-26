package com.novacut.editor.engine

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Beat and onset detection engine.
 * Primary: aubio via NDK (when integrated)
 * Fallback: Pure-Kotlin spectral flux onset detection
 *
 * aubio NDK dependency (add when ready):
 *   Prebuilt: github.com/adamski/aubio-android
 *   Or compile from source: aubio.org with scripts/build_android
 *
 * Usage:
 *   val beats = beatDetectionEngine.detectBeats(uri)
 *   // beats = list of timestamps in ms where beats occur
 */
@Singleton
class BeatDetectionEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioEngine: AudioEngine
) {
    data class BeatInfo(
        val timestampMs: Long,
        val strength: Float,      // 0-1 beat strength/confidence
        val isDownbeat: Boolean = false  // true for bar-start beats
    )

    data class BeatAnalysis(
        val beats: List<BeatInfo>,
        val bpm: Float,
        val timeSignature: Int = 4  // beats per bar (usually 4)
    )

    /**
     * Detect beats in audio from a media URI.
     * Converts to mono 22050Hz for analysis (4x less computation than 44.1kHz stereo).
     *
     * Algorithm (spectral flux onset detection):
     * 1. Compute STFT with hop size 512 (23ms at 22050Hz)
     * 2. Calculate spectral flux (sum of positive magnitude differences)
     * 3. Adaptive threshold: median filter + offset
     * 4. Pick peaks above threshold as onsets
     * 5. Estimate BPM from inter-onset intervals via histogram
     */
    suspend fun detectBeats(
        uri: Uri,
        onProgress: (Float) -> Unit = {}
    ): BeatAnalysis = withContext(Dispatchers.Default) {
        onProgress(0.1f)

        // Extract waveform at 22050Hz mono for faster processing
        val waveform = withContext(Dispatchers.IO) {
            audioEngine.extractWaveform(uri, 22050)
        }
        onProgress(0.3f)

        if (waveform.isEmpty()) return@withContext BeatAnalysis(emptyList(), 0f)

        val sampleRate = 22050
        val hopSize = 512
        val windowSize = 1024

        // Compute spectral flux
        val flux = computeSpectralFlux(waveform, windowSize, hopSize)
        onProgress(0.6f)

        // Adaptive thresholding with median filter
        val medianWindow = 15  // ~350ms at this hop size
        val thresholdOffset = 0.1f
        val onsets = mutableListOf<BeatInfo>()

        for (i in flux.indices) {
            ensureActive()
            val start = maxOf(0, i - medianWindow / 2)
            val end = minOf(flux.size, i + medianWindow / 2 + 1)
            val window = flux.slice(start until end).sorted()
            val median = window[window.size / 2]
            val threshold = median + thresholdOffset

            if (flux[i] > threshold && flux[i] > 0.01f) {
                // Check it's a local peak (not just above threshold)
                val isPeak = (i == 0 || flux[i] >= flux[i - 1]) &&
                             (i == flux.size - 1 || flux[i] >= flux[i + 1])
                if (isPeak) {
                    val timestampMs = (i.toLong() * hopSize * 1000L) / sampleRate
                    onsets.add(BeatInfo(timestampMs, flux[i].coerceIn(0f, 1f)))
                }
            }
        }
        onProgress(0.8f)

        // Estimate BPM from inter-onset intervals
        val bpm = estimateBpm(onsets, sampleRate)

        // Mark downbeats (every timeSignature beats)
        val beatsWithDownbeats = if (onsets.size >= 4) {
            onsets.mapIndexed { idx, beat ->
                beat.copy(isDownbeat = idx % 4 == 0)
            }
        } else onsets

        onProgress(1f)
        BeatAnalysis(beatsWithDownbeats, bpm)
    }

    /**
     * Compute spectral flux: sum of positive magnitude differences between consecutive frames.
     */
    private fun computeSpectralFlux(
        samples: FloatArray,
        windowSize: Int,
        hopSize: Int
    ): FloatArray {
        val numFrames = (samples.size - windowSize) / hopSize
        if (numFrames <= 1) return floatArrayOf()

        val flux = FloatArray(numFrames)
        var prevMagnitudes = FloatArray(windowSize / 2 + 1)

        for (frame in 0 until numFrames) {
            val offset = frame * hopSize

            // Apply Hann window and compute magnitude spectrum (simplified DFT for key bins)
            val magnitudes = FloatArray(windowSize / 2 + 1)
            val numBins = minOf(64, windowSize / 2 + 1) // Analyze first 64 bins for speed

            for (k in 0 until numBins) {
                var real = 0f
                var imag = 0f
                for (n in 0 until windowSize) {
                    val hannWindow = 0.5f * (1f - kotlin.math.cos(2f * Math.PI.toFloat() * n / windowSize))
                    val sample = if (offset + n < samples.size) samples[offset + n] * hannWindow else 0f
                    val angle = 2f * Math.PI.toFloat() * k * n / windowSize
                    real += sample * kotlin.math.cos(angle)
                    imag += sample * kotlin.math.sin(angle)
                }
                magnitudes[k] = sqrt(real * real + imag * imag)
            }

            // Spectral flux = sum of positive differences
            var sf = 0f
            for (k in 0 until numBins) {
                val diff = magnitudes[k] - prevMagnitudes[k]
                if (diff > 0) sf += diff
            }
            flux[frame] = sf

            prevMagnitudes = magnitudes
        }

        // Normalize flux
        val maxFlux = flux.maxOrNull() ?: 1f
        if (maxFlux > 0) {
            for (i in flux.indices) flux[i] /= maxFlux
        }

        return flux
    }

    /**
     * Estimate BPM from beat intervals using histogram voting.
     */
    private fun estimateBpm(beats: List<BeatInfo>, sampleRate: Int): Float {
        if (beats.size < 3) return 0f

        // Compute inter-onset intervals
        val intervals = mutableListOf<Long>()
        for (i in 1 until beats.size) {
            val interval = beats[i].timestampMs - beats[i - 1].timestampMs
            if (interval in 200..2000) { // 30-300 BPM range
                intervals.add(interval)
            }
        }
        if (intervals.isEmpty()) return 0f

        // Histogram voting for most common interval (10ms bins)
        val histogram = mutableMapOf<Long, Int>()
        for (interval in intervals) {
            val binned = (interval / 10) * 10
            histogram[binned] = (histogram[binned] ?: 0) + 1
        }

        val bestInterval = histogram.maxByOrNull { it.value }?.key ?: return 0f
        return (60000f / bestInterval).coerceIn(30f, 300f)
    }
}
