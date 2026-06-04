package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryTrimRegistryTest {

    @Test
    fun dispatch_runsOnlyRequestedTargetsInOrder() {
        val registry = MemoryTrimRegistry()
        val calls = mutableListOf<String>()
        registry.register(MemoryTrimAction.CLEAR_WAVEFORMS, "audio.waveformCache") {
            calls += "waveform"
        }
        registry.register(MemoryTrimAction.CLEAR_THUMBNAILS, "video.thumbnailCache") {
            calls += "thumbnail"
        }

        val results = registry.dispatch(
            listOf(
                MemoryTrimAction.CLEAR_THUMBNAILS,
                MemoryTrimAction.CLEAR_WAVEFORMS,
            )
        )

        assertEquals(listOf("thumbnail", "waveform"), calls)
        assertEquals(
            listOf(
                MemoryTrimAction.CLEAR_THUMBNAILS,
                MemoryTrimAction.CLEAR_WAVEFORMS,
            ),
            results.map { it.action },
        )
        assertTrue(results.all { it.succeeded })
    }

    @Test
    fun dispatch_isolatesTargetFailures() {
        val registry = MemoryTrimRegistry()
        var survivorCalled = false
        registry.register(MemoryTrimAction.CLEAR_PROXY_SCRATCH, "proxy.generatedMediaCache") {
            error("delete failed")
        }
        registry.register(MemoryTrimAction.CLEAR_PROXY_SCRATCH, "proxy.secondaryCache") {
            survivorCalled = true
        }

        val results = registry.dispatch(listOf(MemoryTrimAction.CLEAR_PROXY_SCRATCH))

        assertTrue(survivorCalled)
        assertEquals(2, results.size)
        assertFalse(results[0].succeeded)
        assertEquals("IllegalStateException", results[0].errorType)
        assertTrue(results[1].succeeded)
    }
}
