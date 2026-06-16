package com.novacut.editor.engine

import com.novacut.editor.model.Caption
import com.novacut.editor.model.WordTimestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioDescriptionEngineTest {

    private val engine = AudioDescriptionEngine()

    @Test
    fun mergeSdh_emptyEvents_returnsCaptionsUnchanged() {
        val captions = listOf(Caption(startTimeMs = 0, endTimeMs = 1000, text = "Hello"))
        val result = engine.mergeSdh(captions, emptyList())
        assertEquals(1, result.size)
        assertEquals("Hello", result[0].text)
    }

    @Test
    fun mergeSdh_nonOverlappingEvent_addsBracketedTag() {
        val captions = listOf(Caption(startTimeMs = 0, endTimeMs = 2000, text = "Hello"))
        val events = listOf(AudioDescriptionEngine.AudioEvent(3000, 5000, "music"))
        val result = engine.mergeSdh(captions, events)
        assertEquals(2, result.size)
        assertEquals("[music]", result[1].text)
    }

    @Test
    fun mergeSdh_overlappingEvent_skipped() {
        val captions = listOf(Caption(startTimeMs = 0, endTimeMs = 5000, text = "Hello world"))
        val events = listOf(AudioDescriptionEngine.AudioEvent(1000, 3000, "laughter"))
        val result = engine.mergeSdh(captions, events)
        assertEquals(1, result.size)
    }

    @Test
    fun mergeSdh_resultSortedByTime() {
        val captions = listOf(Caption(startTimeMs = 5000, endTimeMs = 8000, text = "Later"))
        val events = listOf(AudioDescriptionEngine.AudioEvent(0, 2000, "applause"))
        val result = engine.mergeSdh(captions, events)
        assertEquals(2, result.size)
        assertTrue(result[0].startTimeMs <= result[1].startTimeMs)
    }

    @Test
    fun classify_emptyWords_returnsEmpty() {
        val result = engine.classify(emptyList(), 10000L)
        assertTrue(result.isEmpty())
    }

    @Test
    fun classify_longGap_producesEvent() {
        val words = listOf(
            WordTimestamp("hello", 0, 500, 0.9f),
            WordTimestamp("world", 5000, 5500, 0.9f)
        )
        val result = engine.classify(words, 6000L)
        assertEquals(1, result.size)
        assertEquals("music", result[0].label)
        assertEquals(500L, result[0].startMs)
        assertEquals(5000L, result[0].endMs)
    }

    @Test
    fun classify_noLongGap_returnsEmpty() {
        val words = listOf(
            WordTimestamp("hello", 0, 500, 0.9f),
            WordTimestamp("world", 600, 1100, 0.9f)
        )
        val result = engine.classify(words, 2000L)
        assertTrue(result.isEmpty())
    }

    @Test
    fun classify_trailingGap_producesEvent() {
        val words = listOf(WordTimestamp("hello", 0, 500, 0.9f))
        val result = engine.classify(words, 10000L)
        assertEquals(1, result.size)
        assertEquals(500L, result[0].startMs)
        assertEquals(10000L, result[0].endMs)
    }

    @Test
    fun validate_noCollision_returnsAll() {
        val lines = listOf(
            AudioDescriptionEngine.AdLine(5000, "A door opens"),
            AudioDescriptionEngine.AdLine(8000, "She walks in")
        )
        val words = listOf(
            WordTimestamp("hello", 0, 1000, 0.9f),
            WordTimestamp("there", 1500, 2500, 0.9f)
        )
        val result = engine.validate(lines, words)
        assertEquals(2, result.size)
    }

    @Test
    fun validate_collision_filtersOut() {
        val lines = listOf(
            AudioDescriptionEngine.AdLine(500, "A door opens"),
            AudioDescriptionEngine.AdLine(5000, "Safe line")
        )
        val words = listOf(
            WordTimestamp("hello", 0, 1000, 0.9f)
        )
        val result = engine.validate(lines, words)
        assertEquals(1, result.size)
        assertEquals("Safe line", result[0].text)
    }
}
