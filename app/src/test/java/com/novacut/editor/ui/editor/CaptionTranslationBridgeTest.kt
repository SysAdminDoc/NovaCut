package com.novacut.editor.ui.editor

import com.novacut.editor.engine.CaptionTranslationEngine
import com.novacut.editor.model.Caption
import com.novacut.editor.model.CaptionWord
import org.junit.Assert.assertEquals
import org.junit.Test

class CaptionTranslationBridgeTest {

    @Test
    fun captionsToTranslationSegments_sortsAndPreservesWordTiming() {
        val late = Caption(
            text = "second",
            startTimeMs = 2_000L,
            endTimeMs = 3_000L,
        )
        val early = Caption(
            text = "first",
            startTimeMs = 100L,
            endTimeMs = 900L,
            words = listOf(CaptionWord("first", startTimeMs = 120L, endTimeMs = 400L, confidence = 0.8f)),
        )

        val segments = captionsToTranslationSegments(listOf(late, early))

        assertEquals(2, segments.size)
        assertEquals("first", segments[0].text)
        assertEquals(100L, segments[0].startTimeMs)
        assertEquals(900L, segments[0].endTimeMs)
        assertEquals("first", segments[0].words.single().word)
        assertEquals(0.8f, segments[0].words.single().confidence)
        assertEquals("second", segments[1].text)
    }

    @Test
    fun captionsToTranslationSegments_dropsBlankCaptions() {
        val segments = captionsToTranslationSegments(
            listOf(
                Caption(text = " ", startTimeMs = 0L, endTimeMs = 100L),
                Caption(text = "keep", startTimeMs = 100L, endTimeMs = 200L),
            )
        )

        assertEquals(1, segments.size)
        assertEquals("keep", segments.single().text)
    }

    @Test
    fun translatedSegmentToTranscriptionSegment_usesSourceTextForRegenerate() {
        val translated = CaptionTranslationEngine.TranslatedSegment(
            sourceText = "source",
            targetText = "edited target",
            startTimeMs = 10L,
            endTimeMs = 20L,
        )

        val segment = translatedSegmentToTranscriptionSegment(translated)

        assertEquals("source", segment.text)
        assertEquals(10L, segment.startTimeMs)
        assertEquals(20L, segment.endTimeMs)
    }
}
