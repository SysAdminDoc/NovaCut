package com.novacut.editor.engine

import android.content.Context
import android.util.Log
import com.novacut.editor.engine.whisper.SherpaAsrEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stub engine -- requires NLLB-200 distilled or MADLAD-400 Q4 ONNX. See ROADMAP.md Tier C.5.
 *
 * Translates existing caption text across 200 languages on-device. Preserves word
 * timing when the target word count is similar to the source; otherwise proportionally
 * redistributes timings so karaoke highlighting stays readable.
 *
 * Model options:
 *  - NLLB-200 distilled 600M Q4: ~600 MB, 200 languages, quality balance.
 *  - MADLAD-400 3B Q4: ~1.5 GB, 400 languages, highest quality.
 *  - NLLB-200 distilled 300M Q8: ~350 MB, lower quality, fastest.
 */
@Singleton
class CaptionTranslationEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {

    enum class ModelVariant(val displayName: String, val sizeMb: Int, val languageCount: Int) {
        NLLB_300M("NLLB-200 300M distilled", 350, 200),
        NLLB_600M("NLLB-200 600M distilled", 600, 200),
        MADLAD_400_3B("MADLAD-400 3B Q4", 1500, 400)
    }

    data class TranslatedSegment(
        val sourceText: String,
        val targetText: String,
        val startTimeMs: Long,
        val endTimeMs: Long,
        val words: List<SherpaAsrEngine.WordTimestamp> = emptyList()
    )

    private val _modelState = MutableStateFlow(ModelState.NOT_DOWNLOADED)
    val modelState: StateFlow<ModelState> = _modelState

    enum class ModelState { NOT_DOWNLOADED, DOWNLOADING, READY, ERROR }

    fun isModelReady(): Boolean = false

    fun getSupportedLanguages(variant: ModelVariant = ModelVariant.NLLB_600M): List<String> =
        when (variant) {
            ModelVariant.NLLB_300M, ModelVariant.NLLB_600M -> NLLB_LANGUAGES
            ModelVariant.MADLAD_400_3B -> MADLAD_LANGUAGES
        }

    suspend fun downloadModel(
        variant: ModelVariant = ModelVariant.NLLB_600M,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "downloadModel: stub -- requires ${variant.displayName}")
        false
    }

    /**
     * Translate a list of caption segments. Word timings are re-interpolated
     * based on target word count so downstream karaoke rendering keeps working.
     */
    suspend fun translate(
        segments: List<SherpaAsrEngine.TranscriptionSegment>,
        sourceLang: String,
        targetLang: String,
        onProgress: (Float) -> Unit = {}
    ): List<TranslatedSegment> = withContext(Dispatchers.Default) {
        Log.d(TAG, "translate: stub -- $sourceLang -> $targetLang (${segments.size} segments)")
        segments.map { seg ->
            TranslatedSegment(
                sourceText = seg.text,
                targetText = seg.text,
                startTimeMs = seg.startTimeMs,
                endTimeMs = seg.endTimeMs,
                words = seg.words
            )
        }
    }

    companion object {
        private const val TAG = "CaptionTranslate"

        // Abbreviated: production list is the full 200/400.
        val NLLB_LANGUAGES = listOf(
            "en", "es", "fr", "de", "it", "pt", "nl", "ru", "pl", "uk", "cs", "ro",
            "zh", "ja", "ko", "ar", "he", "tr", "fa", "hi", "bn", "ta", "te", "vi",
            "th", "id", "ms", "tl", "sw", "am", "yo", "zu", "ha", "ig", "af"
        )
        val MADLAD_LANGUAGES = NLLB_LANGUAGES + listOf(
            "co", "la", "eo", "cy", "gd", "ga", "mt", "is", "fo", "lb", "rm"
        )
    }
}
