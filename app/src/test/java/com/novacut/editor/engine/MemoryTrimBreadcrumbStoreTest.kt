package com.novacut.editor.engine

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@Suppress("DEPRECATION")
class MemoryTrimBreadcrumbStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun record_writesBoundedRedactedJsonLines() {
        val store = MemoryTrimBreadcrumbStore.forDiagnosticsDir(temp.newFolder("diagnostics"))
        val decision = MemoryTrimPolicy().decisionFor(android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
        val sensitiveTarget = "video.thumbnailCache content://media/external/video/42"

        repeat(3) { index ->
            store.record(
                decision = decision,
                results = listOf(
                    MemoryTrimRegistry.DispatchResult(
                        action = MemoryTrimAction.CLEAR_THUMBNAILS,
                        targetName = sensitiveTarget,
                        succeeded = index != 2,
                        errorType = if (index == 2) "IOException" else null,
                    )
                ),
                nowEpochMs = 1_000L + index,
                retainCount = 2,
            )
        }

        val diagnosticText = store.buildDiagnosticText(maxRecords = 8)
        assertNotNull(diagnosticText)
        val lines = diagnosticText!!.trim().lines()
        assertEquals(2, lines.size)
        assertFalse(diagnosticText.contains("content://"))
        assertFalse(diagnosticText.contains("external/video/42"))
        assertTrue(diagnosticText.contains("<redacted>"))

        val latest = JSONObject(lines.last())
        assertEquals(MemoryTrimBreadcrumbStore.SCHEMA, latest.getString("schema"))
        assertEquals(1_002L, latest.getLong("recordedAtEpochMs"))
        assertEquals("TRIM_MEMORY_COMPLETE", latest.getString("levelName"))
        assertEquals(3, latest.getJSONArray("requestedActions").length())
        val result = latest.getJSONArray("targetResults").getJSONObject(0)
        assertEquals("CLEAR_THUMBNAILS", result.getString("action"))
        assertEquals(false, result.getBoolean("succeeded"))
        assertEquals("IOException", result.getString("errorType"))
    }
}
