package com.novacut.editor.engine

import android.content.ComponentCallbacks2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MemoryTrimDispatcherTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun onTrimMemory_dispatchesPolicyActionsAndRecordsBreadcrumb() {
        val registry = MemoryTrimRegistry()
        val calls = mutableListOf<MemoryTrimAction>()
        registry.register(MemoryTrimAction.CLEAR_THUMBNAILS, "video.thumbnailCache") {
            calls += MemoryTrimAction.CLEAR_THUMBNAILS
        }
        registry.register(MemoryTrimAction.CLEAR_WAVEFORMS, "audio.waveformCache") {
            calls += MemoryTrimAction.CLEAR_WAVEFORMS
        }
        val store = MemoryTrimBreadcrumbStore.forDiagnosticsDir(temp.newFolder("diagnostics"))
        val dispatcher = MemoryTrimDispatcher(MemoryTrimPolicy(), registry, store)

        val dispatch = dispatcher.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)

        assertEquals(
            listOf(
                MemoryTrimAction.CLEAR_THUMBNAILS,
                MemoryTrimAction.CLEAR_WAVEFORMS,
            ),
            calls,
        )
        assertEquals("TRIM_MEMORY_UI_HIDDEN", dispatch.decision.levelName)
        assertTrue(dispatch.results.all { it.succeeded })
        assertTrue(store.buildDiagnosticText()!!.contains("TRIM_MEMORY_UI_HIDDEN"))
    }
}
