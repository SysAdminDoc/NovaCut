package com.novacut.editor.engine

import com.novacut.editor.engine.CaptionTranslationEngine.EditorRowState
import com.novacut.editor.engine.CaptionTranslationEngine.LanguagePairQuality
import com.novacut.editor.engine.CaptionTranslationEngine.ModelVariant
import com.novacut.editor.engine.CaptionTranslationEngine.TranslatedSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the R5.4a / R6.7 caption-translation editor-row helpers that the
 * future Compose translation panel will consume.
 *
 * Engine behaviour today is still stubbed — `translate()` echoes source to
 * target — but the editor-row state machine + the immutable list helpers
 * (`applyUserEdit`, `markRegeneratePending`, `completeRegenerate`) are
 * pure and worth pinning before UI lands.
 */
class CaptionTranslationEditorRowsTest {

    private val engine = CaptionTranslationEngine(context = stubContext())

    private fun stubContext(): android.content.Context =
        object : android.content.ContextWrapper(null) {}

    private fun seg(
        source: String,
        target: String = source,
        state: EditorRowState = EditorRowState.TRANSLATED,
        startMs: Long = 0L,
        endMs: Long = 1_000L,
    ): TranslatedSegment = TranslatedSegment(
        sourceText = source,
        targetText = target,
        startTimeMs = startMs,
        endTimeMs = endMs,
        editorState = state,
    )

    @Test
    fun buildEditorRows_attachesIndexAndQuality() {
        val segments = listOf(seg("Hello"), seg("World"))
        val rows = engine.buildEditorRows(
            segments = segments,
            variant = ModelVariant.MADLAD_400_3B,
            sourceLang = "en",
            targetLang = "es",
        )
        assertEquals(2, rows.size)
        assertEquals(0, rows[0].index)
        assertEquals(1, rows[1].index)
        // Same quality across the pair (it's a (variant, src, tgt) constant).
        assertEquals(LanguagePairQuality.EXCELLENT, rows[0].quality)
        assertEquals(LanguagePairQuality.EXCELLENT, rows[1].quality)
        // Segment is the same reference — no copy required.
        assertSame(segments[0], rows[0].segment)
    }

    @Test
    fun buildEditorRows_propagatesPendingAndEditedFlags() {
        val segments = listOf(
            seg("a", state = EditorRowState.TRANSLATED),
            seg("b", state = EditorRowState.USER_EDITED),
            seg("c", state = EditorRowState.REGENERATE_PENDING),
        )
        val rows = engine.buildEditorRows(
            segments,
            ModelVariant.NLLB_600M,
            sourceLang = "en",
            targetLang = "ja",
        )
        assertFalse(rows[0].isUserEdited)
        assertFalse(rows[0].isPendingRegenerate)
        assertTrue(rows[1].isUserEdited)
        assertFalse(rows[1].isPendingRegenerate)
        assertFalse(rows[2].isUserEdited)
        assertTrue(rows[2].isPendingRegenerate)
    }

    @Test
    fun applyUserEdit_changesOnlyTargetAndState() {
        val segments = listOf(seg("foo"), seg("bar"))
        val edited = engine.applyUserEdit(segments, index = 0, newTargetText = "FOO!")
        assertEquals(2, edited.size)
        // Index 0 mutated.
        assertEquals("FOO!", edited[0].targetText)
        assertEquals(EditorRowState.USER_EDITED, edited[0].editorState)
        // Source text untouched.
        assertEquals("foo", edited[0].sourceText)
        // Index 1 unchanged.
        assertSame(segments[1], edited[1])
    }

    @Test
    fun applyUserEdit_outOfBoundsReturnsInput() {
        val segments = listOf(seg("foo"))
        val edited = engine.applyUserEdit(segments, index = 5, newTargetText = "X")
        assertSame(segments, edited)
    }

    @Test
    fun markRegeneratePending_flipsStateOnly() {
        val segments = listOf(seg("hello", target = "hola"))
        val pending = engine.markRegeneratePending(segments, 0)
        assertEquals(EditorRowState.REGENERATE_PENDING, pending[0].editorState)
        // Target text preserved while waiting for the regenerate to complete.
        assertEquals("hola", pending[0].targetText)
    }

    @Test
    fun completeRegenerate_replacesTargetAndClearsPendingState() {
        val before = engine.markRegeneratePending(listOf(seg("hi", target = "stale")), 0)
        val after = engine.completeRegenerate(before, index = 0, newTargetText = "fresh")
        assertEquals("fresh", after[0].targetText)
        assertEquals(EditorRowState.TRANSLATED, after[0].editorState)
    }

    @Test
    fun completeRegenerate_preservesExistingWordsWhenNewWordsEmpty() {
        // Word-timing lists are expensive to recompute; the helper preserves the
        // original list when the regenerate path doesn't pass new timings.
        val existing = listOf(
            com.novacut.editor.engine.whisper.SherpaAsrEngine.WordTimestamp(
                word = "hi", startTimeMs = 0L, endTimeMs = 500L,
            )
        )
        val segments = listOf(seg("hi", target = "stale").copy(words = existing))
        val updated = engine.completeRegenerate(
            segments = segments,
            index = 0,
            newTargetText = "fresh",
        )
        assertEquals(existing, updated[0].words)
    }
}
