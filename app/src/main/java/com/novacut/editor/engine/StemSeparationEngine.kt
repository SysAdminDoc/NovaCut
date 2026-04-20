package com.novacut.editor.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stub engine -- requires Demucs v4 ONNX model. See ROADMAP.md Tier C.1.
 *
 * Separates a music track into isolated stems (vocals, drums, bass, other) via
 * Hybrid Transformer Demucs (htdemucs). Runs on ONNX Runtime (already an app dep).
 *
 * Model: htdemucs_ft.onnx, ~80 MB quantised, ~1.5s/sec audio on Snapdragon 8 Gen 2.
 */
@Singleton
class StemSeparationEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {

    enum class Stem(val displayName: String) {
        VOCALS("Vocals"),
        DRUMS("Drums"),
        BASS("Bass"),
        OTHER("Other (melody / harmony)")
    }

    data class SeparationResult(
        val stemUris: Map<Stem, Uri>,
        val processingTimeMs: Long,
        val durationMs: Long
    )

    private val _modelState = MutableStateFlow(ModelState.NOT_DOWNLOADED)
    val modelState: StateFlow<ModelState> = _modelState

    enum class ModelState { NOT_DOWNLOADED, DOWNLOADING, READY, ERROR }

    fun isModelReady(): Boolean = false

    suspend fun downloadModel(onProgress: (Float) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "downloadModel: stub -- requires Demucs v4 ONNX model")
        false
    }

    /**
     * Separate a source audio/video clip into isolated stems written to [outputDir].
     * Each stem becomes its own WAV file that can be imported to a new audio track.
     *
     * @param requestedStems Subset of stems to extract. Smaller subsets are not faster
     *   (the model always outputs all four); this just filters which files get written.
     * @return null when the model is not available.
     */
    suspend fun separate(
        sourceUri: Uri,
        outputDir: Uri,
        requestedStems: Set<Stem> = Stem.values().toSet(),
        onProgress: (Float) -> Unit = {}
    ): SeparationResult? = withContext(Dispatchers.IO) {
        Log.d(TAG, "separate: stub -- requires Demucs v4 ONNX model")
        null
    }

    companion object {
        private const val TAG = "StemSeparation"
    }
}
