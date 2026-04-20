package com.novacut.editor.engine

import com.novacut.editor.engine.whisper.SherpaAsrEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SilenceDetectionEngineTest {

    private val engine = SilenceDetectionEngine()

    @Test
    fun detectSilences_emptyWaveform_returnsEmptyList() {
        val result = engine.detectSilences(FloatArray(0), 44100)
        assertTrue(result.isEmpty())
    }

    @Test
    fun detectSilences_zeroSampleRate_returnsEmptyList() {
        val result = engine.detectSilences(FloatArray(44100), 0)
        assertTrue(result.isEmpty())
    }

    @Test
    fun detectSilences_allSilent_producesOneRange() {
        val wave = FloatArray(44100) { 0f } // 1s @ 44.1 kHz all silence
        val result = engine.detectSilences(
            wave, 44100,
            SilenceDetectionEngine.AutoCutConfig(minSilenceMs = 500L, paddingMs = 0L)
        )
        assertEquals(1, result.size)
        assertEquals(SilenceDetectionEngine.CutProposal.Reason.SILENCE, result[0].reason)
        assertEquals(0L, result[0].startMs)
        assertEquals(1000L, result[0].endMs)
    }

    @Test
    fun detectSilences_loudAudio_producesEmpty() {
        val wave = FloatArray(44100) { 0.5f } // loud throughout
        val result = engine.detectSilences(wave, 44100)
        assertTrue(result.isEmpty())
    }

    @Test
    fun detectSilences_shortGap_belowThreshold_ignored() {
        // 50 ms silence -- below default 500 ms minimum
        val sampleRate = 44100
        val wave = FloatArray(sampleRate).apply {
            fill(0.5f) // loud
            for (i in 0 until sampleRate * 50 / 1000) this[i + 1000] = 0f
        }
        val result = engine.detectSilences(wave, sampleRate)
        assertTrue(result.isEmpty())
    }

    @Test
    fun detectSilences_paddingLargerThanRun_skipsProposal() {
        // 200 ms silence, 500 ms padding -- padding eats the whole range
        val sampleRate = 44100
        val wave = FloatArray(sampleRate).apply { fill(0.5f) }
        for (i in 0 until sampleRate * 200 / 1000) wave[i + 5000] = 0f
        val result = engine.detectSilences(
            wave, sampleRate,
            SilenceDetectionEngine.AutoCutConfig(minSilenceMs = 100L, paddingMs = 500L)
        )
        // padding > silence -- no valid proposal
        assertTrue(result.isEmpty())
    }

    @Test
    fun detectSilences_doesNotOverflowOnLargeMinSilence() {
        // With 48kHz * huge minSilenceMs, naive Int math would overflow.
        // The engine must stay in Long space and clamp to waveform bounds.
        val wave = FloatArray(1000) { 0f }
        val result = engine.detectSilences(
            wave, 48000,
            SilenceDetectionEngine.AutoCutConfig(
                minSilenceMs = 60_000L * 60 * 24 * 30, // 30 days
                paddingMs = 0L
            )
        )
        // minSilenceSamples is clamped to waveform.size, so a 1000-sample all-silent
        // run meets its own clamped threshold and yields one proposal.
        assertEquals(1, result.size)
    }

    @Test
    fun detectFillerWords_disabledConfig_returnsEmpty() {
        val words = listOf(
            SherpaAsrEngine.WordTimestamp("um", 100, 200)
        )
        val result = engine.detectFillerWords(
            words,
            SilenceDetectionEngine.AutoCutConfig(cutFillerWords = false)
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun detectFillerWords_matchesCaseInsensitive() {
        val words = listOf(
            SherpaAsrEngine.WordTimestamp("Um,", 100, 200),
            SherpaAsrEngine.WordTimestamp("hello", 200, 500),
            SherpaAsrEngine.WordTimestamp("UH", 500, 600)
        )
        val result = engine.detectFillerWords(words)
        assertEquals(2, result.size)
        assertEquals("um", result[0].matchedText)
        assertEquals("uh", result[1].matchedText)
    }

    @Test
    fun detectFillerWords_paddingClampedAtZero() {
        val words = listOf(SherpaAsrEngine.WordTimestamp("um", 50, 80))
        val result = engine.detectFillerWords(
            words,
            SilenceDetectionEngine.AutoCutConfig(paddingMs = 200L)
        )
        assertEquals(1, result.size)
        // startMs - paddingMs would be negative; coerced to 0.
        assertEquals(0L, result[0].startMs)
    }

    @Test
    fun defaultFillers_containNoMultiWordEntries() {
        // Whisper emits word-by-word; multi-word fillers can never match the
        // single-token matcher in detectFillerWords. Regression guard against
        // anyone adding "you know" back to the default set.
        SilenceDetectionEngine.DEFAULT_FILLERS.forEach { filler ->
            assertTrue(
                "Default filler '$filler' must be a single token -- multi-word fillers " +
                    "would silently never match Whisper word output.",
                !filler.contains(' ')
            )
        }
    }
}
