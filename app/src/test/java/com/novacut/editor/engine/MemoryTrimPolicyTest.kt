package com.novacut.editor.engine

import android.content.ComponentCallbacks2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("DEPRECATION")
class MemoryTrimPolicyTest {

    private val policy = MemoryTrimPolicy()

    @Test
    fun uiHidden_clearsPreviewCachesWithoutProxyScratch() {
        val decision = policy.decisionFor(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)

        assertEquals("TRIM_MEMORY_UI_HIDDEN", decision.levelName)
        assertEquals(MemoryTrimReason.UI_HIDDEN, decision.reason)
        assertEquals(
            listOf(
                MemoryTrimAction.CLEAR_THUMBNAILS,
                MemoryTrimAction.CLEAR_WAVEFORMS,
            ),
            decision.actions,
        )
        assertTrue(decision.logBreadcrumb)
    }

    @Test
    fun runningLow_preservesProxyScratchForVisibleEditingContinuity() {
        val decision = policy.decisionFor(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)

        assertEquals(MemoryTrimReason.VISIBLE_MEMORY_PRESSURE, decision.reason)
        assertEquals(
            listOf(
                MemoryTrimAction.CLEAR_THUMBNAILS,
                MemoryTrimAction.CLEAR_WAVEFORMS,
            ),
            decision.actions,
        )
        assertFalse(decision.actions.contains(MemoryTrimAction.CLEAR_PROXY_SCRATCH))
        assertTrue(decision.logBreadcrumb)
    }

    @Test
    fun runningCritical_evictsAllEditorCaches() {
        val decision = policy.decisionFor(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)

        assertEquals(MemoryTrimReason.BACKGROUND_MEMORY_PRESSURE, decision.reason)
        assertEquals(
            listOf(
                MemoryTrimAction.CLEAR_THUMBNAILS,
                MemoryTrimAction.CLEAR_WAVEFORMS,
                MemoryTrimAction.CLEAR_PROXY_SCRATCH,
            ),
            decision.actions,
        )
        assertTrue(decision.logBreadcrumb)
    }

    @Test
    fun backgroundModerateAndComplete_evictAllEditorCaches() {
        listOf(
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE,
        ).forEach { level ->
            val decision = policy.decisionFor(level)

            assertEquals(MemoryTrimReason.BACKGROUND_MEMORY_PRESSURE, decision.reason)
            assertEquals(
                listOf(
                    MemoryTrimAction.CLEAR_THUMBNAILS,
                    MemoryTrimAction.CLEAR_WAVEFORMS,
                    MemoryTrimAction.CLEAR_PROXY_SCRATCH,
                ),
                decision.actions,
            )
            assertTrue(decision.logBreadcrumb)
        }
    }

    @Test
    fun unknownLevel_isNoop() {
        val decision = policy.decisionFor(-1)

        assertEquals("TRIM_MEMORY_UNKNOWN_-1", decision.levelName)
        assertEquals(MemoryTrimReason.UNKNOWN, decision.reason)
        assertEquals(emptyList<MemoryTrimAction>(), decision.actions)
        assertFalse(decision.logBreadcrumb)
    }
}
