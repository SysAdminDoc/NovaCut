package com.novacut.editor.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stub engine -- requires Wav2Lip GAN ONNX model. See ROADMAP.md Tier C.4.
 *
 * Generates lip-synced video by replacing a speaker's mouth region to match a new
 * audio track (e.g. translated voiceover or cloned-voice playback). Powers the
 * dubbing workflow together with [CaptionTranslationEngine] and [VoiceCloneEngine].
 *
 * Model licensing: Wav2Lip-GAN is research / non-commercial. Before release, audit
 * alternatives (e.g. MuseTalk, SadTalker) with permissive licenses.
 *
 * Model: wav2lip_gan.onnx ~300 MB, ~80 ms/frame on Snapdragon 8 Gen 3.
 */
@Singleton
class LipSyncEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {

    data class SyncConfig(
        /** Pad the detected mouth crop by this ratio to preserve chin/jaw context. */
        val cropPadding: Float = 0.12f,
        /** If true, detect face once and track; if false, detect each frame. */
        val useTracking: Boolean = true,
        /** Blend weight of generated mouth vs original (1.0 = full replace). */
        val blendStrength: Float = 1.0f
    ) {
        init {
            require(cropPadding in 0f..0.5f)
            require(blendStrength in 0f..1f)
        }
    }

    data class SyncResult(
        val outputUri: Uri,
        val framesProcessed: Int,
        val faceDetectionRate: Float,
        val processingTimeMs: Long
    )

    fun isModelReady(): Boolean = false

    suspend fun downloadModel(onProgress: (Float) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "downloadModel: stub -- requires Wav2Lip ONNX model")
        false
    }

    /**
     * Generate a lip-synced version of [videoUri] driven by [audioUri], writing the
     * result to [outputUri]. If no face is detected for a frame, that frame is passed
     * through unchanged (use [SyncResult.faceDetectionRate] to surface partial coverage).
     */
    suspend fun lipSync(
        videoUri: Uri,
        audioUri: Uri,
        outputUri: Uri,
        config: SyncConfig = SyncConfig(),
        onProgress: (Float) -> Unit = {}
    ): SyncResult? = withContext(Dispatchers.IO) {
        Log.d(TAG, "lipSync: stub -- requires Wav2Lip ONNX model")
        null
    }

    companion object {
        private const val TAG = "LipSync"
    }
}
