package com.novacut.editor.engine

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stub engine -- requires com.google.oboe:oboe. See ROADMAP.md Tier A.10.
 *
 * High-quality sinc-based sample rate conversion via Oboe's resampler, used to
 * mix 44.1 kHz music with 48 kHz video audio without the aliasing artefacts of
 * naive linear resampling.
 *
 * Oboe dependency (add to app/build.gradle.kts when ready):
 *   implementation("com.google.oboe:oboe:1.9.0")
 *
 * Falls back to Media3's built-in resampler when unavailable (current behaviour).
 */
@Singleton
class OboeResamplerEngine @Inject constructor() {

    enum class Quality {
        /** Linear interpolation. Lowest quality, zero cost. */
        FASTEST,
        /** 8-point sinc. Adequate for non-critical audio. */
        LOW,
        /** 16-point sinc. Default. */
        MEDIUM,
        /** 32-point sinc. Use for music tracks. */
        HIGH,
        /** 64-point sinc. Archival quality. */
        BEST
    }

    /** Whether native Oboe resampler is available. */
    fun isAvailable(): Boolean = false

    /**
     * Resample an interleaved float PCM buffer from [fromSampleRate] to [toSampleRate].
     * Returns null when native resampler is unavailable -- callers must fall back.
     */
    fun resample(
        input: FloatArray,
        channels: Int,
        fromSampleRate: Int,
        toSampleRate: Int,
        quality: Quality = Quality.MEDIUM
    ): FloatArray? {
        Log.d(TAG, "resample: stub -- requires Oboe (${input.size} samples, ${fromSampleRate}->${toSampleRate} Hz)")
        return null
    }

    companion object {
        private const val TAG = "OboeResampler"
    }
}
