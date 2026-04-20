package com.novacut.editor.engine

import android.util.Log
import com.novacut.editor.engine.whisper.SherpaAsrEngine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Descript-style auto-cut proposer. See ROADMAP.md Tier C.2.
 *
 * Two detection passes:
 *  1. Silence: consecutive samples with |amplitude| below [silenceThreshold] for
 *     longer than [minSilenceMs]. Uses RMS from the existing waveform.
 *  2. Filler words: scans Whisper word-level timestamps for filler tokens
 *     (um, uh, like, you know, etc.) and proposes a cut range per occurrence.
 *
 * Returns cut proposals; UI confirms before the ViewModel applies them as
 * back-to-back `splitClipAt` + `deleteClip` commands so the result is fully
 * undo-able.
 */
@Singleton
class SilenceDetectionEngine @Inject constructor() {

    data class CutProposal(
        val startMs: Long,
        val endMs: Long,
        val reason: Reason,
        val matchedText: String? = null
    ) {
        enum class Reason { SILENCE, FILLER_WORD }

        val durationMs: Long get() = endMs - startMs
    }

    data class AutoCutConfig(
        /** RMS threshold below which a sample is considered silent. [0, 1]. */
        val silenceThreshold: Float = 0.02f,
        /** Minimum silence duration to propose a cut. Shorter silences are ignored. */
        val minSilenceMs: Long = 500L,
        /** Safety margin kept at both ends of a silence range (don't clip actual speech). */
        val paddingMs: Long = 80L,
        /** If true, propose cuts for filler words found in the transcript. */
        val cutFillerWords: Boolean = true,
        /** Filler tokens to match. Case-insensitive exact word match on Whisper output. */
        val fillerWords: Set<String> = DEFAULT_FILLERS
    ) {
        init {
            require(silenceThreshold in 0f..1f) { "silenceThreshold must be in [0, 1]" }
            require(minSilenceMs >= 50L) { "minSilenceMs must be >= 50" }
            require(paddingMs >= 0L) { "paddingMs must be >= 0" }
        }
    }

    /**
     * Scan an audio waveform for silent ranges.
     *
     * @param waveform Normalised amplitudes in [-1, 1].
     * @param sampleRate Hz.
     * @param config Thresholds and padding.
     */
    fun detectSilences(
        waveform: FloatArray,
        sampleRate: Int,
        config: AutoCutConfig = AutoCutConfig()
    ): List<CutProposal> {
        if (waveform.isEmpty() || sampleRate <= 0) return emptyList()
        // Stay in Long space until we know the value fits, so pathological thresholds
        // (e.g. a user-entered minSilenceMs in the hours at 48 kHz) can't silently
        // wrap Int.MAX_VALUE and surface as a negative-length run.
        val minSilenceSamplesLong = (config.minSilenceMs * sampleRate.toLong() / 1000L)
            .coerceIn(1L, waveform.size.toLong())
        val minSilenceSamples = minSilenceSamplesLong.toInt()
        val paddingSamples = (config.paddingMs * sampleRate.toLong() / 1000L)
            .coerceIn(0L, waveform.size.toLong())
            .toInt()
        val out = mutableListOf<CutProposal>()
        var runStart = -1
        var i = 0
        while (i < waveform.size) {
            val isSilent = kotlin.math.abs(waveform[i]) < config.silenceThreshold
            if (isSilent && runStart < 0) {
                runStart = i
            } else if (!isSilent && runStart >= 0) {
                val runLen = i - runStart
                if (runLen >= minSilenceSamples) {
                    val startSample = (runStart + paddingSamples).coerceAtMost(i)
                    val endSample = (i - paddingSamples).coerceAtLeast(startSample)
                    if (endSample > startSample) {
                        out += CutProposal(
                            startMs = startSample.toLong() * 1000L / sampleRate,
                            endMs = endSample.toLong() * 1000L / sampleRate,
                            reason = CutProposal.Reason.SILENCE
                        )
                    }
                }
                runStart = -1
            }
            i++
        }
        if (runStart >= 0 && waveform.size - runStart >= minSilenceSamples) {
            val startSample = (runStart + paddingSamples).coerceAtMost(waveform.size)
            val endSample = (waveform.size - paddingSamples).coerceAtLeast(startSample)
            if (endSample > startSample) {
                out += CutProposal(
                    startMs = startSample.toLong() * 1000L / sampleRate,
                    endMs = endSample.toLong() * 1000L / sampleRate,
                    reason = CutProposal.Reason.SILENCE
                )
            }
        }
        Log.d(TAG, "detectSilences: ${out.size} silences proposed")
        return out
    }

    /**
     * Scan Whisper word timestamps for filler tokens.
     *
     * Whisper emits punctuation and capitalisation; we lowercase and strip punctuation
     * before matching so "Um," / "um." / "UM" all hit the "um" entry in [config.fillerWords].
     */
    fun detectFillerWords(
        words: List<SherpaAsrEngine.WordTimestamp>,
        config: AutoCutConfig = AutoCutConfig()
    ): List<CutProposal> {
        if (!config.cutFillerWords) return emptyList()
        return words.mapNotNull { w ->
            val token = w.word.lowercase().trim { it.isWhitespace() || it in PUNCTUATION }
            if (token in config.fillerWords) {
                CutProposal(
                    startMs = (w.startTimeMs - config.paddingMs).coerceAtLeast(0L),
                    endMs = w.endTimeMs + config.paddingMs,
                    reason = CutProposal.Reason.FILLER_WORD,
                    matchedText = token
                )
            } else null
        }.also { Log.d(TAG, "detectFillerWords: ${it.size} fillers proposed") }
    }

    companion object {
        private const val TAG = "SilenceDetect"
        private const val PUNCTUATION = ".,!?;:-\"'()"

        /**
         * Single-token filler set. Multi-word fillers ("you know", "i mean", "sort of")
         * are intentionally excluded because Whisper emits one timestamp per whitespace-
         * separated token -- a multi-word pattern would need a sliding-window matcher
         * over adjacent [SherpaAsrEngine.WordTimestamp] pairs, which is a follow-up.
         */
        val DEFAULT_FILLERS: Set<String> = setOf(
            "um", "uh", "er", "ah", "hmm", "mhm", "like",
            "basically", "literally", "right", "so", "well", "actually"
        )
    }
}
